package com.rpb.reservation.winestorage.api;

import com.rpb.reservation.winestorage.application.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/wine-storage/reports")
public class StorageReportController {
 private final StorageIdentity identity;private final StorageReportQuery query;
 public StorageReportController(StorageIdentity identity,StorageReportQuery query){this.identity=identity;this.query=query;}
 @GetMapping public StorageReportDtos.Page list(@PathVariable UUID storeId,@RequestParam(defaultValue="deposit")String kind,@RequestParam(defaultValue="")String query,@RequestParam(defaultValue="all")String status,@RequestParam(defaultValue="")String from,@RequestParam(defaultValue="")String to,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){var a=identity.staff();return this.query.list(identity.scope(a.tenantId(),storeId),a,kind,query,status,from,to,page,size);}
 @GetMapping("/{kind}/{id}") public StorageReportDtos.Detail detail(@PathVariable UUID storeId,@PathVariable String kind,@PathVariable UUID id){var a=identity.staff();return query.detail(identity.scope(a.tenantId(),storeId),a,kind,id);}
}
