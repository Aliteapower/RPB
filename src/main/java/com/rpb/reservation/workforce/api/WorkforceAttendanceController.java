package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.attendance.*;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceAttendanceController extends WorkforceEndpointSupport {
    private final ShiftService shifts;private final ScheduleService schedules;private final ClockService clock;private final AttendanceQueryService query;private final CorrectionService corrections;
    public WorkforceAttendanceController(CurrentActorProvider actors,ShiftService shifts,ScheduleService schedules,ClockService clock,AttendanceQueryService query,CorrectionService corrections){super(actors);this.shifts=shifts;this.schedules=schedules;this.clock=clock;this.query=query;this.corrections=corrections;}
    @GetMapping("/shifts") public List<Shift> shifts(@PathVariable UUID storeId){return shifts.list(actor(),storeId);}
    @PutMapping("/shifts/{id}") public Shift shift(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody ShiftWrite c){return shifts.save(actor(),storeId,id,c);}
    @GetMapping("/schedule") public SchedulePage schedule(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to){return schedules.list(actor(),storeId,from,to);}
    @PutMapping("/schedule/{id}") public Schedule schedule(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody ScheduleWrite c){return schedules.save(actor(),storeId,id,c);}
    @PutMapping("/schedule-copy/{id}") public List<Schedule> copy(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody CopyWrite c){return schedules.copy(actor(),storeId,id,c);}
    @GetMapping("/clock") public ClockHome clock(@PathVariable UUID storeId){return query.home(actor(),storeId);}
    @PutMapping("/clock/{id}") public Session clock(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody ClockWrite c){return clock.punch(actor(),storeId,id,c);}
    @GetMapping("/attendance") public AttendancePage attendance(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(required=false)UUID employeeId,@RequestParam(defaultValue="50")int limit,@RequestParam(defaultValue="0")int offset){return query.list(actor(),storeId,from,to,employeeId,limit,offset);}
    @GetMapping("/attendance/{id}") public Detail detail(@PathVariable UUID storeId,@PathVariable UUID id){return query.detail(actor(),storeId,id);}
    @GetMapping("/corrections") public List<Correction> corrections(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to){return corrections.list(actor(),storeId,from,to);}
    @PutMapping("/corrections/{id}") public Correction correction(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody CorrectionWrite c){return corrections.submit(actor(),storeId,id,c);}
    @PutMapping("/corrections/{id}/decision") public Correction decision(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody DecisionWrite c){return corrections.decide(actor(),storeId,id,c);}
}
