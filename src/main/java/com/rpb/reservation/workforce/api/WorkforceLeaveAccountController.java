package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;import com.rpb.reservation.workforce.leave.account.*;import static com.rpb.reservation.workforce.leave.account.LeaveAccountDtos.*;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce") public class WorkforceLeaveAccountController extends WorkforceEndpointSupport {
 private final LeaveAccountService service;private final LeaveYearEndService yearEnd;public WorkforceLeaveAccountController(CurrentActorProvider actors,LeaveAccountService service,LeaveYearEndService yearEnd){super(actors);this.service=service;this.yearEnd=yearEnd;}
 @GetMapping("/leave-accounts") public Page page(@PathVariable UUID storeId,@RequestParam UUID employeeId,@RequestParam int year){return service.page(actor(),storeId,employeeId,year);}
 @PutMapping("/leave-accounts/enable/{id}") public Page enable(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Enable c){return service.enable(actor(),storeId,id,c);}
 @PutMapping("/leave-accounts/refresh/{id}") public Page refresh(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Refresh c){return service.refresh(actor(),storeId,id,c);}
 @PutMapping("/leave-accounts/adjust/{id}") public Page adjust(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Adjust c){return service.adjust(actor(),storeId,id,c);}
 @PutMapping("/leave-accounts/review/{id}") public Page review(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Review c){return service.review(actor(),storeId,id,c);}
 @PutMapping("/leave-accounts/year-end/{id}") public Page yearEnd(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody YearEnd c){return yearEnd.close(actor(),storeId,id,c);}
 @GetMapping("/leave-type-policies") public List<Binding> bindings(@PathVariable UUID storeId){return service.bindings(actor(),storeId);}
 @PutMapping("/leave-type-policies/{typeId}") public Binding bind(@PathVariable UUID storeId,@PathVariable UUID typeId,@RequestBody BindWrite c){return service.bind(actor(),storeId,typeId,c);}
}
