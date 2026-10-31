package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.payroll.PayrollDtos;
import com.rpb.reservation.workforce.payroll.supplemental.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceSupplementalController extends WorkforceEndpointSupport {
 private final SupplementalService service;
 public WorkforceSupplementalController(CurrentActorProvider actors,SupplementalService service){super(actors);this.service=service;}
 @GetMapping("/supplemental-month-summary") public SupplementalDtos.MonthlySummary summary(@PathVariable UUID storeId,@RequestParam UUID companyId,@RequestParam String month){return service.monthSummary(actor(),storeId,companyId,month);}
 @GetMapping("/supplemental-sources") public SupplementalDtos.SourcePage source(@PathVariable UUID storeId,@RequestParam UUID companyId,@RequestParam UUID employeeId,@RequestParam String month){return service.source(actor(),storeId,companyId,employeeId,month);}
 @GetMapping("/supplemental-batches") public List<SupplementalDtos.Batch> list(@PathVariable UUID storeId,@RequestParam UUID companyId){return service.list(actor(),storeId,companyId);}
 @GetMapping("/supplemental-batches/{id}") public SupplementalDtos.Detail detail(@PathVariable UUID storeId,@PathVariable UUID id){return service.detail(actor(),storeId,id);}
 @PutMapping("/supplemental-batches/{id}") public SupplementalDtos.Detail save(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody SupplementalDtos.Write c){return service.save(actor(),storeId,id,c);}
 @PostMapping("/supplemental-batches/{id}/actions") public SupplementalDtos.Detail action(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody SupplementalDtos.Action c){return service.action(actor(),storeId,id,c);}
 @PutMapping("/supplemental-batches/{id}/payments/{paymentId}") public SupplementalDtos.Detail payment(@PathVariable UUID storeId,@PathVariable UUID id,@PathVariable UUID paymentId,@RequestBody PayrollDtos.PaymentWrite c){return service.payment(actor(),storeId,id,paymentId,c);}
 @GetMapping("/my-supplemental-payslips") public List<SupplementalDtos.Payslip> mine(@PathVariable UUID storeId){return service.mine(actor(),storeId);}
 @GetMapping("/my-supplemental-payslips/{id}") public SupplementalDtos.Payslip mine(@PathVariable UUID storeId,@PathVariable UUID id){return service.mine(actor(),storeId,id);}
}
