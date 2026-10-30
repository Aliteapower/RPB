package com.rpb.reservation.workforce.attendance;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.application.WorkforceDtos.Employee;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class AttendanceAccess {
    private final WorkforceAccess access;private final WorkforceEmployeeRepository employees;private final SchedulingRepository schedules;
    public AttendanceAccess(WorkforceAccess access,WorkforceEmployeeRepository employees,SchedulingRepository schedules){this.access=access;this.employees=employees;this.schedules=schedules;}
    public void sourceWrite(CurrentActor actor){access.sourceWrite(actor);}
    public Employee self(CurrentActor actor,UUID store){access.enter(actor,store);return employees.byAccount(actor.tenantId(),actor.actorId()).orElseThrow(WorkforceException::forbidden);}
    public Schedule read(CurrentActor actor,UUID store,UUID id){
        access.enter(actor,store);var s=schedules.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);
        if(!store.equals(schedules.store(actor.tenantId(),id)))throw WorkforceException.missing();
        if(!manager(actor,store)&&!s.employeeId().equals(self(actor,store).id()))throw WorkforceException.forbidden();return s;
    }
    public boolean manager(CurrentActor actor,UUID store){return access.managedStores(actor).contains(store);}
}
