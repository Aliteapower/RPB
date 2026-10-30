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
        if(!access.manager(actor,store))employee=access.self(actor,store).id();
        return new AttendancePage(schedules.list(actor.tenantId(),store,from,to,employee,limit,offset).stream().map(s->{var a=attendance(actor.tenantId(),s);return privacy==null?a:privacy.attendance(actor,a);}).toList(),limit,offset);
    }
    public Detail detail(CurrentActor actor,UUID store,UUID id){var s=access.read(actor,store,id);var d=new Detail(attendance(actor.tenantId(),s),repository.events(actor.tenantId(),id),repository.corrections(actor.tenantId(),id));return privacy==null?d:privacy.detail(actor,d);}
    public ClockHome home(CurrentActor actor,UUID store){
        var employee=access.self(actor,store);var zone=ZoneId.of(schedules.timezone(actor.tenantId(),store));var today=LocalDate.now(clock.withZone(zone));var now=clock.instant();
        var available=schedules.list(actor.tenantId(),store,today.minusDays(2),today.plusDays(1),employee.id(),100,0).stream().filter(s->s.status().equals("scheduled")&&employee.storeIds().contains(store)&&employee.companyId().equals(s.companyId())&&schedules.assigned(actor.tenantId(),employee.id(),store,s.workDate())&&!now.isBefore(s.startAt().minusSeconds(12*3600))&&now.isBefore(s.endAt())&&repository.bySchedule(actor.tenantId(),s.id()).isEmpty()).toList();
        var active=repository.active(actor.tenantId(),employee.id()).orElse(null);Attendance attendance=null;var actions=new ArrayList<String>();
        if(active!=null){
            if(active.storeId().equals(store)){
                var s=schedules.find(actor.tenantId(),active.scheduleId()).orElseThrow();attendance=attendance(actor.tenantId(),s);
                if(active.breakStartedAt()!=null)actions.add("BREAK_END");else{actions.add("OUT");if(s.shift().rules()!=null?s.shift().rules().breaks().stream().anyMatch(b->!b.punchMode().equals("none")):s.shift().breakMode().equals("manual"))actions.add("BREAK_START");}
            }
        }else{
            if(!available.isEmpty()&&employee.status().equals("active")&&employee.enabled())actions.add("IN");
            var latest=repository.latest(actor.tenantId(),store,employee.id());
            if(latest.isPresent())attendance=attendance(actor.tenantId(),schedules.find(actor.tenantId(),latest.get().scheduleId()).orElseThrow());
        }
        if(workModes==null)return new ClockHome(available,active,attendance,actions,now);
        var policy=workModes.current(actor.tenantId(),employee.id(),store,today);var policies=new LinkedHashMap<UUID,com.rpb.reservation.workforce.workmode.WorkModeDtos.ClockPolicy>();
        for(var s:available)policies.put(s.id(),workModes.current(actor.tenantId(),employee.id(),store,s.workDate()));
        if(active==null){available=available.stream().filter(s->!policies.get(s.id()).mode().equals("free")).toList();if(available.isEmpty())actions.remove("IN");}
        return new ClockHome(available,active,attendance,actions,now,policy,policies);
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


