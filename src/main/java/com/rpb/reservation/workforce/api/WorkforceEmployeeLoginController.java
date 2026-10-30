package com.rpb.reservation.workforce.api;

import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.application.WorkforceEmployeeLoginService;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce/employees/{id}/login-account")
public class WorkforceEmployeeLoginController extends WorkforceEndpointSupport {
 private final WorkforceEmployeeLoginService service;
 public WorkforceEmployeeLoginController(CurrentActorProvider actors,WorkforceEmployeeLoginService service){super(actors);this.service=service;}
 @GetMapping public WorkforceEmployeeLoginService.Account get(@PathVariable UUID storeId,@PathVariable UUID id){return service.get(actor(),storeId,id);}
 @PutMapping public WorkforceEmployeeLoginService.Account reset(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody WorkforceEmployeeLoginService.Write c){return service.reset(actor(),storeId,id,c);}
 @PutMapping("/employee-number") public WorkforceEmployeeLoginService.Account useEmployeeNumber(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody WorkforceEmployeeLoginService.NumberWrite c){return service.useEmployeeNumber(actor(),storeId,id,c);}
}
