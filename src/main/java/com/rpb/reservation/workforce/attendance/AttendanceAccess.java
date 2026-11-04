package com.rpb.reservation.workforce.attendance;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.application.WorkforceDtos.Employee;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class AttendanceAccess {
    private final WorkforceAccess access;private final WorkforceEmployeeRepository employees;private final SchedulingRepository schedules;
    public AttendanceAccess(WorkforceAccess access,WorkforceEmployeeRepository employees,SchedulingRepository schedules){this.access=access;this.employees=employees;this.schedules=schedules;}
    public void sourceWrite(CurrentActor actor){access.sourceWrite(actor);}
    public Employee self(CurrentActor actor,UUID store){access.enter(actor,store);return employees.byAccount(actor.tenantId(),actor.actorId()).orElseThrow(WorkforceException::forbidden);}
    public Employee historySelf(CurrentActor actor,UUID store){access.history(actor,store);return employees.byAccount(actor.tenantId(),actor.actorId()).orElseThrow(WorkforceException::forbidden);}
    public Schedule read(CurrentActor actor,UUID store,UUID id){
        access.enter(actor,store);var s=schedules.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);
        if(!store.equals(schedules.store(actor.tenantId(),id)))throw WorkforceException.missing();
        if(!manager(actor,store)&&!s.employeeId().equals(self(actor,store).id()))throw WorkforceException.forbidden();return s;
    }
    public record ClockScope(UUID store,Schedule schedule) {}
    public ClockScope clockScope(CurrentActor actor,UUID anchor,UUID id,Employee employee){
        var schedule=schedules.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);
        UUID actual=schedules.store(actor.tenantId(),id);
        if(!actual.equals(anchor)){
            if(!schedule.employeeId().equals(employee.id())||!transferFrom(actor.tenantId(),anchor,id)
                    ||!transferAllowed(actor,actual))throw WorkforceException.missing();
        }else if(!schedule.employeeId().equals(employee.id()))throw WorkforceException.forbidden();
        return new ClockScope(actual,schedule);
    }
    public boolean transferFrom(UUID tenant,UUID anchor,UUID schedule){
        return schedules.transferAnchor(tenant,schedule).filter(anchor::equals).isPresent();
    }
    public boolean transferAllowed(CurrentActor actor,UUID target){
        return schedules.storeCompany(actor.tenantId(),target)!=null&&access.transferClockAllowed(actor,target);
    }
    public boolean canStartAt(UUID tenant,Schedule schedule,Instant now){
        return !now.isBefore(schedule.startAt().minusSeconds(12*3600))&&now.isBefore(schedule.endAt())
            &&schedules.transferClockNotBefore(tenant,schedule.id()).filter(now::isBefore).isEmpty();
    }
    public boolean canStart(UUID tenant,UUID store,Employee employee,Schedule schedule){
        if(!schedule.status().equals("scheduled")||!employee.enabled()||!employee.status().equals("active")
                ||!employee.companyId().equals(schedule.companyId()))return false;
        if(employee.storeIds().contains(store)&&schedules.assigned(tenant,employee.id(),store,schedule.workDate()))return true;
        return schedules.transferAnchor(tenant,schedule.id()).isPresent()
            &&schedule.companyId().equals(schedules.storeCompany(tenant,store))
            &&schedule.companyId().equals(schedules.companyOn(tenant,employee.id(),schedule.workDate()))
            &&!schedule.workDate().isBefore(employee.hireDate())
            &&(employee.leaveDate()==null||!schedule.workDate().isAfter(employee.leaveDate()));
    }
    public Schedule ownTransferDetail(CurrentActor actor,UUID anchor,UUID id,boolean history){
        var employee=history?historySelf(actor,anchor):self(actor,anchor);
        var schedule=schedules.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);
        if(!schedule.employeeId().equals(employee.id())||!transferFrom(actor.tenantId(),anchor,id))
            throw WorkforceException.missing();
        return schedule;
    }
    public boolean manager(CurrentActor actor,UUID store){return access.managedStores(actor).contains(store);}
}
