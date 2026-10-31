package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.payroll.PayrollDtos;
import com.rpb.reservation.workforce.payroll.segment.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceSegmentController extends WorkforceEndpointSupport {
 private final SegmentService service;
 public WorkforceSegmentController(CurrentActorProvider actors,SegmentService service){super(actors);this.service=service;}
 @GetMapping("/payroll-segments") public SegmentDtos.Page page(@PathVariable UUID storeId,@RequestParam UUID companyId,@RequestParam String month){return service.page(actor(),storeId,companyId,month);}
 @GetMapping("/payroll-segments/{id}") public SegmentDtos.Detail detail(@PathVariable UUID storeId,@PathVariable UUID id){return service.detail(actor(),storeId,id);}
 @PutMapping("/payroll-segments/{id}") public SegmentDtos.Detail save(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody SegmentDtos.Write c){return service.save(actor(),storeId,id,c);}
 @PostMapping("/payroll-segments/{id}/commands/{commandId}") public SegmentDtos.Detail action(@PathVariable UUID storeId,@PathVariable UUID id,@PathVariable UUID commandId,@RequestBody SegmentDtos.Action c){return service.action(actor(),storeId,id,commandId,c);}
 @PutMapping("/payroll-segments/{id}/payments/{paymentId}") public SegmentDtos.Detail payment(@PathVariable UUID storeId,@PathVariable UUID id,@PathVariable UUID paymentId,@RequestBody PayrollDtos.PaymentWrite c){return service.payment(actor(),storeId,id,paymentId,c);}
 @GetMapping("/my-payroll-segments") public List<SegmentDtos.Slip> mine(@PathVariable UUID storeId,@RequestParam int year){return service.mine(actor(),storeId,year);}
 @GetMapping("/my-payroll-segments/{id}") public SegmentDtos.Slip mine(@PathVariable UUID storeId,@PathVariable UUID id){return service.mine(actor(),storeId,id);}
}
