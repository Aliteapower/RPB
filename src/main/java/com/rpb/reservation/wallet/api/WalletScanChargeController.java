package com.rpb.reservation.wallet.api;

import com.rpb.reservation.wallet.application.WalletScanChargeService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.wallet.application.WalletCommands.*;
import static com.rpb.reservation.wallet.application.WalletViews.*;

@RestController
@RequestMapping("/api/v1")
public class WalletScanChargeController {
    private final WalletApiIdentity identity;
    private final WalletScanChargeService charges;
    public WalletScanChargeController(WalletApiIdentity identity,WalletScanChargeService charges) {this.identity=identity;this.charges=charges;}
    @PostMapping("/stores/{storeId}/wallet/scan-charges")
    public Paid charge(@PathVariable UUID storeId,@RequestBody ScanCharge body) {
        var actor=identity.staff();return charges.charge(identity.scope(actor.tenantId(),storeId),actor,body);
    }
    @PostMapping("/public/stores/{storeId}/wallet/payment-codes/status")
    public PaymentCodeStatus status(@PathVariable UUID storeId,@RequestBody Code body,HttpServletRequest request) {
        var principal=identity.customer(request);return charges.status(identity.scope(principal.tenantId(),storeId),principal,body.code());
    }
    public record Code(String code) {}
}
