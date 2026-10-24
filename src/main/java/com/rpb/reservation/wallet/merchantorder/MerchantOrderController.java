package com.rpb.reservation.wallet.merchantorder;
import com.rpb.reservation.wallet.api.WalletApiIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.*;
@RestController
public class MerchantOrderController {
 private final WalletApiIdentity identity;private final MerchantOrderService service;
 public MerchantOrderController(WalletApiIdentity identity,MerchantOrderService service){this.identity=identity;this.service=service;}
 @PostMapping("/api/v1/stores/{storeId}/wallet/merchant-orders") public View create(@PathVariable UUID storeId,@RequestBody Create body){var a=identity.staff();return service.create(identity.scope(a.tenantId(),storeId),a,body);}
 @GetMapping("/api/v1/stores/{storeId}/wallet/merchant-orders/{id}") public View get(@PathVariable UUID storeId,@PathVariable UUID id){var a=identity.staff();return service.get(identity.scope(a.tenantId(),storeId),a,id);}
 @PostMapping("/api/v1/stores/{storeId}/wallet/merchant-orders/{id}/code") public Code code(@PathVariable UUID storeId,@PathVariable UUID id){var a=identity.staff();return service.issue(identity.scope(a.tenantId(),storeId),a,id);}
 @PostMapping("/api/v1/stores/{storeId}/wallet/merchant-orders/{id}/cancel") public View cancel(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Cancel body){var a=identity.staff();return service.cancel(identity.scope(a.tenantId(),storeId),a,id,body);}
 @PostMapping("/api/v1/public/stores/{storeId}/wallet/merchant-order-code") public View scan(@PathVariable UUID storeId,@RequestBody CodeInput body,HttpServletRequest request){var p=identity.customer(request);return service.scan(identity.scope(p.tenantId(),storeId),p,body.code());}
 @GetMapping("/api/v1/public/stores/{storeId}/wallet/merchant-orders/{id}") public View own(@PathVariable UUID storeId,@PathVariable UUID id,HttpServletRequest request){var p=identity.customer(request);return service.own(identity.scope(p.tenantId(),storeId),p,id);}
 @PostMapping("/api/v1/public/stores/{storeId}/wallet/merchant-orders/{id}/confirm") public View confirm(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Confirm body,HttpServletRequest request){var p=identity.customer(request);return service.confirm(identity.scope(p.tenantId(),storeId),p,id,body);}
}
