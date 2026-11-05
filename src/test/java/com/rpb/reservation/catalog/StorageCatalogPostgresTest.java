package com.rpb.reservation.catalog;

import com.rpb.reservation.catalog.persistence.CatalogRepository;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageCatalogPostgresTest extends CatalogPostgresTest {
 @Test void allStorageMatchesAreReachableAcrossStablePages(){
  jdbc.update("insert into catalog_products(id,tenant_id,category_id,code,name_zh,name_en,kind,unit,status) select gen_random_uuid(),?,?,'WINE'||n,'寄存酒'||n,'Wine','physical','瓶','active' from generate_series(1,17) n",tenant,product.categoryId());
  jdbc.update("insert into catalog_product_stores(tenant_id,product_id,store_id,price_cents,channels,enabled) select tenant_id,id,?,100,array['storage'],false from catalog_products where tenant_id=? and code like 'WINE%'",store,tenant);
  var r=new CatalogRepository(jdbc);var first=r.storageProducts(scope,"",0,12);var next=r.storageProducts(scope,"",1,12);
  assertThat(first.total()).isEqualTo(17);assertThat(first.items()).hasSize(12);assertThat(first.hasMore()).isTrue();assertThat(next.items()).hasSize(5);assertThat(next.hasMore()).isFalse();
  var ids=new HashSet<UUID>();first.items().forEach(p->ids.add(p.id()));next.items().forEach(p->assertThat(ids.add(p.id())).isTrue());assertThat(ids).hasSize(17);
 }
 @Test void storageFilterRunsBeforePaginationIncludesDisabledAndKeepsScopes(){
  jdbc.update("update catalog_product_stores set channels=array['storage'],enabled=false where tenant_id=? and product_id=? and store_id=?",tenant,product.id(),store);jdbc.update("update catalog_products set status='inactive' where id=?",product.id());
  jdbc.update("insert into catalog_products(id,tenant_id,category_id,code,name_zh,name_en,kind,unit,status) select gen_random_uuid(),?,?,'A'||n,'其他商品'||n,'Other','physical','件','active' from generate_series(1,501) n",tenant,product.categoryId());
  var r=new CatalogRepository(jdbc);var result=r.storageProducts(scope,"",0,12);
  assertThat(result.total()).isEqualTo(1);assertThat(result.items()).singleElement().satisfies(v->{assertThat(v.id()).isEqualTo(product.id());assertThat(v.listingEnabled()).isFalse();assertThat(v.status()).isEqualTo("inactive");});
  assertThat(r.storageProducts(scope,"Coffee",0,1).items()).hasSize(1);assertThat(r.storageProducts(scope,"%_",0,12).total()).isZero();assertThat(r.storageProducts(scope,"",1,1).items()).isEmpty();
  assertThat(r.storageProducts(new StoreScope(new TenantId(UUID.randomUUID()),store),"",0,12).total()).isZero();assertThat(r.storageProducts(new StoreScope(new TenantId(tenant),UUID.randomUUID()),"",0,12).total()).isZero();
 }
}
