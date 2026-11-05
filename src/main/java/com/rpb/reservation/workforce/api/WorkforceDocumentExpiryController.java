package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;import com.rpb.reservation.workforce.documents.expiry.*;import java.util.UUID;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce/document-expiries")
public class WorkforceDocumentExpiryController extends WorkforceEndpointSupport {
 private final DocumentExpiryService service;public WorkforceDocumentExpiryController(CurrentActorProvider actors,DocumentExpiryService service){super(actors);this.service=service;}
 @GetMapping public DocumentExpiryDtos.Page list(@PathVariable UUID storeId,@RequestParam(defaultValue="30")int days,@RequestParam(defaultValue="all")String status,@RequestParam(defaultValue="50")int limit,@RequestParam(defaultValue="0")int offset,@RequestParam(required=false)UUID employeeId){var actor=actor();return employeeId==null?service.list(actor,storeId,days,status,limit,offset):service.list(actor,storeId,days,status,limit,offset,employeeId);}
 @GetMapping("/employees") public DocumentExpiryDtos.EmployeePage employeeCounts(@PathVariable UUID storeId,@RequestParam(defaultValue="30")int days,@RequestParam java.util.List<UUID> employeeIds){return service.employeeCounts(actor(),storeId,days,employeeIds);}
}
