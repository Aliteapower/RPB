package com.rpb.reservation.wallet.redemption;
import com.rpb.reservation.wallet.api.WalletApiIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.wallet.redemption.RedemptionDtos.*;
@RestController
public class RedemptionController {
 private final WalletApiIdentity identity;private final RedemptionService service;
 public RedemptionController(WalletApiIdentity identity,RedemptionService service){this.identity=identity;this.service=service;}
 @PostMapping("/api/v1/stores/{storeId}/wallet/points-redemptions") public View create(@PathVariable UUID storeId,@RequestBody Create body){var a=identity.staff();return service.create(identity.scope(a.tenantId(),storeId),a,body);}
 @GetMapping("/api/v1/stores/{storeId}/wallet/points-redemptions/{id}") public View get(@PathVariable UUID storeId,@PathVariable UUID id){var a=identity.staff();return service.get(identity.scope(a.tenantId(),storeId),a,id);}
 @PostMapping("/api/v1/stores/{storeId}/wallet/points-redemptions/{id}/code") public Code code(@PathVariable UUID storeId,@PathVariable UUID id){var a=identity.staff();return service.issue(identity.scope(a.tenantId(),storeId),a,id);}
 @PostMapping("/api/v1/stores/{storeId}/wallet/points-redemptions/{id}/cancel") public View cancel(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Cancel body){var a=identity.staff();return service.cancel(identity.scope(a.tenantId(),storeId),a,id,body);}
 @PostMapping("/api/v1/public/stores/{storeId}/wallet/points-redemption-code") public View scan(@PathVariable UUID storeId,@RequestBody CodeInput body,HttpServletRequest request){var p=identity.customer(request);return service.scan(identity.scope(p.tenantId(),storeId),p,body.code());}
 @GetMapping("/api/v1/public/stores/{storeId}/wallet/points-redemptions/{id}") public View own(@PathVariable UUID storeId,@PathVariable UUID id,HttpServletRequest request){var p=identity.customer(request);return service.own(identity.scope(p.tenantId(),storeId),p,id);}
 @PostMapping("/api/v1/public/stores/{storeId}/wallet/points-redemptions/{id}/confirm") public View confirm(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Confirm body,HttpServletRequest request){var p=identity.customer(request);return service.confirm(identity.scope(p.tenantId(),storeId),p,id,body);}
}
