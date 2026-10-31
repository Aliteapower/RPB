package com.rpb.reservation.workforce.leave;

import static com.rpb.reservation.workforce.leave.LeaveDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.attendance.AttendanceAccess;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.DecisionWrite;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class LeaveService {
    private final WorkforceAccess gate;
    private final AttendanceAccess access;
    private final WorkforceEmployeeRepository employees;
    private final SchedulingRepository schedules;
    private final LeaveTypeRepository types;
    private final LeaveRepository repository;
    private final WorkforceAudit audit;
    private final Clock clock;
    private final com.rpb.reservation.workforce.leave.account.LeaveAccountBridge balances;
    public LeaveService(WorkforceAccess gate,AttendanceAccess access,WorkforceEmployeeRepository employees,SchedulingRepository schedules,LeaveTypeRepository types,LeaveRepository repository,WorkforceAudit audit,Clock clock){this(gate,access,employees,schedules,types,repository,audit,clock,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public LeaveService(WorkforceAccess gate,AttendanceAccess access,WorkforceEmployeeRepository employees,SchedulingRepository schedules,LeaveTypeRepository types,LeaveRepository repository,WorkforceAudit audit,Clock clock,com.rpb.reservation.workforce.leave.account.LeaveAccountBridge balances){this.balances=balances;this.gate=gate;this.access=access;this.employees=employees;this.schedules=schedules;this.types=types;this.repository=repository;this.audit=audit;this.clock=clock;}
    public List<Leave> list(CurrentActor actor,UUID store,LocalDate from,LocalDate to){
        gate.enter(actor,store);ScheduleService.range(from,to);
        var result=repository.leaves(actor.tenantId(),store,from,to,access.manager(actor,store)?null:access.self(actor,store).id());
        if(result.size()>1000)throw WorkforceException.invalid();return result;
    }
    @Transactional public Leave submit(CurrentActor actor,UUID store,UUID id,LeaveWrite c){
        gate.sourceWrite(actor);
        if(id==null||c==null||c.scheduleId()==null||c.typeId()==null||c.startAt()==null||c.endAt()==null||c.reason()==null||c.reason().isBlank()||c.reason().trim().length()>500)throw WorkforceException.invalid();
        c=new LeaveWrite(c.scheduleId(),c.typeId(),c.startAt().truncatedTo(ChronoUnit.MICROS),c.endAt().truncatedTo(ChronoUnit.MICROS),c.reason().trim(),c.medicalReference()==null?null:c.medicalReference().trim());
        if(c.medicalReference()!=null&&(c.medicalReference().isBlank()||c.medicalReference().length()>500))throw WorkforceException.invalid();
        var self=access.self(actor,store);employees.lock(self.id());var s=access.read(actor,store,c.scheduleId());
        if(!self.id().equals(s.employeeId()))throw WorkforceException.forbidden();
        var old=repository.find(actor.tenantId(),id).orElse(null);
        if(old!=null){
            if(old.applicantId().equals(actor.actorId())&&old.scheduleId().equals(c.scheduleId())&&old.typeId().equals(c.typeId())&&old.startAt().equals(c.startAt())&&old.endAt().equals(c.endAt())&&old.reason().equals(c.reason())&&Objects.equals(old.medicalReference(),c.medicalReference()))return old;
            throw WorkforceException.conflict();
        }
        if(!s.status().equals("scheduled")||c.startAt().isBefore(s.startAt())||c.endAt().isAfter(s.endAt())||!c.endAt().isAfter(c.startAt()))throw WorkforceException.invalid();
        if(repository.overlapsDailyException(actor.tenantId(),self.id(),c.startAt(),c.endAt()))throw new WorkforceException("WORKFORCE_LEAVE_OVERLAP",409);
        schedules.lock("workforce.leave-type:"+c.typeId());
        var type=types.find(actor.tenantId(),store,c.typeId()).orElseThrow(WorkforceException::missing);if(!type.enabled())throw WorkforceException.invalid();
        if(repository.forSchedule(actor.tenantId(),s.id()).stream().anyMatch(l->Set.of("pending","cancel_requested").contains(l.status())))throw WorkforceException.conflict();
        var next=new Leave(id,s.id(),self.id(),self.name(),type.id(),type.name(),type.paid(),c.startAt(),c.endAt(),c.reason(),"pending",actor.actorId(),null,null,1,clock.instant(),c.medicalReference());
        if(balances!=null)balances.reserve(actor,store,next,s);repository.create(actor.tenantId(),store,next);var saved=repository.find(actor.tenantId(),id).orElseThrow();audit.changed(actor,store,"leave.submit",id,null,saved);return saved;
    }
    @Transactional public Leave decide(CurrentActor actor,UUID store,UUID id,DecisionWrite c){
        gate.sourceWrite(actor);
        gate.enter(actor,store);
        if(c==null||c.expectedVersion()==null||c.expectedVersion()<1||!Set.of("approved","rejected","withdrawn","cancel_requested","cancelled","cancel_rejected").contains(c.status()==null?"":c.status())||c.note()==null||c.note().trim().length()>500||(!c.status().equals("withdrawn")&&c.note().isBlank()))throw WorkforceException.invalid();
        var initial=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);employees.lock(initial.employeeId());
        var old=repository.find(actor.tenantId(),id).orElseThrow();access.read(actor,store,old.scheduleId());
        var employee=employees.find(actor.tenantId(),old.employeeId()).orElseThrow();boolean applicant=old.applicantId().equals(actor.actorId());
        boolean personal=Set.of("withdrawn","cancel_requested").contains(c.status());
        if(personal){if(!applicant)throw WorkforceException.forbidden();}
        else if(!access.manager(actor,store)||applicant||actor.actorId().equals(employee.accountId()))throw WorkforceException.forbidden();
        String target=c.status().equals("cancel_rejected")?"approved":c.status();String note=c.note().trim();
        if(old.version()==c.expectedVersion()+1&&old.status().equals(target)&&actor.actorId().equals(old.reviewerId())&&note.equals(old.reviewNote())&&repository.lastAction(actor.tenantId(),id).equals(c.status()))return old;
        if(old.version()!=c.expectedVersion())throw WorkforceException.conflict();
        boolean allowed=switch(c.status()){
            case "approved","rejected","withdrawn" -> old.status().equals("pending");
            case "cancel_requested" -> old.status().equals("approved");
            case "cancelled","cancel_rejected" -> old.status().equals("cancel_requested");
            default -> false;
        };
        if(!allowed)throw WorkforceException.conflict();
        if(c.status().equals("approved")&&repository.overlapsDailyException(actor.tenantId(),old.employeeId(),old.startAt(),old.endAt()))throw new WorkforceException("WORKFORCE_LEAVE_OVERLAP",409);
        if(c.status().equals("approved")&&repository.forSchedule(actor.tenantId(),old.scheduleId()).stream().anyMatch(l->!l.id().equals(id)&&Set.of("approved","cancel_requested").contains(l.status())&&l.startAt().isBefore(old.endAt())&&l.endAt().isAfter(old.startAt())))throw new WorkforceException("WORKFORCE_LEAVE_OVERLAP",409);
        if(c.status().equals("cancel_requested")&&repository.forSchedule(actor.tenantId(),old.scheduleId()).stream().anyMatch(l->!l.id().equals(id)&&Set.of("pending","cancel_requested").contains(l.status())))throw WorkforceException.conflict();
        if(balances!=null)balances.decision(actor,store,old,access.read(actor,store,old.scheduleId()),c.status());
        repository.decision(actor.tenantId(),id,target,c.status(),actor.actorId(),note,old.version()+1);
        var saved=repository.find(actor.tenantId(),id).orElseThrow();audit.changed(actor,store,"leave."+c.status(),id,old,saved);return saved;
    }
}
