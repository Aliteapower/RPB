package com.rpb.reservation.winestorage;
import com.rpb.reservation.catalog.application.StorageProductCatalog;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.application.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class StorageProductQueryTest {
 @Test void staffPagesAuthorizeBeforeCatalogQueries(){
  var scope=new StoreScope(new TenantId(UUID.randomUUID()),UUID.randomUUID());var actor=new CurrentActor(scope.tenantId().value(),UUID.randomUUID(),"staff",Set.of(),Set.of("wine_storage.view"),Set.of(scope.storeId().value()));
  var access=mock(StorageAccess.class);var products=mock(StorageProductCatalog.class);var service=new StorageQueryService(access,null,products,null,null);var category=UUID.randomUUID();
  when(products.options(scope,"wine",category,2,6,"")).thenReturn(new StorageProductCatalog.Options(List.of(),false,2,List.of()));
  assertThat(service.staffOptions(scope,actor,"wine",category,2,6).page()).isEqualTo(2);
  var order=inOrder(access,products);order.verify(access).staff(scope,actor,"wine_storage.view",false);order.verify(products).options(scope,"wine",category,2,6,"");
  reset(products);doThrow(new IllegalStateException("denied")).when(access).staff(scope,actor,"wine_storage.view",false);
  assertThatThrownBy(()->service.staffOptions(scope,actor,"",null,0,6)).hasMessage("denied");verifyNoInteractions(products);
 }
 @Test void singleProductLookupAuthorizesBeforeUsingTheSameCatalogEligibility(){
  var scope=new StoreScope(new TenantId(UUID.randomUUID()),UUID.randomUUID());var actor=new CurrentActor(scope.tenantId().value(),UUID.randomUUID(),"staff",Set.of(),Set.of("wine_storage.view"),Set.of(scope.storeId().value()));
  var access=mock(StorageAccess.class);var products=mock(StorageProductCatalog.class);var service=new StorageQueryService(access,null,products,null,null);var id=UUID.randomUUID();var product=mock(com.rpb.reservation.catalog.application.CatalogDtos.Product.class);when(product.id()).thenReturn(id);when(products.selectable(scope,id)).thenReturn(product);
  assertThat(service.product(scope,actor,id).id()).isEqualTo(id);verify(access).staff(scope,actor,"wine_storage.view",false);verify(products).selectable(scope,id);
  reset(products);doThrow(new IllegalStateException("denied")).when(access).staff(scope,actor,"wine_storage.view",false);assertThatThrownBy(()->service.product(scope,actor,id)).hasMessage("denied");verifyNoInteractions(products);
 }
}
