package com.rpb.reservation.workforce.scheduling;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class ScheduleService {
    private final WorkforceAccess access;private final SchedulingRepository repository;private final WorkforceEmployeeRepository employees;private final WorkforceAudit audit;private final Clock clock;
    public ScheduleService(WorkforceAccess access,SchedulingRepository repository,WorkforceEmployeeRepository employees,WorkforceAudit audit,Clock clock){this.access=access;this.repository=repository;this.employees=employees;this.audit=audit;this.clock=clock;}
    public SchedulePage list(CurrentActor actor,UUID store,LocalDate from,LocalDate to){
        access.enter(actor,store);range(from,to);boolean manager=access.managedStores(actor).contains(store);
        UUID employee=manager?null:employees.byAccount(actor.tenantId(),actor.actorId()).orElseThrow(WorkforceException::forbidden).id();
        return new SchedulePage(repository.list(actor.tenantId(),store,from,to,employee,1000,0),manager?repository.employees(actor.tenantId(),store,from,to):List.of(),repository.timezone(actor.tenantId(),store));
    }
    @Transactional public Schedule save(CurrentActor actor,UUID store,UUID id,ScheduleWrite c){
        access.sourceWrite(actor);
        access.enter(actor,store);access.manage(actor,store);
        if(c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.employeeId()==null||c.shiftId()==null||c.workDate()==null||!Set.of("scheduled","cancelled").contains(c.status()==null?"":c.status()))throw WorkforceException.invalid();
        repository.lockPlanning(actor.tenantId(),store,false);employees.lock(c.employeeId());repository.lock("workforce.schedule:"+id);
        var old=repository.find(actor.tenantId(),id).orElse(null);
        if(old!=null&&repository.deleted(actor.tenantId(),id))throw WorkforceException.missing();
        if(old!=null&&(!store.equals(repository.store(actor.tenantId(),id))||!old.employeeId().equals(c.employeeId())))throw WorkforceException.forbidden();
        if(old!=null&&c.expectedVersion()==0&&old.shiftId().equals(c.shiftId())&&old.workDate().equals(c.workDate())&&old.status().equals(c.status()))return old;
        if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
        if(repository.used(actor.tenantId(),id))throw error("WORKFORCE_ACTIVE_ATTENDANCE");
        if("cancelled".equals(c.status())){
            if(old==null||!old.shiftId().equals(c.shiftId())||!old.workDate().equals(c.workDate()))throw WorkforceException.invalid();
            var cancelled=new Schedule(old.id(),old.employeeId(),old.employeeName(),old.employeeNo(),old.shiftId(),old.workDate(),old.startAt(),old.endAt(),old.timezone(),"cancelled",old.shift(),old.companyId(),old.version()+1);
            repository.save(actor.tenantId(),store,cancelled);audit.changed(actor,store,"schedule.cancel",id,old,cancelled);return cancelled;
        }
        String zone=repository.timezone(actor.tenantId(),store);var today=LocalDate.now(clock.withZone(ZoneId.of(zone)));
        if(c.workDate().isBefore(today)||c.workDate().isAfter(today.plusDays(90)))throw invalid("WORKFORCE_SCHEDULE_DATE_OUT_OF_RANGE");
        boolean assigned=repository.assigned(actor.tenantId(),c.employeeId(),store,c.workDate());
        // Unknown/out-of-store IDs must not disclose another employee's employment state.
        if(!assigned&&!repository.hasStoreAssignment(actor.tenantId(),c.employeeId(),store))throw invalid("WORKFORCE_SCHEDULE_STORE_ASSIGNMENT");
        var employee=employees.find(actor.tenantId(),c.employeeId()).orElseThrow(WorkforceException::missing);
        if(!employee.enabled()||!"active".equals(employee.status()))throw invalid("WORKFORCE_SCHEDULE_EMPLOYEE_INACTIVE");
        if(c.workDate().isBefore(employee.hireDate()))throw invalid("WORKFORCE_SCHEDULE_BEFORE_HIRE");
        if(employee.leaveDate()!=null&&c.workDate().isAfter(employee.leaveDate()))throw invalid("WORKFORCE_SCHEDULE_EMPLOYEE_LEFT");
        if(!assigned)throw invalid("WORKFORCE_SCHEDULE_STORE_ASSIGNMENT");
        var shift=repository.shift(actor.tenantId(),store,c.shiftId()).orElseThrow(WorkforceException::missing);if(!shift.enabled())throw invalid("WORKFORCE_SCHEDULE_SHIFT_DISABLED");
        if(old!=null&&old.status().equals("cancelled")){
            if(!old.shiftId().equals(c.shiftId())||!old.workDate().equals(c.workDate()))throw WorkforceException.invalid();
            if(repository.overlaps(actor.tenantId(),employee.id(),id,old.startAt(),old.endAt()))throw error("WORKFORCE_SCHEDULE_OVERLAP");
            var restored=new Schedule(old.id(),old.employeeId(),employee.name(),employee.employeeNo(),old.shiftId(),old.workDate(),old.startAt(),old.endAt(),old.timezone(),"scheduled",old.shift(),old.companyId(),old.version()+1);
            repository.save(actor.tenantId(),store,restored);audit.changed(actor,store,"schedule.restore",id,old,restored);return restored;
        }
        var interval=ShiftTiming.resolve(c.workDate(),shift,ZoneId.of(zone));Instant start=interval.start(),end=interval.end();
        if(!end.isAfter(start)||Duration.between(start,end).getSeconds()>24*3600)throw WorkforceException.invalid();
        if(repository.overlaps(actor.tenantId(),employee.id(),id,start,end))throw error("WORKFORCE_SCHEDULE_OVERLAP");
        var saved=new Schedule(id,employee.id(),employee.name(),employee.employeeNo(),shift.id(),c.workDate(),start,end,zone,"scheduled",shift,employee.companyId(),c.expectedVersion()+1);
        repository.save(actor.tenantId(),store,saved);audit.changed(actor,store,"schedule.save",id,old,saved);return saved;
    }
    @Transactional public List<Schedule> copy(CurrentActor actor,UUID store,UUID command,CopyWrite c){
        access.sourceWrite(actor);
        access.enter(actor,store);access.manage(actor,store);
        if(c==null||c.sourceWeek()==null||c.targetWeek()==null||c.sourceWeek().getDayOfWeek()!=DayOfWeek.MONDAY||c.targetWeek().getDayOfWeek()!=DayOfWeek.MONDAY||c.sourceWeek().equals(c.targetWeek()))throw WorkforceException.invalid();
        repository.lockPlanning(actor.tenantId(),store,false);repository.lock("workforce.copy:"+actor.tenantId()+":"+command);
        var replay=repository.copied(actor.tenantId(),store,command,c);if(replay.isPresent())return replay.get();
        var source=repository.copySource(actor.tenantId(),store,c.sourceWeek());
        if(source.size()>100)throw WorkforceException.invalid();
        source.stream().map(Schedule::employeeId).distinct().sorted().forEach(employees::lock);
        var result=new ArrayList<Schedule>();long delta=ChronoUnit.DAYS.between(c.sourceWeek(),c.targetWeek());
        for(var s:source)result.add(save(actor,store,UUID.randomUUID(),new ScheduleWrite(0L,s.employeeId(),s.shiftId(),s.workDate().plusDays(delta),"scheduled")));
        repository.copied(actor.tenantId(),store,actor.actorId(),command,c,result);audit.changed(actor,store,"schedule.copy",command,c,result);return result;
    }
    public static void range(LocalDate from,LocalDate to){if(from==null||to==null||to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>30)throw WorkforceException.invalid();}
    private WorkforceException error(String code){return new WorkforceException(code,409);}
    private WorkforceException invalid(String code){return new WorkforceException(code,400);}
}

