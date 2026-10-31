package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.statutory.settlement.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceStatutorySettlementController extends WorkforceEndpointSupport {
 private final SettlementService service;
 public WorkforceStatutorySettlementController(CurrentActorProvider actors,SettlementService service){super(actors);this.service=service;}
 @GetMapping("/statutory-settlement-contexts/{companyId}/{employeeId}/{month}") public SettlementDtos.EvidencePage read(@PathVariable UUID storeId,@PathVariable UUID companyId,@PathVariable UUID employeeId,@PathVariable String month){return service.read(actor(),storeId,companyId,employeeId,month);}
 @PutMapping("/statutory-settlement-contexts/{companyId}/{employeeId}/{month}") public SettlementDtos.EvidencePage save(@PathVariable UUID storeId,@PathVariable UUID companyId,@PathVariable UUID employeeId,@PathVariable String month,@RequestBody SettlementDtos.Evidence c){return service.save(actor(),storeId,companyId,employeeId,month,c);}
 @PostMapping("/statutory-settlement-contexts/{companyId}/{employeeId}/{month}/settlements") public SettlementDtos.Settlement settleMonth(@PathVariable UUID storeId,@PathVariable UUID companyId,@PathVariable UUID employeeId,@PathVariable String month,@RequestBody SettlementDtos.SettleWrite c){return service.settleMonth(actor(),storeId,companyId,employeeId,month,c);}
 @GetMapping("/statutory-company-months/{companyId}/{month}") public SettlementDtos.CompanySdl company(@PathVariable UUID storeId,@PathVariable UUID companyId,@PathVariable String month){return service.company(actor(),storeId,companyId,month);}
 @PutMapping("/statutory-company-months/{companyId}/{month}") public SettlementDtos.CompanySdl company(@PathVariable UUID storeId,@PathVariable UUID companyId,@PathVariable String month,@RequestBody SettlementDtos.CompanyEvidence c){return service.company(actor(),storeId,companyId,month,c);}
 @GetMapping("/statutory-remittances") public List<SettlementDtos.Remittance> list(@PathVariable UUID storeId,@RequestParam UUID companyId){return service.remittances(actor(),storeId,companyId);}
 @GetMapping("/statutory-remittances/{id}") public SettlementDtos.Remittance detail(@PathVariable UUID storeId,@PathVariable UUID id){return service.remittance(actor(),storeId,id);}
 @PutMapping("/statutory-remittances/{id}") public SettlementDtos.Remittance remit(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody SettlementDtos.RemittanceWrite c){return service.remit(actor(),storeId,id,c);}
 @GetMapping("/aw-reconciliations") public List<SettlementDtos.ReconciliationPage> reconciliations(@PathVariable UUID storeId,@RequestParam UUID companyId,@RequestParam UUID employeeId,@RequestParam int year){return service.reconciliations(actor(),storeId,companyId,employeeId,year);}
 @GetMapping("/aw-reconciliations/{id}") public SettlementDtos.ReconciliationPage reconciliation(@PathVariable UUID storeId,@PathVariable UUID id){return service.reconciliation(actor(),storeId,id);}
 @PostMapping("/aw-reconciliations/{id}/settlements") public SettlementDtos.Settlement settle(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody SettlementDtos.SettleWrite c){return service.settle(actor(),storeId,id,c);}
}
