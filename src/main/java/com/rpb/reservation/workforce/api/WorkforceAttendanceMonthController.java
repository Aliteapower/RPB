package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;import com.rpb.reservation.workforce.attendance.daily.*;import static com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.*;import java.time.*;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce") public class WorkforceAttendanceMonthController extends WorkforceEndpointSupport {
 private final AttendanceDailyService daily;private final AttendanceExceptionService exceptions;
 public WorkforceAttendanceMonthController(CurrentActorProvider actors,AttendanceDailyService daily,AttendanceExceptionService exceptions){super(actors);this.daily=daily;this.exceptions=exceptions;}
 @GetMapping("/attendance-month") public MonthPage month(@PathVariable UUID storeId,@RequestParam YearMonth month,@RequestParam(required=false) UUID employeeId,@RequestParam(required=false) String search,@RequestParam(required=false) UUID positionId,@RequestParam(defaultValue="30") int limit,@RequestParam(defaultValue="0") int offset){return daily.month(actor(),storeId,month,employeeId,search,positionId,limit,offset);}
 @PutMapping("/attendance-month/refresh") public MonthPage refresh(@PathVariable UUID storeId,@RequestBody RefreshWrite c){return daily.refresh(actor(),storeId,c);}
 @PutMapping("/attendance-days/{id}/review") public EffectiveDay review(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody DayReviewWrite c){return daily.review(actor(),storeId,id,c);}
 @GetMapping("/attendance-exceptions") public List<IntervalException> exceptions(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(required=false) UUID employeeId){return exceptions.list(actor(),storeId,from,to,employeeId);}
 @PutMapping("/attendance-exceptions/{id}") public IntervalException exception(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody ExceptionWrite c){return exceptions.submit(actor(),storeId,id,c);}
 @PutMapping("/attendance-exceptions/{id}/decision") public IntervalException decision(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody DecisionWrite c){return exceptions.decide(actor(),storeId,id,c);}
}
