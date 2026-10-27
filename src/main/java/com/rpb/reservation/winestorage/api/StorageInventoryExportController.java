package com.rpb.reservation.winestorage.api;

import com.rpb.reservation.winestorage.application.StorageInventoryExportService;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/wine-storage/inventory/export")
public class StorageInventoryExportController {
 private final StorageIdentity identity;private final StorageInventoryExportService service;
 public StorageInventoryExportController(StorageIdentity identity,StorageInventoryExportService service){this.identity=identity;this.service=service;}
 @GetMapping public ResponseEntity<byte[]> export(@PathVariable UUID storeId,@RequestParam(defaultValue="")String query,@RequestParam(defaultValue="all")String status,@RequestParam(defaultValue="zh-CN")String locale){var actor=identity.staff();var bytes=service.export(identity.scope(actor.tenantId(),storeId),actor,query,status,locale);return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"storage-inventory-"+storeId+".xlsx\"").cacheControl(CacheControl.noStore().cachePrivate()).body(bytes);}
}
