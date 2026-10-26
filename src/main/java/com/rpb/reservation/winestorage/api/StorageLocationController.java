package com.rpb.reservation.winestorage.api;

import com.rpb.reservation.winestorage.application.*;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/wine-storage/locations")
public class StorageLocationController {
 private final StorageIdentity identity;private final StorageLocationService locations;
 public StorageLocationController(StorageIdentity identity,StorageLocationService locations){this.identity=identity;this.locations=locations;}
 @GetMapping public List<StorageLocation> list(@PathVariable UUID storeId){var actor=identity.staff();return locations.list(identity.scope(actor.tenantId(),storeId),actor);}
 @PostMapping public StorageLocation create(@PathVariable UUID storeId,@RequestBody LocationWrite c){var actor=identity.staff();return locations.save(identity.scope(actor.tenantId(),storeId),actor,null,c);}
 @PostMapping("/{id}") public StorageLocation update(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody LocationWrite c){var actor=identity.staff();return locations.save(identity.scope(actor.tenantId(),storeId),actor,id,c);}
}
