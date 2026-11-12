package com.rpb.reservation.wallet.api;

import com.rpb.reservation.wallet.application.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.wallet.application.WalletCommands.*;
import static com.rpb.reservation.wallet.application.WalletViews.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/wallet/collection-profile")
public class WalletCollectionSettingsController {
    private final WalletApiIdentity identity;private final WalletCollectionSettingsService settings;
    public WalletCollectionSettingsController(WalletApiIdentity identity,WalletCollectionSettingsService settings) {this.identity=identity;this.settings=settings;}
    @GetMapping public CollectionSettings get(@PathVariable UUID storeId) {var a=identity.staff();return settings.get(identity.scope(a.tenantId(),storeId),a);}
    @PutMapping public CollectionSettings save(@PathVariable UUID storeId,@RequestBody CollectionSettingsUpdate body) {var a=identity.staff();return settings.save(identity.scope(a.tenantId(),storeId),a,body);}
}
