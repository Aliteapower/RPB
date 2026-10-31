package com.rpb.reservation.wallet.api;

import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.wallet.application.WalletCommands.*;
import com.rpb.reservation.wallet.application.WalletViews.Collection;

/** Shared-brand reads and manager exception recovery, separate from normal cashier commands. */
@RestController
public class WalletSupportController {
    private final WalletApiIdentity identity;private final WalletAccountService accounts;private final WalletCollectionExceptionService exceptions;
    public WalletSupportController(WalletApiIdentity identity,WalletAccountService accounts,WalletCollectionExceptionService exceptions) {this.identity=identity;this.accounts=accounts;this.exceptions=exceptions;}
    @GetMapping("/api/v1/stores/{storeId}/wallet/logo/{assetId}")
    public ResponseEntity<Resource> staffLogo(@PathVariable UUID storeId,@PathVariable UUID assetId) {var a=identity.staff();return media(accounts.logo(identity.scope(a.tenantId(),storeId),a,assetId));}
    @GetMapping("/api/v1/public/stores/{storeId}/wallet/logo/{assetId}")
    public ResponseEntity<Resource> customerLogo(@PathVariable UUID storeId,@PathVariable UUID assetId,HttpServletRequest request) {var p=identity.customer(request);return media(accounts.ownLogo(identity.scope(p.tenantId(),storeId),p,assetId));}
    @GetMapping("/api/v1/stores/{storeId}/wallet/collection-exceptions")
    public List<Collection> list(@PathVariable UUID storeId) {var a=identity.staff();return exceptions.list(identity.scope(a.tenantId(),storeId),a);}
    @PostMapping("/api/v1/stores/{storeId}/wallet/collection-exceptions")
    public Collection record(@PathVariable UUID storeId,@RequestBody CollectionException body) {var a=identity.staff();return exceptions.record(identity.scope(a.tenantId(),storeId),a,body);}
    private ResponseEntity<Resource> media(CallScreenMediaContent c) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(c.contentType())).contentLength(c.byteSize()).body(c.resource());}
}
