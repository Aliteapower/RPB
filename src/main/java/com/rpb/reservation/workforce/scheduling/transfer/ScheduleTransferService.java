package com.rpb.reservation.workforce.scheduling.transfer;

import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.attendance.AttendanceCalculator;
import com.rpb.reservation.workforce.attendance.AttendanceRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.bulk.BulkScheduleRepository;
import com.rpb.reservation.workforce.scheduling.bulk.BulkSchedulePlanService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;

@Service
@Transactional(readOnly=true)
public class ScheduleTransferService {
    public record Transfer(UUID sourceId,Long expectedVersion,UUID targetStoreId,UUID targetShiftId,Instant splitAt,Long targetShiftVersion) {
        public Transfer(UUID sourceId,Long expectedVersion,UUID targetStoreId,UUID targetShiftId,Instant splitAt){
            this(sourceId,expectedVersion,targetStoreId,targetShiftId,splitAt,null);
        }
    }
    public record Result(Schedule source,Schedule target,Long sourcePlannedSeconds,Long targetPlannedSeconds) {}
    private final WorkforceAccess access;
    private final SchedulingRepository schedules;
    private final WorkforceEmployeeRepository employees;
    private final AttendanceRepository attendance;
    private final BulkScheduleRepository commands;
    private final WorkforceAudit audit;
    private final WorkforceJson json;
    private final Clock clock;

    public ScheduleTransferService(WorkforceAccess access,SchedulingRepository schedules,WorkforceEmployeeRepository employees,
            AttendanceRepository attendance,BulkScheduleRepository commands,WorkforceAudit audit,WorkforceJson json,Clock clock){
        this.access=access;
        this.schedules=schedules;
        this.employees=employees;
        this.attendance=attendance;
        this.commands=commands;
        this.audit=audit;
        this.json=json;
        this.clock=clock;
    }

    public Result preview(CurrentActor actor,UUID sourceStore,Transfer write){
        authorize(actor,sourceStore,write);
        var source=source(actor.tenantId(),sourceStore,write.sourceId());
        return plan(actor.tenantId(),sourceStore,source,UUID.randomUUID(),write);
    }

    @Transactional
    public Result commit(CurrentActor actor,UUID sourceStore,UUID command,Transfer write){
        authorize(actor,sourceStore,write);
        access.sourceWrite(actor);
        UUID tenant=actor.tenantId(),targetStore=write.targetStoreId();
        var orderedStores=List.of(sourceStore,targetStore).stream().sorted(Comparator.comparing(UUID::toString)).toList();
        for(var store:orderedStores)schedules.lockPlanning(tenant,store,false);
        schedules.lock("workforce.bulk:"+tenant+":"+command);
        String hash=BulkSchedulePlanService.hash("schedule-transfer:"+json.write(write));
        var replay=commands.replayAs(tenant,sourceStore,actor.actorId(),command,hash,Result.class);
        if(replay.isPresent())return replay.get();
        for(var store:orderedStores)commands.lockStore(tenant,store);
        for(var store:orderedStores)schedules.lock("workforce.calendar:"+tenant+":"+store);
        schedules.lock("workforce.shift:"+write.targetShiftId());
        var previous=source(tenant,sourceStore,write.sourceId());
        employees.lock(previous.employeeId());
        schedules.lock("workforce.schedule:"+previous.id());
        previous=source(tenant,sourceStore,write.sourceId());
        var result=plan(tenant,sourceStore,previous,UUID.randomUUID(),write);
        schedules.save(tenant,sourceStore,result.source());
        schedules.save(tenant,targetStore,result.target());
        var anchor=schedules.transferAnchor(tenant,previous.id()).orElse(sourceStore);
        var priorCut=schedules.transferClockNotBefore(tenant,previous.id()).orElse(null);
        var cutoff=write.splitAt()==null?priorCut:write.splitAt();
        schedules.linkTransfer(tenant,previous.id(),result.target().id(),anchor,cutoff);
        audit.changed(actor,sourceStore,"schedule.transfer.out",previous.id(),previous,result.source());
        audit.changed(actor,targetStore,"schedule.transfer.in",result.target().id(),null,result.target());
        commands.receiptValue(tenant,sourceStore,actor.actorId(),command,hash,result);
        return result;
    }

    private void authorize(CurrentActor actor,UUID sourceStore,Transfer write){
        access.enter(actor,sourceStore);
        access.manage(actor,sourceStore);
        if(write==null||write.sourceId()==null||write.targetShiftId()==null||write.targetStoreId()==null
                ||write.expectedVersion()==null||write.expectedVersion()<1||sourceStore.equals(write.targetStoreId()))
            throw WorkforceException.invalid();
        access.enter(actor,write.targetStoreId());
        access.manage(actor,write.targetStoreId());
    }

    private Schedule source(UUID tenant,UUID store,UUID id){
        var source=schedules.find(tenant,id).orElseThrow(WorkforceException::missing);
        if(!store.equals(schedules.store(tenant,id))||schedules.deleted(tenant,id))throw WorkforceException.missing();
        return source;
    }

    private Result plan(UUID tenant,UUID sourceStore,Schedule source,UUID targetId,Transfer write){
        if(source.version()!=write.expectedVersion()||!source.status().equals("scheduled")||schedules.transferredOut(tenant,source.id()))
            throw WorkforceException.conflict();
        var employee=employees.find(tenant,source.employeeId()).orElseThrow(WorkforceException::missing);
        var date=source.workDate();
        var targetStore=write.targetStoreId();
        if(!employee.enabled()||!employee.status().equals("active")||date.isBefore(employee.hireDate())
                ||employee.leaveDate()!=null&&date.isAfter(employee.leaveDate())
                ||!source.companyId().equals(schedules.companyOn(tenant,employee.id(),date))
                ||!source.companyId().equals(schedules.storeCompany(tenant,sourceStore))
                ||!source.companyId().equals(schedules.storeCompany(tenant,targetStore)))
            throw new WorkforceException("WORKFORCE_SCHEDULE_STORE_ASSIGNMENT",400);
        var shift=schedules.shift(tenant,targetStore,write.targetShiftId()).orElseThrow(WorkforceException::missing);
        if(write.targetShiftVersion()!=null&&shift.version()!=write.targetShiftVersion())throw WorkforceException.conflict();
        if(!shift.enabled())throw new WorkforceException("WORKFORCE_SCHEDULE_SHIFT_DISABLED",400);
        String zone=schedules.timezone(tenant,targetStore);
        var interval=ShiftTiming.resolve(date,shift,ZoneId.of(zone));
        if(schedules.transferClockNotBefore(tenant,source.id()).filter(interval.start()::isBefore).isPresent())
            throw WorkforceException.invalid();
        var now=clock.instant().truncatedTo(ChronoUnit.MICROS);
        var split=write.splitAt();
        var session=attendance.bySchedule(tenant,source.id()).orElse(null);
        if(schedules.transferProtected(tenant,source,interval)||session!=null&&(split==null||session.outAt()!=null
                ||session.breakStartedAt()!=null||!split.isAfter(session.inAt())))
            throw new WorkforceException("WORKFORCE_ACTIVE_ATTENDANCE",409);
        if(split==null){
            if(!now.isBefore(source.startAt())||interval.start().isBefore(now))throw WorkforceException.invalid();
        }else if(split.isBefore(now)||!split.isAfter(source.startAt())||!split.isBefore(source.endAt())
                ||interval.start().isBefore(split)||!split.equals(split.truncatedTo(ChronoUnit.SECONDS))){
            throw WorkforceException.invalid();
        }
        if(schedules.overlaps(tenant,source.employeeId(),source.id(),interval.start(),interval.end()))
            throw new WorkforceException("WORKFORCE_SCHEDULE_OVERLAP",409);
        var retained=new Schedule(source.id(),source.employeeId(),source.employeeName(),source.employeeNo(),
            source.shiftId(),date,source.startAt(),split==null?source.endAt():split,source.timezone(),
            split==null?"cancelled":"scheduled",source.shift(),source.companyId(),source.version()+1);
        var target=new Schedule(targetId,source.employeeId(),source.employeeName(),source.employeeNo(),shift.id(),date,
            interval.start(),interval.end(),zone,"scheduled",shift,source.companyId(),1);
        return new Result(retained,target,split==null?0L:planned(retained),planned(target));
    }

    private Long planned(Schedule schedule){
        return new AttendanceCalculator().calculate(schedule.shift(),schedule.startAt(),schedule.startAt(),schedule.endAt(),
            List.of(),false).workSeconds();
    }
}
