package com.rpb.reservation.winestorage.api;

import com.rpb.reservation.winestorage.application.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/wine-storage/inventory")
public class StorageInventoryController {
 private final StorageIdentity identity;private final StorageInventoryQuery query;
 public StorageInventoryController(StorageIdentity identity,StorageInventoryQuery query){this.identity=identity;this.query=query;}
 @GetMapping public StorageInventoryDtos.Page list(@PathVariable UUID storeId,@RequestParam(defaultValue="")String query,@RequestParam(defaultValue="all")String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){var actor=identity.staff();return this.query.list(identity.scope(actor.tenantId(),storeId),actor,query,status,page,size);}
}
