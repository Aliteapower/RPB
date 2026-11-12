package com.rpb.reservation.wallet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.rpb.reservation.wallet.application.WalletCustomerIdentityService;
import com.rpb.reservation.wallet.application.WalletViews;
import com.rpb.reservation.wallet.domain.WalletException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class WalletCustomerIdentityController {
 private final WalletApiIdentity identity;private final WalletCustomerIdentityService service;
 public WalletCustomerIdentityController(WalletApiIdentity identity,WalletCustomerIdentityService service){this.identity=identity;this.service=service;}
 @PostMapping("/api/v1/public/stores/{storeId}/wallet/customer-codes")
 public WalletViews.PaymentCode issue(@PathVariable UUID storeId,@RequestBody(required=false) JsonNode body,HttpServletRequest request){
  var customer=identity.customer(request);if(body!=null&&(!body.isObject()||!body.isEmpty()))throw WalletException.invalid();
  return service.issue(identity.scope(customer.tenantId(),storeId),customer);
 }
 @PostMapping("/api/v1/stores/{storeId}/wallet/customer-codes/resolve")
 public WalletViews.Customer resolve(@PathVariable UUID storeId,@RequestBody JsonNode body){
  var actor=identity.staff();if(body==null||!body.isObject()||body.size()!=1||!body.path("code").isTextual())throw WalletException.invalid();
  return service.resolve(identity.scope(actor.tenantId(),storeId),actor,body.get("code").textValue());
 }
}
