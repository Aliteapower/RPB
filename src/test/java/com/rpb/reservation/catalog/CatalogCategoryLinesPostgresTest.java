package com.rpb.reservation.catalog;
import com.rpb.reservation.catalog.application.*;
import com.rpb.reservation.catalog.application.CatalogDtos.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CatalogCategoryLinesPostgresTest extends CatalogPostgresTest {
 @Test void backfillUsesActualChannelsAndLeavesUnusedCategoriesUnassigned(){
  tx.execute(status->{
   jdbc.update("update catalog_product_stores set channels=array['cards','storage'] where tenant_id=? and product_id=?",tenant,product.id());
   var unused=UUID.randomUUID();jdbc.update("insert into catalog_categories(id,tenant_id,code,name_zh,name_en) values(?,?,'UNUSED','集卡','Unused')",unused,tenant);
   jdbc.execute("alter table catalog_categories drop column product_lines");
   try{jdbc.execute(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/V127__catalog_category_product_lines.sql")));}catch(java.io.IOException e){throw new RuntimeException(e);}
   var cats=new com.rpb.reservation.catalog.persistence.CatalogRepository(jdbc).categories(tenant);
   assertThat(cats.stream().filter(c->c.id().equals(product.categoryId())).findFirst().orElseThrow().productLines()).containsExactly("wallet","wine_storage");
   assertThat(cats.stream().filter(c->c.id().equals(unused)).findFirst().orElseThrow().productLines()).isEmpty();
   status.setRollbackOnly();return null;
  });
 }
 @Test void wrongLineAndCrossTenantCategoriesCannotBeAssignedToProducts(){
  var c=tx.execute(s->catalog.category(scope,actor,null,new CategoryWrite("ONLYWINE","酒水","Wine",true,0,null,key(),List.of("wine_storage"))));
  assertThatThrownBy(()->tx.execute(s->catalog.save(scope,actor,null,write(c.id(),null)))).hasMessageContaining("产品线");
  assertThatThrownBy(()->tx.execute(s->catalog.save(scope,actor,null,write(UUID.randomUUID(),null)))).isInstanceOf(com.rpb.reservation.catalog.domain.CatalogException.class);
  assertThatThrownBy(()->catalog.category(scope,actor,null,new CategoryWrite("EMPTY","酒水","Wine",true,0,null,key(),List.of()))).isInstanceOf(com.rpb.reservation.catalog.domain.CatalogException.class);
 }
 @Test void movingSharedProductMustPreserveOtherStoreChannels(){
  var other=UUID.randomUUID();jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'other','Other','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",other,tenant,issuer);
  jdbc.update("insert into catalog_product_stores(tenant_id,product_id,store_id,price_cents,channels) values(?,?,?,500,array['storage'])",tenant,product.id(),other);
  var cat=tx.execute(s->catalog.category(scope,actor,null,new CategoryWrite("ONLYCARDS","集卡","Cards",true,0,null,key(),List.of("wallet"))));
  assertThatThrownBy(()->tx.execute(s->catalog.save(scope,actor,product.id(),write(cat.id(),product.version())))).hasMessageContaining("产品线");
  assertThat(catalog.products(scope,actor).stream().filter(p->p.id().equals(product.id())).findFirst().orElseThrow().categoryId()).isEqualTo(product.categoryId());
 }
 @Test void removingAProductLineSerializesWithANewProduct() throws Exception {
  var started=new java.util.concurrent.CountDownLatch(1);
  var result=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Boolean>>();
  tx.execute(status->{
   var repo=new com.rpb.reservation.catalog.persistence.CatalogRepository(jdbc);repo.lockCategory(scope,product.categoryId());
   result.set(java.util.concurrent.CompletableFuture.supplyAsync(()->tx.execute(worker->{
    jdbc.execute("set local lock_timeout='5s'");started.countDown();
    try{repo.requireCategory(scope,product.categoryId(),List.of("storage"));return true;}catch(com.rpb.reservation.catalog.domain.CatalogException e){assertThat(e.code()).isEqualTo("CATALOG_INVALID");return false;}
   })));
   try{assertThat(started.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new RuntimeException(e);}
   jdbc.update("update catalog_categories set product_lines=array['wallet'] where tenant_id=? and id=?",tenant,product.categoryId());return null;
  });
  assertThat(result.get().get(10,java.util.concurrent.TimeUnit.SECONDS)).isFalse();
 }
 @Test void storageCategoriesAndProductsExcludeOtherLinesBeforePaging(){
  jdbc.update("update catalog_product_stores set channels=array['storage'] where tenant_id=? and product_id=?",tenant,product.id());
  jdbc.update("update catalog_categories set product_lines=array['wallet'] where id=?",product.categoryId());
  var storage=new StorageProductCatalog(new com.rpb.reservation.catalog.persistence.CatalogRepository(jdbc),products,jdbc);
  assertThat(storage.categories(scope)).isEmpty();
  assertThat(storage.options(scope,"",null,0,6,"").items()).isEmpty();
  assertThatThrownBy(()->storage.associate(scope,null,product.categoryId(),"不应建立",null,actor.actorId())).isInstanceOf(com.rpb.reservation.catalog.domain.CatalogException.class);
 }
 @Test void categoryLinesAreValidatedAndCannotRemoveUsedChannels(){
  var c=catalog.categories(scope,actor).getFirst();
  assertThatThrownBy(()->tx.execute(s->catalog.category(scope,actor,c.id(),new CategoryWrite(c.code(),"饮品","Drinks",true,0,c.version(),key(),List.of("wine_storage"))))).hasMessageContaining("使用");
  assertThatThrownBy(()->catalog.category(scope,actor,null,new CategoryWrite("BAD","错误","",true,0,null,key(),List.of("unknown")))).isInstanceOf(com.rpb.reservation.catalog.domain.CatalogException.class);
  var saved=tx.execute(s->catalog.category(scope,actor,null,new CategoryWrite("WINE","酒水","Wine",true,0,null,key(),List.of("wine_storage"))));
  assertThat(saved.productLines()).containsExactly("wine_storage");
 }
 @Test void staffOptionsHaveStableBoundedPagesAndLiteralFiltering(){
  jdbc.update("insert into catalog_products(id,tenant_id,category_id,code,name_zh,name_en,kind,unit,status) select gen_random_uuid(),?,?,'QA'||n,'酒品'||n,'Wine','physical','瓶','active' from generate_series(1,15)n",tenant,product.categoryId());
  jdbc.update("insert into catalog_product_stores(tenant_id,product_id,store_id,channels) select tenant_id,id,?,array['storage'] from catalog_products where tenant_id=? and code like 'QA%'",store,tenant);
  var storage=new StorageProductCatalog(new com.rpb.reservation.catalog.persistence.CatalogRepository(jdbc),products,jdbc);
  var a=storage.options(scope,"Wine",null,0,6,"");var b=storage.options(scope,"Wine",null,1,6,"");var c=storage.options(scope,"Wine",null,2,6,"");
  assertThat(a.items()).hasSize(6);assertThat(b.items()).hasSize(6);assertThat(c.items()).hasSize(3);assertThat(a.hasMore()).isTrue();assertThat(c.hasMore()).isFalse();
  var ids=new HashSet<UUID>();for(var page:List.of(a,b,c))for(var p:page.items())assertThat(ids.add(p.id())).isTrue();
  assertThat(storage.options(scope,"%_",null,0,6,"").items()).isEmpty();
  assertThat(storage.options(new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(UUID.randomUUID()),store),"",null,0,6,"").items()).isEmpty();
  assertThatThrownBy(()->storage.options(scope,"",null,-1,6,"")).isInstanceOf(com.rpb.reservation.catalog.domain.CatalogException.class);
 }
}
