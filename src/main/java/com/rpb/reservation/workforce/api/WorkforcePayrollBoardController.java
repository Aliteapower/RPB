package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.payroll.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce/employee-payroll-board")
public class WorkforcePayrollBoardController extends WorkforceEndpointSupport {
 private final PayrollBoardService service;
 public WorkforcePayrollBoardController(CurrentActorProvider actors,PayrollBoardService service){super(actors);this.service=service;}
 @GetMapping public PayrollBoardDtos.Board read(@PathVariable UUID storeId,@RequestParam UUID companyId,@RequestParam String month){return service.read(actor(),storeId,companyId,month);}
}
