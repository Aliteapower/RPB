package com.rpb.reservation.workforce.attendance;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class AttendanceQueryService {
    private final WorkforceAccess gate;private final AttendanceAccess access;private final SchedulingRepository schedules;private final AttendanceRepository repository;private final Clock clock;
    private final AttendanceCalculator calculator=new AttendanceCalculator();
    private final com.rpb.reservation.workforce.workmode.WorkModeClock workModes;
    public AttendanceQueryService(WorkforceAccess gate,AttendanceAccess access,SchedulingRepository schedules,AttendanceRepository repository,Clock clock){this(gate,access,schedules,repository,clock,null);}
    private final com.rpb.reservation.workforce.workmode.WorkModePrivacy privacy;
    public AttendanceQueryService(WorkforceAccess gate,AttendanceAccess access,SchedulingRepository schedules,AttendanceRepository repository,Clock clock,com.rpb.reservation.workforce.workmode.WorkModeClock workModes){this(gate,access,schedules,repository,clock,workModes,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public AttendanceQueryService(WorkforceAccess gate,AttendanceAccess access,SchedulingRepository schedules,AttendanceRepository repository,Clock clock,com.rpb.reservation.workforce.workmode.WorkModeClock workModes,com.rpb.reservation.workforce.workmode.WorkModePrivacy privacy){this.gate=gate;this.access=access;this.schedules=schedules;this.repository=repository;this.clock=clock;this.workModes=workModes;this.privacy=privacy;}
    public AttendancePage list(CurrentActor actor,UUID store,LocalDate from,LocalDate to,UUID employee,int limit,int offset){
        gate.enter(actor,store);ScheduleService.range(from,to);if(limit<1||limit>100||offset<0)throw WorkforceException.invalid();
        var items=access.manager(actor,store)?schedules.list(actor.tenantId(),store,from,to,employee,limit,offset)
            :schedules.ownAttendance(actor.tenantId(),store,from,to,access.self(actor,store).id(),limit,offset);
        return new AttendancePage(items.stream().map(s->{var a=attendance(actor.tenantId(),s);return privacy==null?a:privacy.attendance(actor,a);}).toList(),limit,offset);
    }
    public Detail detail(CurrentActor actor,UUID store,UUID id){
        gate.enter(actor,store);
        UUID tenant=actor.tenantId();
        var transfer=access.transferFrom(tenant,store,id)&&!store.equals(schedules.store(tenant,id));
        var s=transfer?access.ownTransferDetail(actor,store,id,false):access.read(actor,store,id);
        return detailProjection(actor,s,transfer);
    }
    private Detail detailProjection(CurrentActor actor,Schedule s,boolean transfer){
        UUID tenant=actor.tenantId();
        var detail=new Detail(attendance(tenant,s),repository.events(tenant,s.id()),repository.corrections(tenant,s.id()));
        var projected=privacy==null?detail:privacy.detail(actor,detail);
        if(!transfer)return projected;
        UUID target=schedules.store(tenant,s.id());
        return new Detail(projected.attendance(),projected.events(),projected.corrections(),
            new ClockStore(target,schedules.storeName(tenant,target)));
    }
    public AttendancePage historyList(CurrentActor actor,UUID store,LocalDate from,LocalDate to,UUID employee,int limit,int offset){
        gate.history(actor,store);ScheduleService.range(from,to);if(limit<1||limit>100||offset<0)throw WorkforceException.invalid();
        var items=access.manager(actor,store)?schedules.list(actor.tenantId(),store,from,to,employee,limit,offset)
            :schedules.ownAttendance(actor.tenantId(),store,from,to,access.historySelf(actor,store).id(),limit,offset);
        return new AttendancePage(items.stream().map(s->{var a=attendance(actor.tenantId(),s);return privacy==null?a:privacy.attendance(actor,a);}).toList(),limit,offset);
    }
    public Detail historyDetail(CurrentActor actor,UUID store,UUID id){
        gate.history(actor,store);var s=schedules.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);
        boolean transfer=!store.equals(schedules.store(actor.tenantId(),id));
        if(transfer)s=access.ownTransferDetail(actor,store,id,true);
        else if(!access.manager(actor,store)&&!s.employeeId().equals(access.historySelf(actor,store).id()))throw WorkforceException.forbidden();
        return detailProjection(actor,s,transfer);
    }
    public ClockHome home(CurrentActor actor,UUID store){
        var employee=access.self(actor,store);var zone=ZoneId.of(schedules.timezone(actor.tenantId(),store));var today=LocalDate.now(clock.withZone(zone));var now=clock.instant();
        UUID tenant=actor.tenantId();
        var transferStores=new LinkedHashMap<UUID,ClockStore>();
        var available=new ArrayList<>(schedules.list(tenant,store,today.minusDays(2),today.plusDays(1),employee.id(),100,0).stream()
            .filter(s->canOffer(tenant,store,employee,s,now)).toList());
        for(var incoming:schedules.incomingTransfers(tenant,store,employee.id(),now)){
            var s=incoming.schedule();
            if(!incoming.store().equals(store)&&canOffer(tenant,incoming.store(),employee,s,now)
                    &&access.transferAllowed(actor,incoming.store())){
                available.add(s);
                transferStores.put(s.id(),new ClockStore(incoming.store(),schedules.storeName(tenant,incoming.store())));
            }
        }
        available.sort(Comparator.comparing(Schedule::startAt).thenComparing(Schedule::id));
        var active=repository.active(tenant,employee.id()).orElse(null);
        Attendance attendance=null;
        var actions=new ArrayList<String>();
        if(active!=null){
            if(active.storeId().equals(store)||access.transferFrom(tenant,store,active.scheduleId())
                    &&access.transferAllowed(actor,active.storeId())){
                var s=schedules.find(tenant,active.scheduleId()).orElseThrow();
                attendance=attendance(tenant,s);
                if(!active.storeId().equals(store))
                    transferStores.put(s.id(),new ClockStore(active.storeId(),schedules.storeName(tenant,active.storeId())));
                if(active.breakStartedAt()!=null)actions.add("BREAK_END");
                else{
                    actions.add("OUT");
                    if(s.shift().rules()!=null?s.shift().rules().breaks().stream().anyMatch(b->!b.punchMode().equals("none"))
                            :s.shift().breakMode().equals("manual"))actions.add("BREAK_START");
                }
            }
        }else{
            if(!available.isEmpty())actions.add("IN");
            var latest=java.util.stream.Stream.concat(repository.latest(tenant,store,employee.id()).stream(),
                repository.latestTransferred(tenant,store,employee.id()).stream()).max(Comparator.comparing(Session::inAt));
            if(latest.isPresent()){
                var session=latest.get();
                attendance=attendance(tenant,schedules.find(tenant,session.scheduleId()).orElseThrow());
                if(!session.storeId().equals(store))
                    transferStores.put(session.scheduleId(),new ClockStore(session.storeId(),schedules.storeName(tenant,session.storeId())));
            }
        }
        var policies=new LinkedHashMap<UUID,com.rpb.reservation.workforce.workmode.WorkModeDtos.ClockPolicy>();
        com.rpb.reservation.workforce.workmode.WorkModeDtos.ClockPolicy policy=null;
        if(workModes!=null){
            policy=active==null?workModes.current(tenant,employee.id(),store,today)
                :workModes.policy(tenant,employee.id(),active.storeId(),schedules.find(tenant,active.scheduleId()).orElseThrow().workDate(),active);
            for(var s:available){
                var target=transferStores.get(s.id());
                policies.put(s.id(),workModes.current(tenant,employee.id(),target==null?store:target.id(),s.workDate()));
            }
            if(active==null){
                available.removeIf(s->policies.get(s.id()).mode().equals("free"));
                if(available.isEmpty())actions.remove("IN");
            }
        }
        return new ClockHome(available,active,attendance,actions,now,policy,policies,transferStores);
    }
    private boolean canOffer(UUID tenant,UUID store,com.rpb.reservation.workforce.application.WorkforceDtos.Employee employee,
            Schedule schedule,Instant now){
        return access.canStart(tenant,store,employee,schedule)&&access.canStartAt(tenant,schedule,now)&&repository.bySchedule(tenant,schedule.id()).isEmpty();
    }
    private Attendance attendance(UUID tenant,Schedule s){
        var session=repository.bySchedule(tenant,s.id()).orElse(null);
        if(workModes!=null&&workModes.exempt(tenant,s.employeeId(),schedules.store(tenant,s.id()),s.workDate(),session))return new Attendance(s,session,null,null,null,List.of("EXEMPT"),List.of(),true);
        if(session==null)return new Attendance(s,null,null,null,null,List.of(s.status().equals("cancelled")?"CANCELLED":clock.instant().isAfter(s.endAt())?"MISSING_IN":"NOT_CLOCKED"));
        var result=calculator.calculate(s.shift(),s.startAt(),session.inAt(),session.outAt(),session.breaks(),session.overrideBreak());
        var anomalies=new ArrayList<>(result.anomalies());if(session.outAt()==null&&clock.instant().isAfter(s.endAt()))anomalies.add("MISSING_OUT");
        return new Attendance(s,session,result.grossSeconds(),result.breakSeconds(),result.workSeconds(),anomalies,new BreakCalculator().summary(s.shift(),s.startAt(),session));
    }
}
