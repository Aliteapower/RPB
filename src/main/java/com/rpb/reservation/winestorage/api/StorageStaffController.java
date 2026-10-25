package com.rpb.reservation.winestorage.api;

import com.rpb.reservation.winestorage.application.*;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/wine-storage")
public class StorageStaffController {
 private final StorageIdentity identity;private final StorageQueryService query;private final StorageApplicationService applications;private final StorageWithdrawalService withdrawals;private final StoragePhotoService photos;
 public StorageStaffController(StorageIdentity identity,StorageQueryService query,StorageApplicationService applications,StorageWithdrawalService withdrawals,StoragePhotoService photos){this.identity=identity;this.query=query;this.applications=applications;this.withdrawals=withdrawals;this.photos=photos;}
 @GetMapping("/home") public Home home(@PathVariable UUID storeId){var a=identity.staff();return query.staff(identity.scope(a.tenantId(),storeId),a);}
 @GetMapping("/products") public List<Product> products(@PathVariable UUID storeId,@RequestParam(defaultValue="")String query){var a=identity.staff();return this.query.products(identity.scope(a.tenantId(),storeId),a,query);}
 @PostMapping("/rules") public Rule rule(@PathVariable UUID storeId,@RequestBody RuleWrite c){var a=identity.staff();return applications.rule(identity.scope(a.tenantId(),storeId),a,c);}
 @PostMapping("/applications/{id}/receive") public Application receive(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Receive c){var a=identity.staff();return applications.receive(identity.scope(a.tenantId(),storeId),a,id,c);}
 @PostMapping("/applications/{id}/reject") public Application reject(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Key c){var a=identity.staff();return applications.reject(identity.scope(a.tenantId(),storeId),a,id,c);}
 @PostMapping("/intents") public Intent intent(@PathVariable UUID storeId,@RequestBody IntentWrite c){var a=identity.staff();return withdrawals.create(identity.scope(a.tenantId(),storeId),a,c);}
 @PostMapping("/scan") public Intent scan(@PathVariable UUID storeId,@RequestBody Scan c){var a=identity.staff();return withdrawals.scan(identity.scope(a.tenantId(),storeId),a,c.code());}
 @PostMapping("/intents/{id}/deliver") public Intent deliver(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Key c){var a=identity.staff();return withdrawals.deliver(identity.scope(a.tenantId(),storeId),a,id,c);}
 @PostMapping("/intents/{id}/cancel") public Intent cancelIntent(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Key c){var a=identity.staff();return withdrawals.cancel(identity.scope(a.tenantId(),storeId),a,id,c);}
 @PostMapping("/items/{id}/corrections") public Event correction(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Correction c){var a=identity.staff();return applications.correct(identity.scope(a.tenantId(),storeId),a,id,c);}
 @PostMapping("/items/{id}/photos") public Event addPhotos(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody PhotoAdd c){var a=identity.staff();return applications.addPhotos(identity.scope(a.tenantId(),storeId),a,id,c);}
 @PostMapping("/photos") public Photo upload(@PathVariable UUID storeId,@RequestParam UUID customerId,@RequestParam String phase,@RequestPart("image")MultipartFile image)throws IOException{var a=identity.staff();if(image.isEmpty()||image.getSize()>5*1024*1024)throw com.rpb.reservation.winestorage.domain.StorageException.invalid();return photos.upload(identity.scope(a.tenantId(),storeId),a,customerId,phase,image.getContentType(),image.getBytes());}
 @GetMapping("/photos/{id}") public ResponseEntity<byte[]> image(@PathVariable UUID storeId,@PathVariable UUID id){var a=identity.staff();var img=photos.image(identity.scope(a.tenantId(),storeId),a,id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(img.contentType())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(img.bytes());}
}
