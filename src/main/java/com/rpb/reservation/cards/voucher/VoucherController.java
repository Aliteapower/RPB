package com.rpb.reservation.cards.voucher;

import com.rpb.reservation.cards.voucher.VoucherDtos.*;
import com.rpb.reservation.wallet.api.WalletApiIdentity;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/wallet/cards")
public class VoucherController {
    private final WalletApiIdentity identity;
    private final VoucherService vouchers;

    public VoucherController(WalletApiIdentity identity, VoucherService vouchers) {
        this.identity = identity;
        this.vouchers = vouchers;
    }

    @GetMapping("/voucher-campaigns")
    public List<Summary> list(@PathVariable UUID storeId) {
        var actor = identity.staff();
        return vouchers.list(identity.scope(actor.tenantId(), storeId), actor);
    }

    @GetMapping("/voucher-campaigns/{id}")
    public Campaign detail(@PathVariable UUID storeId, @PathVariable UUID id) {
        var actor = identity.staff();
        return vouchers.detail(identity.scope(actor.tenantId(), storeId), actor, id);
    }

    @PostMapping("/voucher-campaigns")
    public Campaign create(@PathVariable UUID storeId, @RequestBody Write input) {
        var actor = identity.staff();
        return vouchers.save(identity.scope(actor.tenantId(), storeId), actor, null, input);
    }

    @PutMapping("/voucher-campaigns/{id}")
    public Campaign update(@PathVariable UUID storeId, @PathVariable UUID id, @RequestBody Write input) {
        var actor = identity.staff();
        return vouchers.save(identity.scope(actor.tenantId(), storeId), actor, id, input);
    }

    @PostMapping("/voucher-campaigns/{id}/status")
    public Campaign state(@PathVariable UUID storeId, @PathVariable UUID id, @RequestBody State input) {
        var actor = identity.staff();
        return vouchers.state(identity.scope(actor.tenantId(), storeId), actor, id, input.status(), input.version());
    }

    @DeleteMapping("/voucher-campaigns/{id}")
    public void delete(@PathVariable UUID storeId, @PathVariable UUID id, @RequestParam int version) {
        var actor = identity.staff();
        vouchers.delete(identity.scope(actor.tenantId(), storeId), actor, id, version);
    }

    @PostMapping("/vouchers/redeem")
    public ScanResult redeem(@PathVariable UUID storeId, @RequestBody Token token) {
        var actor = identity.staff();
        return vouchers.redeem(identity.scope(actor.tenantId(), storeId), actor, token.code());
    }
}
