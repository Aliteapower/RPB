package com.rpb.reservation.wallet.api;
import com.rpb.reservation.wallet.funding.application.*;

import com.rpb.reservation.wallet.application.WalletViews.Account;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/wallet")
public class WalletFundingController {
 private final WalletApiIdentity identity;private final WalletFundingOperations operations;
 public WalletFundingController(WalletApiIdentity identity,WalletFundingOperations operations){this.identity=identity;this.operations=operations;}
 @GetMapping("/accounts/{accountId}/funding-lots") public List<WalletFundingOperations.LotView> lots(@PathVariable UUID storeId,@PathVariable UUID accountId){var a=identity.staff();return operations.lots(identity.scope(a.tenantId(),storeId),a,accountId);}
 @PostMapping("/charge-previews") public WalletFundingOperations.ChargeSplit charge(@PathVariable UUID storeId,@RequestBody WalletFundingOperations.ChargePreview body){var a=identity.staff();return operations.chargePreview(identity.scope(a.tenantId(),storeId),a,body);}
 @PostMapping("/refund-previews") public WalletFundingOperations.RefundPreview refund(@PathVariable UUID storeId,@RequestBody WalletFundingOperations.RefundPreviewRequest body){var a=identity.staff();return operations.refundPreview(identity.scope(a.tenantId(),storeId),a,body);}
 @PostMapping("/funding-lots/{lotId}/exit-bonus") public Account exit(@PathVariable UUID storeId,@PathVariable UUID lotId,@RequestBody WalletFundingOperations.BonusExit body,@RequestHeader(value="X-RPB-Wallet-Model",defaultValue="")String model){var a=identity.staff();WalletModelGuard.require(model);return operations.exit(identity.scope(a.tenantId(),storeId),a,lotId,body);}
}
