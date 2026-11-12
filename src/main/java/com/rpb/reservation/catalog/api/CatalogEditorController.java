package com.rpb.reservation.catalog.api;
import com.rpb.reservation.catalog.access.CatalogAccess;
import com.rpb.reservation.catalog.application.CatalogProductEditService;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/catalog/products")
public class CatalogEditorController {
 private final CatalogAccess identity;private final CatalogProductEditService editor;
 public CatalogEditorController(CatalogAccess a,CatalogProductEditService e){identity=a;editor=e;}
 private StoreScope scope(UUID id){return new StoreScope(new TenantId(identity.actor().tenantId()),id);}
 @GetMapping("/{id}/editor")public CatalogProductEditService.Detail get(@PathVariable UUID storeId,@PathVariable UUID id){return editor.get(scope(storeId),identity.actor(),id);}
 @PostMapping("/editor")public CatalogProductEditService.Detail create(@PathVariable UUID storeId,@RequestBody CatalogProductEditService.Write c){return editor.save(scope(storeId),identity.actor(),null,c);}
 @PutMapping("/{id}/editor")public CatalogProductEditService.Detail update(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody CatalogProductEditService.Write c){return editor.save(scope(storeId),identity.actor(),id,c);}
}
