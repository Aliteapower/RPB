package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.payroll.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforcePayrollController extends WorkforceEndpointSupport {
 private final PayrollConfigurationService configuration;private final PayrollBatchService batches;private final PayrollPaymentService payments;private final PayslipService slips;
 public WorkforcePayrollController(CurrentActorProvider actors,PayrollConfigurationService configuration,PayrollBatchService batches,PayrollPaymentService payments,PayslipService slips){super(actors);this.configuration=configuration;this.batches=batches;this.payments=payments;this.slips=slips;}
 @GetMapping("/payroll-home") public PayrollHome home(@PathVariable UUID storeId){return configuration.home(actor(),storeId);}
 @GetMapping("/salary-templates") public List<SalaryTemplate> templates(@PathVariable UUID storeId,@RequestParam UUID companyId){return configuration.listTemplates(actor(),storeId,companyId);}
 @PutMapping("/salary-templates/{id}") public SalaryTemplate template(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody TemplateWrite c){return configuration.saveTemplate(actor(),storeId,id,c);}
 @GetMapping("/pay-profiles") public ProfilePage profiles(@PathVariable UUID storeId,@RequestParam UUID companyId){return configuration.profiles(actor(),storeId,companyId);}
 @PutMapping("/pay-profiles/{id}") public PayProfile profile(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody ProfileWrite c){return configuration.saveProfile(actor(),storeId,id,c);}
 @GetMapping("/payroll-batches") public List<BatchSummary> batches(@PathVariable UUID storeId,@RequestParam UUID companyId){return batches.list(actor(),storeId,companyId);}
 @PutMapping("/payroll-batches/{id}") public BatchDetail create(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody BatchWrite c){return batches.create(actor(),storeId,id,c);}
 @GetMapping("/payroll-batches/{id}") public BatchDetail detail(@PathVariable UUID storeId,@PathVariable UUID id){return batches.detail(actor(),storeId,id);}
 @PutMapping("/payroll-batches/{id}/inputs/{employeeId}") public BatchDetail input(@PathVariable UUID storeId,@PathVariable UUID id,@PathVariable UUID employeeId,@RequestBody InputWrite c){return batches.input(actor(),storeId,id,employeeId,c);}
 @PutMapping("/payroll-batches/{id}/actions/{commandId}") public BatchDetail action(@PathVariable UUID storeId,@PathVariable UUID id,@PathVariable UUID commandId,@RequestBody ActionWrite c){return batches.action(actor(),storeId,id,commandId,c);}
 @PutMapping("/payroll-batches/{id}/payments/{paymentId}") public BatchDetail payment(@PathVariable UUID storeId,@PathVariable UUID id,@PathVariable UUID paymentId,@RequestBody PaymentWrite c){return payments.register(actor(),storeId,id,paymentId,c);}
 @GetMapping("/my-payslips") public List<PayslipSummary> mine(@PathVariable UUID storeId,@RequestParam int year){return slips.mine(actor(),storeId,year);}
 @GetMapping("/my-payslips/{id}") public Payslip mine(@PathVariable UUID storeId,@PathVariable UUID id){return slips.mineDetail(actor(),storeId,id);}
 @GetMapping("/payroll-batches/{id}/payslips/{employeeId}") public Payslip preview(@PathVariable UUID storeId,@PathVariable UUID id,@PathVariable UUID employeeId){return slips.preview(actor(),storeId,id,employeeId);}
}
