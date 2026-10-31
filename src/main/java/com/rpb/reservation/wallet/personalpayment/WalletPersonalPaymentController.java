package com.rpb.reservation.wallet.personalpayment;

import com.fasterxml.jackson.databind.JsonNode;
import com.rpb.reservation.wallet.api.*;
import com.rpb.reservation.wallet.domain.WalletException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.*;

@RestController
@RequestMapping("/api/v1")
public class WalletPersonalPaymentController {
 private final WalletApiIdentity identity;private final WalletPersonalPaymentService service;
 public WalletPersonalPaymentController(WalletApiIdentity identity,WalletPersonalPaymentService service){this.identity=identity;this.service=service;}
 @GetMapping("/public/stores/{storeId}/wallet/personal-payment-consent") public Consent consent(@PathVariable UUID storeId,HttpServletRequest request){var customer=identity.customer(request);return service.consent(identity.scope(customer.tenantId(),storeId),customer);}
 @PostMapping("/public/stores/{storeId}/wallet/personal-payment-consent") public Consent consent(@PathVariable UUID storeId,@RequestBody JsonNode body,HttpServletRequest request){
  var customer=identity.customer(request);WalletModelGuard.require(request);fields(body,"policyVersion","limitCents","accepted","idempotencyKey");if(!body.has("limitCents")||!body.path("accepted").isBoolean())throw WalletException.invalid();return service.consent(identity.scope(customer.tenantId(),storeId),customer,new ConsentUpdate(integer(body,"policyVersion"),number(body,"limitCents"),body.get("accepted").booleanValue(),string(body,"idempotencyKey")));
 }
 @PostMapping("/public/stores/{storeId}/wallet/personal-codes") public PersonalCode issue(@PathVariable UUID storeId,@RequestBody JsonNode body,HttpServletRequest request){var customer=identity.customer(request);WalletModelGuard.require(request);fields(body);return service.issue(identity.scope(customer.tenantId(),storeId),customer);}
 @PostMapping("/public/stores/{storeId}/wallet/personal-codes/status") public CodeStatus status(@PathVariable UUID storeId,@RequestBody JsonNode body,HttpServletRequest request){var customer=identity.customer(request);WalletModelGuard.require(request);fields(body,"code");return service.status(identity.scope(customer.tenantId(),storeId),customer,string(body,"code"));}
 @PostMapping("/stores/{storeId}/wallet/personal-scan-charges") public ChargeResult charge(@PathVariable UUID storeId,@RequestBody JsonNode body,HttpServletRequest request){
  var actor=identity.staff();WalletModelGuard.require(request);fields(body,"customerCode","amount","description","idempotencyKey");return service.charge(identity.scope(actor.tenantId(),storeId),actor,new ScanCharge(string(body,"customerCode"),string(body,"amount"),string(body,"description"),string(body,"idempotencyKey")));
 }
 @GetMapping("/stores/{storeId}/wallet/personal-scan-charges/result") public ChargeResult result(@PathVariable UUID storeId,@RequestParam String idempotencyKey){var actor=identity.staff();return service.result(identity.scope(actor.tenantId(),storeId),actor,idempotencyKey);}
 @PostMapping("/stores/{storeId}/wallet/personal-scan-charges/reconcile") public ChargeResult reconcile(@PathVariable UUID storeId,@RequestBody JsonNode body,HttpServletRequest request){var actor=identity.staff();WalletModelGuard.require(request);fields(body,"idempotencyKey");return service.reconcile(identity.scope(actor.tenantId(),storeId),actor,string(body,"idempotencyKey"));}
 private static void fields(JsonNode body,String... allowed){if(body==null||!body.isObject())throw WalletException.invalid();var set=Set.of(allowed);body.fieldNames().forEachRemaining(k->{if(!set.contains(k))throw WalletException.invalid();});}
 private static String string(JsonNode body,String field){var v=body.get(field);if(v==null||v.isNull())return null;if(!v.isTextual())throw WalletException.invalid();return v.textValue();}
 private static Long number(JsonNode body,String field){var v=body.get(field);if(v==null||v.isNull())return null;if(!v.isIntegralNumber()||!v.canConvertToLong())throw WalletException.invalid();return v.longValue();}
 private static Integer integer(JsonNode body,String field){Long n=number(body,field);if(n==null)return null;if(n<0||n>Integer.MAX_VALUE)throw WalletException.invalid();return n.intValue();}
}
