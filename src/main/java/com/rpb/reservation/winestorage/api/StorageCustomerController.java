package com.rpb.reservation.winestorage.api;

import com.rpb.reservation.winestorage.application.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;

@RestController
@RequestMapping("/api/v1/public/stores/{storeId}/wine-storage")
public class StorageCustomerController {
 private final StorageIdentity identity;private final StorageQueryService query;private final StorageApplicationService applications;private final StorageWithdrawalService withdrawals;private final StoragePhotoService photos;
 public StorageCustomerController(StorageIdentity identity,StorageQueryService query,StorageApplicationService applications,StorageWithdrawalService withdrawals,StoragePhotoService photos){this.identity=identity;this.query=query;this.applications=applications;this.withdrawals=withdrawals;this.photos=photos;}
 @GetMapping("/home") public Home home(@PathVariable UUID storeId,HttpServletRequest req){var p=identity.customer(req);return query.customer(identity.scope(p.tenantId(),storeId),p);}
 @GetMapping("/product-options") public StorageQueryService.ProductOptions options(@PathVariable UUID storeId,@RequestParam(defaultValue="")String query,@RequestParam(required=false)UUID categoryId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="6")int size,@RequestParam(defaultValue="")String barcode,HttpServletRequest req){var p=identity.customer(req);return this.query.options(identity.scope(p.tenantId(),storeId),p,query,categoryId,page,size,barcode);}
 @GetMapping("/products") public List<Product> products(@PathVariable UUID storeId,@RequestParam(defaultValue="")String query,HttpServletRequest req){var p=identity.customer(req);return this.query.products(identity.scope(p.tenantId(),storeId),p,query);}
 @PostMapping("/applications") public Application apply(@PathVariable UUID storeId,@RequestBody Apply c,HttpServletRequest req){var p=identity.customer(req);return applications.apply(identity.scope(p.tenantId(),storeId),p,c);}
 @PostMapping("/applications/{id}/cancel") public Application cancel(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Key c,HttpServletRequest req){var p=identity.customer(req);return applications.cancel(identity.scope(p.tenantId(),storeId),p,id,c);}
 @PostMapping("/intents") public Intent intent(@PathVariable UUID storeId,@RequestBody IntentWrite c,HttpServletRequest req){var p=identity.customer(req);return withdrawals.create(identity.scope(p.tenantId(),storeId),p,c);}
 @PostMapping("/scan") public Intent scan(@PathVariable UUID storeId,@RequestBody Scan c,HttpServletRequest req){var p=identity.customer(req);return withdrawals.scan(identity.scope(p.tenantId(),storeId),p,c.code());}
 @PostMapping("/intents/{id}/authorize") public Intent authorize(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Authorize c,HttpServletRequest req){var p=identity.customer(req);return withdrawals.authorize(identity.scope(p.tenantId(),storeId),p,id,c);}
 @PostMapping("/intents/{id}/cancel") public Intent cancelIntent(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Key c,HttpServletRequest req){var p=identity.customer(req);return withdrawals.cancel(identity.scope(p.tenantId(),storeId),p,id,c);}
 @PostMapping("/photos") public Photo upload(@PathVariable UUID storeId,@RequestPart("image")MultipartFile image,HttpServletRequest req)throws IOException{var p=identity.customer(req);if(image.isEmpty()||image.getSize()>5*1024*1024)throw com.rpb.reservation.winestorage.domain.StorageException.invalid();return photos.upload(identity.scope(p.tenantId(),storeId),p,image.getContentType(),image.getBytes());}
 @GetMapping("/photos/{id}") public ResponseEntity<byte[]> image(@PathVariable UUID storeId,@PathVariable UUID id,HttpServletRequest req){var p=identity.customer(req);var img=photos.image(identity.scope(p.tenantId(),storeId),p,id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(img.contentType())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(img.bytes());}
}
