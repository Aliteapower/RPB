package com.rpb.reservation.catalog;
import com.rpb.reservation.catalog.application.*;
import com.rpb.reservation.catalog.application.CatalogDtos.Listing;
import com.rpb.reservation.catalog.persistence.CatalogRepository;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
public class StorageProductEligibilityTest extends CatalogPostgresTest {
 StorageProductCatalog storage(){return new StorageProductCatalog(new CatalogRepository(jdbc),products,jdbc);}
 void enable(){product=tx.execute(t->catalog.listing(scope,actor,product.id(),new Listing(500,List.of("storage"),true,product.listingVersion(),key())));}
 @Test void staffCannotSeeOrAssociateProductsOutsideStorageChannel(){assertThat(storage().search(scope,"",true)).isEmpty();assertThat(storage().search(scope,"",false)).isEmpty();assertThatThrownBy(()->tx.execute(t->storage().associate(scope,product.id(),null,"","",actor.actorId()))).hasMessageContaining("不适用");enable();assertThat(storage().search(scope,"",true)).extracting(p->p.id()).containsExactly(product.id());}
 @Test void staffAndCustomerApplySameStoreCategoryKindAndStatusFilters(){enable();for(String update:List.of("update catalog_product_stores set enabled=false where product_id=?","update catalog_products set status='inactive' where id=?","update catalog_products set status='archived' where id=?","update catalog_products set kind='service' where id=?")){
   tx.execute(t->{jdbc.update(update,product.id());assertThat(storage().search(scope,"",true)).isEmpty();assertThat(storage().search(scope,"",false)).isEmpty();assertThatThrownBy(()->storage().selectable(scope,product.id())).isInstanceOf(RuntimeException.class);t.setRollbackOnly();return null;});}
  tx.execute(t->{jdbc.update("update catalog_categories set enabled=false where id=?",product.categoryId());assertThat(storage().search(scope,"",true)).isEmpty();assertThatThrownBy(()->storage().selectable(scope,product.id())).isInstanceOf(RuntimeException.class);t.setRollbackOnly();return null;});
  assertThat(storage().search(new StoreScope(new TenantId(tenant),UUID.randomUUID()),"",true)).isEmpty();assertThat(storage().search(new StoreScope(new TenantId(UUID.randomUUID()),store),"",true)).isEmpty();
 }
}
