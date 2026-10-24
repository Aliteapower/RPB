package com.rpb.reservation.wallet.api;
import com.rpb.reservation.wallet.integration.WalletPointsAdapter;
import com.rpb.reservation.loyalty.application.PointsViews.*;
import com.rpb.reservation.wallet.api.WalletPointsRequests.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/wallet")
public class WalletPointsStaffController {
 private final WalletApiIdentity identity;private final WalletPointsAdapter points;
 public WalletPointsStaffController(WalletApiIdentity identity,WalletPointsAdapter points){this.identity=identity;this.points=points;}
 @GetMapping("/points/context")public com.rpb.reservation.wallet.application.WalletViews.Context context(@PathVariable UUID storeId){var a=identity.staff();return points.context(identity.scope(a.tenantId(),storeId),a);}
 @GetMapping("/customers/{customerId}/points")public Account get(@PathVariable UUID storeId,@PathVariable UUID customerId){var a=identity.staff();return points.get(identity.scope(a.tenantId(),storeId),a,customerId);}
 @PostMapping("/customers/{customerId}/points/grants")public Result grant(@PathVariable UUID storeId,@PathVariable UUID customerId,@RequestBody Grant body){var a=identity.staff();return points.mutate(identity.scope(a.tenantId(),storeId),a,customerId,"manual_grant",body.command());}
 @PostMapping("/customers/{customerId}/points/deductions")public Result deduct(@PathVariable UUID storeId,@PathVariable UUID customerId,@RequestBody Deduction body){var a=identity.staff();return points.mutate(identity.scope(a.tenantId(),storeId),a,customerId,"manual_deduct",body.command());}
 @PostMapping("/customers/{customerId}/points/redemptions")public Result redeem(@PathVariable UUID storeId,@PathVariable UUID customerId,@RequestBody Redeem body){var a=identity.staff();return points.mutate(identity.scope(a.tenantId(),storeId),a,customerId,"redemption",body.command());}
 @PostMapping("/points/entries/{entryId}/reverse")public Result reverse(@PathVariable UUID storeId,@PathVariable UUID entryId,@RequestBody Reversal body){var a=identity.staff();return points.reverse(identity.scope(a.tenantId(),storeId),a,entryId,body.command());}
}
