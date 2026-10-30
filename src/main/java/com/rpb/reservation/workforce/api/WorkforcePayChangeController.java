package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.payroll.governance.*;
import com.rpb.reservation.workforce.payroll.PayrollDtos.LineInput;
import static com.rpb.reservation.workforce.payroll.governance.PayChangeDtos.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforcePayChangeController extends WorkforceEndpointSupport {
 private final PayChangeService requests;private final PayChangeImpactService impacts;
 public WorkforcePayChangeController(CurrentActorProvider actors,PayChangeService requests,PayChangeImpactService impacts){super(actors);this.requests=requests;this.impacts=impacts;}
 @GetMapping("/pay-change-requests") public List<Request> list(@PathVariable UUID storeId,@RequestParam UUID companyId,@RequestParam(required=false) String month){var period=month==null||month.isBlank()?null:com.rpb.reservation.workforce.payroll.PayrollProfileValidation.month(month);return requests.list(actor(),storeId,companyId).stream().filter(r->PayChangeMonthFilter.matches(r,period)).toList();}
 @GetMapping("/pay-change-requests/{id}") public Request detail(@PathVariable UUID storeId,@PathVariable UUID id){return requests.detail(actor(),storeId,id);}
 @PutMapping("/pay-change-requests/{id}") public Request save(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Write c){return requests.save(actor(),storeId,id,c);}
 @PutMapping("/pay-change-requests/{id}/actions") public Request action(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Action c){return requests.action(actor(),storeId,id,c);}
 @GetMapping("/pay-change-requests/{id}/impact") public Impact impact(@PathVariable UUID storeId,@PathVariable UUID id,@RequestParam String month){return impacts.preview(actor(),storeId,id,month);}
 @GetMapping("/pay-change-monthly-baseline") public LineInput baseline(@PathVariable UUID storeId,@RequestParam UUID companyId,@RequestParam UUID employeeId,@RequestParam String month){return requests.monthlyBaseline(actor(),storeId,companyId,employeeId,month);}
}
