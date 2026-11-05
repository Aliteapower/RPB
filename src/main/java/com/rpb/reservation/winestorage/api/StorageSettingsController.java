package com.rpb.reservation.winestorage.api;

import com.rpb.reservation.winestorage.application.StorageSettingsService;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/wine-storage/settings")
public class StorageSettingsController {
 private final StorageIdentity identity;private final StorageSettingsService settings;
 public StorageSettingsController(StorageIdentity identity,StorageSettingsService settings){this.identity=identity;this.settings=settings;}
 @PostMapping public Settings save(@PathVariable UUID storeId,@RequestBody SettingsWrite c){var a=identity.staff();return settings.save(identity.scope(a.tenantId(),storeId),a,c);}
}
