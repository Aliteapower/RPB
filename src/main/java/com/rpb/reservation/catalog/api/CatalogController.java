package com.rpb.reservation.catalog.api;
import com.rpb.reservation.catalog.access.CatalogAccess;
import com.rpb.reservation.catalog.application.*;
import com.rpb.reservation.catalog.application.CatalogDtos.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/catalog")
public class CatalogController {
 private final CatalogBarcodeService barcodes;private final CatalogAccess identity;private final CatalogService service;
 public CatalogController(CatalogAccess identity,CatalogService service,CatalogBarcodeService barcodes){this.barcodes=barcodes;this.identity=identity;this.service=service;}
 private StoreScope scope(UUID store){return new StoreScope(new TenantId(identity.actor().tenantId()),store);}
 @GetMapping("/products/{id}/barcodes")public CatalogBarcodeService.Barcodes barcodes(@PathVariable UUID storeId,@PathVariable UUID id){return barcodes.list(scope(storeId),identity.actor(),id);}
 @PostMapping("/products/{id}/barcodes")public CatalogBarcodeService.Barcodes barcode(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody CatalogBarcodeService.Write c){return barcodes.save(scope(storeId),identity.actor(),id,c);}
 @GetMapping("/categories")public List<Category> categories(@PathVariable UUID storeId){return service.categories(scope(storeId),identity.actor());}
 @PostMapping("/categories")public Category createCategory(@PathVariable UUID storeId,@RequestBody CategoryWrite c){return service.category(scope(storeId),identity.actor(),null,c);}
 @PutMapping("/categories/{id}")public Category updateCategory(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody CategoryWrite c){return service.category(scope(storeId),identity.actor(),id,c);}
 @GetMapping("/products")public List<Product> products(@PathVariable UUID storeId){return service.products(scope(storeId),identity.actor());}
 @PostMapping("/products")public Product create(@PathVariable UUID storeId,@RequestBody ProductWrite c){return service.save(scope(storeId),identity.actor(),null,c);}
 @PutMapping("/products/{id}")public Product update(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody ProductWrite c){return service.save(scope(storeId),identity.actor(),id,c);}
 @PostMapping("/products/{id}/status")public Product status(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Status c){return service.status(scope(storeId),identity.actor(),id,c);}
 @PostMapping("/products/{id}/listing")public Product listing(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Listing c){return service.listing(scope(storeId),identity.actor(),id,c);}
 @PostMapping("/products/{id}/quote")public Quote quote(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody QuoteRequest c){return service.quote(scope(storeId),identity.actor(),id,c);}
}
