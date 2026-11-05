package com.rpb.reservation.wallet.collectionmethods;
import com.rpb.reservation.wallet.api.WalletApiIdentity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.rpb.reservation.wallet.collectionmethods.CollectionMethods.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/wallet/collection-methods")
public class CollectionMethodController {
 private final WalletApiIdentity identity;private final CollectionMethodService service;
 public CollectionMethodController(WalletApiIdentity identity,CollectionMethodService service){this.identity=identity;this.service=service;}
 @GetMapping public Settings get(@PathVariable UUID storeId){var a=identity.staff();return service.get(identity.scope(a.tenantId(),storeId),a);}
 @PutMapping public Settings save(@PathVariable UUID storeId,@RequestBody Update body){var a=identity.staff();return service.save(identity.scope(a.tenantId(),storeId),a,body);}
}
