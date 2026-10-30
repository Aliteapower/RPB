package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.payroll.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import java.util.*;import java.time.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforcePayrollConfigurationController extends WorkforceEndpointSupport {
 private final EmployeePaySummaryService summaries;private final PositionSalaryDefaultsService defaults;
 public WorkforcePayrollConfigurationController(CurrentActorProvider actors,EmployeePaySummaryService summaries,PositionSalaryDefaultsService defaults){super(actors);this.summaries=summaries;this.defaults=defaults;}
 @GetMapping("/employees/{id}/pay-summary") public EmployeePaySummary summary(@PathVariable UUID storeId,@PathVariable UUID id,@RequestParam UUID companyId,@RequestParam LocalDate on){return summaries.summary(actor(),storeId,id,companyId,on);}
 @GetMapping("/position-salary-defaults") public List<PositionSalaryDefault> defaults(@PathVariable UUID storeId,@RequestParam UUID companyId,@RequestParam UUID positionId){return defaults.list(actor(),storeId,companyId,positionId);}
 @PutMapping("/position-salary-defaults/{id}") public PositionSalaryDefault save(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody DefaultWrite c){return defaults.save(actor(),storeId,id,c);}
 @GetMapping("/pay-profile-preview") public ProfilePreview preview(@PathVariable UUID storeId,@RequestParam UUID companyId,@RequestParam UUID employeeId,@RequestParam LocalDate on,@RequestParam(required=false) UUID templateId){return defaults.preview(actor(),storeId,companyId,employeeId,on,templateId);}
}
