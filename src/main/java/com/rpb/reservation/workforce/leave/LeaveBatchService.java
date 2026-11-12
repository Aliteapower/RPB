package com.rpb.reservation.workforce.leave;

import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.attendance.AttendanceAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.scheduling.ScheduleService;
import com.rpb.reservation.workforce.scheduling.SchedulingRepository;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.SchedulePage;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.workforce.leave.LeaveDtos.*;

/** Transactional orchestration only: individual leave rules and audit remain in LeaveService. */
@Service
public class LeaveBatchService {
    public record Item(UUID id,Long expectedScheduleVersion,LeaveWrite request) {}
    public record Request(LocalDate from,LocalDate to,List<Item> items) {}
    private final WorkforceAccess gate;
    private final AttendanceAccess access;
    private final WorkforceEmployeeRepository employees;
    private final LeaveService leaves;
    private final SchedulingRepository scheduling;
    private final LeaveGroupRepository groups;
    public LeaveBatchService(WorkforceAccess gate,AttendanceAccess access,WorkforceEmployeeRepository employees,LeaveService leaves,SchedulingRepository scheduling,LeaveGroupRepository groups){this.gate=gate;this.access=access;this.employees=employees;this.leaves=leaves;this.scheduling=scheduling;this.groups=groups;}
    @Transactional(readOnly=true) public SchedulePage options(CurrentActor actor,UUID store,LocalDate from,LocalDate to){
        var self=access.self(actor,store);ScheduleService.range(from,to);
        var items=scheduling.list(actor.tenantId(),store,from,to,self.id(),1001,0);
        if(items.size()>1000)throw WorkforceException.invalid();
        return new SchedulePage(items,List.of(),scheduling.timezone(actor.tenantId(),store));
    }
    @Transactional public List<Leave> submit(CurrentActor actor,UUID store,Request request){
        gate.sourceWrite(actor);
        var self=access.self(actor,store);
        if(request==null)throw WorkforceException.invalid();
        ScheduleService.range(request.from(),request.to());
        if(request.items()==null||request.items().isEmpty()||request.items().size()>1000)throw WorkforceException.invalid();
        employees.lock(self.id());
        if(request.items().getFirst()==null||request.items().getFirst().id()==null)throw WorkforceException.invalid();
        var replay=groups.replay(actor,store,self.id(),request);if(replay.isPresent())return replay.get();
        var ids=new HashSet<UUID>();var schedules=new HashSet<UUID>();
        for(var item:request.items()){
            if(item==null||item.id()==null||item.expectedScheduleVersion()==null||item.expectedScheduleVersion()<1||item.request()==null||item.request().scheduleId()==null||!ids.add(item.id())||!schedules.add(item.request().scheduleId()))throw WorkforceException.invalid();
            var s=access.read(actor,store,item.request().scheduleId());
            if(!s.employeeId().equals(self.id()))throw WorkforceException.forbidden();
            if(s.workDate().isBefore(request.from())||s.workDate().isAfter(request.to()))throw WorkforceException.invalid();
            if(s.version()!=item.expectedScheduleVersion())throw WorkforceException.conflict();
        }
        var result=new ArrayList<Leave>();
        for(var item:request.items())result.add(leaves.submit(actor,store,item.id(),item.request()));
        groups.save(actor,store,self.id(),request,result);
        return result;
    }
}
