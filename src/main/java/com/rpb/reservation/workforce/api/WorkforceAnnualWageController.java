package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.statutory.annual.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce/statutory-year-contexts/{companyId}/{employeeId}/{year}")
public class WorkforceAnnualWageController extends WorkforceEndpointSupport {
 private final AnnualWageService service;
 public WorkforceAnnualWageController(CurrentActorProvider actors,AnnualWageService service){super(actors);this.service=service;}
 @GetMapping public AnnualWageDtos.Page read(@PathVariable UUID storeId,@PathVariable UUID companyId,@PathVariable UUID employeeId,@PathVariable int year){return service.read(actor(),storeId,companyId,employeeId,year);}
 @PutMapping public AnnualWageDtos.Page save(@PathVariable UUID storeId,@PathVariable UUID companyId,@PathVariable UUID employeeId,@PathVariable int year,@RequestBody AnnualWageDtos.Write c){return service.save(actor(),storeId,companyId,employeeId,year,c);}
 @PostMapping("/reconciliations") public AnnualWageDtos.Reconciliation reconcile(@PathVariable UUID storeId,@PathVariable UUID companyId,@PathVariable UUID employeeId,@PathVariable int year,@RequestBody AnnualWageDtos.ReconcileWrite c){return service.reconcile(actor(),storeId,companyId,employeeId,year,c);}
}
