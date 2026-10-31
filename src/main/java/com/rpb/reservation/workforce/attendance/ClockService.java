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
public class ClockService {
    private final AttendanceAccess access;private final WorkforceEmployeeRepository employees;private final SchedulingRepository schedules;private final AttendanceRepository repository;private final WorkforceAudit audit;private final Clock clock;
    private final com.rpb.reservation.workforce.workmode.WorkModeClock workModes;
    public ClockService(AttendanceAccess access,WorkforceEmployeeRepository employees,SchedulingRepository schedules,AttendanceRepository repository,WorkforceAudit audit,Clock clock){this(access,employees,schedules,repository,audit,clock,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public ClockService(AttendanceAccess access,WorkforceEmployeeRepository employees,SchedulingRepository schedules,AttendanceRepository repository,WorkforceAudit audit,Clock clock,com.rpb.reservation.workforce.workmode.WorkModeClock workModes){this.access=access;this.employees=employees;this.schedules=schedules;this.repository=repository;this.audit=audit;this.clock=clock;this.workModes=workModes;}
    @Transactional public Session punch(CurrentActor actor,UUID store,UUID command,ClockWrite c){
        access.sourceWrite(actor);
        if(c==null||c.scheduleId()==null||!Set.of("IN","OUT","BREAK_START","BREAK_END").contains(c.action()==null?"":c.action()))throw WorkforceException.invalid();
        var employee=access.self(actor,store);employees.lock(employee.id());
        var replay=repository.replay(actor.tenantId(),command,employee.id(),store,c);if(replay.isPresent())return replay.get();
        var schedule=access.read(actor,store,c.scheduleId());if(!schedule.employeeId().equals(employee.id()))throw WorkforceException.forbidden();
        Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);Session old=repository.bySchedule(actor.tenantId(),schedule.id()).orElse(null),next;
        if(workModes!=null&&workModes.exempt(actor.tenantId(),employee.id(),store,schedule.workDate(),old))throw new WorkforceException("WORKFORCE_CLOCK_EXEMPT",409);
        var policy=workModes==null?null:workModes.policy(actor.tenantId(),employee.id(),store,schedule.workDate(),old);var selectedPolicy=com.rpb.reservation.workforce.workmode.WorkModeOptions.select(policy,c.mode());if(workModes!=null)workModes.verify(selectedPolicy,c.location());
        if("IN".equals(c.action())){
            // Reload after the shared employee lock so employment/membership edits cannot race a punch.
            employee=employees.byAccount(actor.tenantId(),actor.actorId()).orElseThrow(WorkforceException::forbidden);
            if(old!=null||!schedule.status().equals("scheduled")||!employee.status().equals("active")||!employee.storeIds().contains(store)||!employee.companyId().equals(schedule.companyId())||!schedules.assigned(actor.tenantId(),employee.id(),store,schedule.workDate())||now.isBefore(schedule.startAt().minusSeconds(12*3600))||!now.isBefore(schedule.endAt()))throw state();
            if(repository.active(actor.tenantId(),employee.id()).isPresent()||repository.overlaps(actor.tenantId(),employee.id(),schedule.id(),now,null))throw new WorkforceException("WORKFORCE_ATTENDANCE_OVERLAP",409);
            next=new Session(UUID.randomUUID(),schedule.id(),employee.id(),store,now,null,null,List.of(),false,1,null,policy);
        }else{
            if(old==null||old.outAt()!=null||!now.isAfter(old.inAt()))throw state();
            var breaks=new ArrayList<>(old.breaks());Instant rest=old.breakStartedAt(),out=null;String restRule=old.breakStartedRuleId();
            switch(c.action()){
                case "BREAK_START"->{if(rest!=null)throw state();if(schedule.shift().rules()==null){if(!schedule.shift().breakMode().equals("manual")||c.breakRuleId()!=null)throw state();}else{var allowed=schedule.shift().rules().breaks().stream().filter(b->!b.punchMode().equals("none")).toList();restRule=c.breakRuleId();if(restRule==null&&allowed.size()==1)restRule=allowed.getFirst().id();final String selected=restRule;if(selected==null||allowed.stream().noneMatch(b->b.id().equals(selected)))throw state();}rest=now;}
                case "BREAK_END"->{if(rest==null||!now.isAfter(rest)||c.breakRuleId()!=null&&!Objects.equals(c.breakRuleId(),restRule))throw state();breaks.add(new Interval(rest,now,restRule));rest=null;restRule=null;}
                case "OUT"->{if(rest!=null)throw state();out=now;}
                default->throw state();
            }
            next=new Session(old.id(),old.scheduleId(),old.employeeId(),store,old.inAt(),out,rest,breaks,old.overrideBreak(),old.version()+1,restRule,policy);
        }
        repository.save(actor.tenantId(),next);repository.event(actor.tenantId(),command,actor.actorId(),c.action(),now,next,c.breakRuleId(),selectedPolicy==null?"legacy":selectedPolicy.mode(),c.mode());
        if(workModes!=null)workModes.evidence(actor.tenantId(),command,selectedPolicy,c.location());
        audit.changed(actor,store,"clock."+c.action().toLowerCase(Locale.ROOT),command,old,next);return next;
    }
    private WorkforceException state(){return new WorkforceException("WORKFORCE_CLOCK_STATE",409);}
}
