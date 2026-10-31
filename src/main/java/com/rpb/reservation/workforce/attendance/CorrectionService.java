package com.rpb.reservation.workforce.attendance;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class CorrectionService {
    private final WorkforceAccess gate;private final AttendanceAccess access;private final WorkforceEmployeeRepository employees;private final SchedulingRepository schedules;private final AttendanceRepository repository;private final WorkforceAudit audit;private final Clock clock;
    private final com.rpb.reservation.workforce.workmode.WorkModeClock workModes;
    public CorrectionService(WorkforceAccess gate,AttendanceAccess access,WorkforceEmployeeRepository employees,SchedulingRepository schedules,AttendanceRepository repository,WorkforceAudit audit,Clock clock){this(gate,access,employees,schedules,repository,audit,clock,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public CorrectionService(WorkforceAccess gate,AttendanceAccess access,WorkforceEmployeeRepository employees,SchedulingRepository schedules,AttendanceRepository repository,WorkforceAudit audit,Clock clock,com.rpb.reservation.workforce.workmode.WorkModeClock workModes){this.gate=gate;this.access=access;this.employees=employees;this.schedules=schedules;this.repository=repository;this.audit=audit;this.clock=clock;this.workModes=workModes;}
    public List<Correction> list(CurrentActor actor,UUID store,LocalDate from,LocalDate to){gate.enter(actor,store);ScheduleService.range(from,to);return repository.corrections(actor.tenantId(),store,from,to,access.manager(actor,store)?null:access.self(actor,store).id());}
    @Transactional public Correction submit(CurrentActor actor,UUID store,UUID id,CorrectionWrite c){
        gate.sourceWrite(actor);
        if(c==null||c.scheduleId()==null||c.basedSessionVersion()==null||c.basedSessionVersion()<0||c.inAt()==null||c.outAt()==null||c.breaks()==null||c.breaks().size()>20||c.overrideBreak()==null||c.reason()==null||c.reason().trim().isEmpty()||c.reason().trim().length()>500)throw WorkforceException.invalid();
        if(c.breaks().stream().anyMatch(b->b==null||b.start()==null||b.end()==null))throw WorkforceException.invalid();
        c=new CorrectionWrite(c.scheduleId(),c.basedSessionVersion(),c.inAt().truncatedTo(ChronoUnit.MICROS),c.outAt().truncatedTo(ChronoUnit.MICROS),c.breaks().stream().map(b->new Interval(b.start().truncatedTo(ChronoUnit.MICROS),b.end().truncatedTo(ChronoUnit.MICROS),b.ruleId())).toList(),c.overrideBreak(),c.reason());
        var employee=access.self(actor,store);employees.lock(employee.id());var s=access.read(actor,store,c.scheduleId());
        if(workModes!=null&&workModes.exempt(actor.tenantId(),s.employeeId(),store,s.workDate(),repository.bySchedule(actor.tenantId(),s.id()).orElse(null)))throw new WorkforceException("WORKFORCE_CLOCK_EXEMPT",409);
        if(!s.employeeId().equals(employee.id()))throw WorkforceException.forbidden();
        var old=repository.correction(actor.tenantId(),id).orElse(null);
        if(old!=null){
            if(old.applicantId().equals(actor.actorId())&&old.scheduleId().equals(c.scheduleId())&&old.basedSessionVersion()==c.basedSessionVersion()&&old.inAt().equals(c.inAt())&&old.outAt().equals(c.outAt())&&old.breaks().equals(c.breaks())&&old.overrideBreak()==c.overrideBreak()&&old.reason().equals(c.reason().trim()))return old;
            throw WorkforceException.conflict();
        }
        if(!s.status().equals("scheduled")||c.inAt().isBefore(s.startAt().minusSeconds(12*3600))||c.outAt().isAfter(s.endAt().plusSeconds(12*3600))||c.outAt().isAfter(clock.instant())||!c.outAt().isAfter(c.inAt())||Duration.between(c.inAt(),c.outAt()).getSeconds()>36*3600)throw WorkforceException.invalid();
        if(s.shift().rules()==null&&!c.overrideBreak()&&!s.shift().breakMode().equals("manual")&&!c.breaks().isEmpty())throw WorkforceException.invalid();
        new AttendanceCalculator().calculate(s.shift(),s.startAt(),c.inAt(),c.outAt(),c.breaks(),c.overrideBreak());
        var session=repository.bySchedule(actor.tenantId(),s.id()).orElse(null);
        if((session==null?0:session.version())!=c.basedSessionVersion())throw WorkforceException.conflict();
        if(repository.corrections(actor.tenantId(),s.id()).stream().anyMatch(r->r.status().equals("pending")))throw new WorkforceException("WORKFORCE_PENDING_CORRECTION",409);
        var correction=new Correction(id,s.id(),employee.id(),employee.name(),c.basedSessionVersion(),c.inAt(),c.outAt(),List.copyOf(c.breaks()),c.overrideBreak(),c.reason().trim(),"pending",actor.actorId(),null,null,1,clock.instant());
        repository.create(actor.tenantId(),store,correction);var saved=repository.correction(actor.tenantId(),id).orElseThrow();audit.changed(actor,store,"correction.submit",id,null,saved);return saved;
    }
    @Transactional public Correction decide(CurrentActor actor,UUID store,UUID id,DecisionWrite c){
        gate.sourceWrite(actor);
        gate.enter(actor,store);
        if(c==null||c.expectedVersion()==null||c.expectedVersion()<1||!Set.of("approved","rejected","withdrawn").contains(c.status()==null?"":c.status())||c.note()==null||(!c.status().equals("withdrawn")&&c.note().trim().isEmpty())||c.note().trim().length()>500)throw WorkforceException.invalid();
        var initial=repository.correction(actor.tenantId(),id).orElseThrow(WorkforceException::missing);employees.lock(initial.employeeId());
        var old=repository.correction(actor.tenantId(),id).orElseThrow();var s=access.read(actor,store,old.scheduleId());
        var employee=employees.find(actor.tenantId(),old.employeeId()).orElseThrow();
        if(c.status().equals("withdrawn")){if(!old.applicantId().equals(actor.actorId()))throw WorkforceException.forbidden();}
        else if(!access.manager(actor,store)||old.applicantId().equals(actor.actorId())||actor.actorId().equals(employee.accountId()))throw WorkforceException.forbidden();
        if(!old.status().equals("pending")){
            if(old.status().equals(c.status())&&actor.actorId().equals(old.reviewerId())&&c.note().trim().equals(old.reviewNote()))return old;
            throw WorkforceException.conflict();
        }
        if(old.version()!=c.expectedVersion())throw WorkforceException.conflict();
        if(c.status().equals("approved")){
            var before=repository.bySchedule(actor.tenantId(),s.id()).orElse(null);
            if((before==null?0:before.version())!=old.basedSessionVersion())throw WorkforceException.conflict();
            if(repository.overlaps(actor.tenantId(),old.employeeId(),s.id(),old.inAt(),old.outAt()))throw new WorkforceException("WORKFORCE_ATTENDANCE_OVERLAP",409);
            if(workModes!=null&&workModes.exempt(actor.tenantId(),s.employeeId(),store,s.workDate(),before))throw new WorkforceException("WORKFORCE_CLOCK_EXEMPT",409);
            var next=new Session(before==null?UUID.randomUUID():before.id(),s.id(),s.employeeId(),store,old.inAt(),old.outAt(),null,old.breaks(),old.overrideBreak(),old.basedSessionVersion()+1,null,workModes==null?(before==null?null:before.clockPolicy()):workModes.policy(actor.tenantId(),s.employeeId(),store,s.workDate(),before));
            repository.save(actor.tenantId(),next);
        }
        repository.decision(actor.tenantId(),id,actor.actorId(),c,old.version()+1);var saved=repository.correction(actor.tenantId(),id).orElseThrow();audit.changed(actor,store,"correction."+c.status(),id,old,saved);return saved;
    }
}

