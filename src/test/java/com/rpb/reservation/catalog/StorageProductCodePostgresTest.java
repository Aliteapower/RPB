package com.rpb.reservation.catalog;
import com.rpb.reservation.catalog.application.*;
import com.rpb.reservation.catalog.application.CatalogDtos.*;
import com.rpb.reservation.catalog.persistence.CatalogRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageProductCodePostgresTest extends CatalogPostgresTest {
 @Test void atomicEditorCreatesWithBlankCodeAndReplaysItsAssignedCode(){
  var repo=new CatalogRepository(jdbc);var commands=new com.rpb.reservation.catalog.persistence.CatalogCommands(jdbc,json);var access=org.mockito.Mockito.mock(com.rpb.reservation.catalog.access.CatalogAccess.class);
  var barcodes=new CatalogBarcodeService(access,repo,commands,jdbc);var editor=new CatalogProductEditService(access,repo,commands,catalog,barcodes);
  var command=new CatalogProductEditService.Write(wine(null),List.of(),List.of(),0,key());var first=tx.execute(s->editor.save(scope,actor,null,command));
  assertThat(first.product().code()).matches("[0-9]{1,5}");var replay=tx.execute(s->editor.save(scope,actor,null,command));assertThat(replay).isEqualTo(first);
 }
 @Test void shortCodeLookupIsTenantScopedAndExhaustionAllowsCustomCodes(){
  jdbc.update("insert into catalog_products(id,tenant_id,category_id,code,name_zh,name_en,kind,unit,status) select gen_random_uuid(),?,?,n::text,'酒品','','physical','瓶','active' from generate_series(1,99999) n",tenant,product.categoryId());
  assertThatThrownBy(()->tx.execute(s->catalog.save(scope,actor,null,wine("")))).isInstanceOfSatisfying(com.rpb.reservation.catalog.domain.CatalogException.class,e->{assertThat(e.code()).isEqualTo("CATALOG_CODE_EXHAUSTED");assertThat(e.status()).isEqualTo(409);});
  var codes=new com.rpb.reservation.catalog.persistence.CatalogProductCodes(jdbc);
  String other=tx.execute(s->codes.next(new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(UUID.randomUUID()),store)));assertThat(other).isEqualTo("1");
  var custom=tx.execute(s->catalog.save(scope,actor,null,wine("CUSTOM-FULL")));assertThat(custom.code()).isEqualTo("CUSTOM-FULL");
 }
 private ProductWrite wine(String code){return new ProductWrite(code,product.categoryId(),"新酒","","","","physical","瓶","",(Long)null,null,null,null,List.of("storage"),null,key());}
 @Test void automaticCodesAreShortSkipExistingAndReplayWithoutAllocatingAgain(){
  tx.execute(s->catalog.save(scope,actor,null,wine("1")));
  var command=wine("");var first=tx.execute(s->catalog.save(scope,actor,null,command));
  assertThat(first.code()).matches("[0-9]{1,5}").isNotEqualTo("1");
  Product replay=tx.execute(s->catalog.save(scope,actor,null,command));assertThat(replay).isEqualTo(first);
  var manual=tx.execute(s->catalog.save(scope,actor,null,wine("MY-WINE")));assertThat(manual.code()).isEqualTo("MY-WINE");
  assertThatThrownBy(()->tx.execute(s->catalog.save(scope,actor,null,wine("MY-WINE")))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void concurrentAutomaticCreatesAndTrustedStorageUseSameNamespace() throws Exception {
  var pool=Executors.newFixedThreadPool(2);try{
   var jobs=pool.invokeAll(List.of(()->tx.execute(s->catalog.save(scope,actor,null,wine(""))),()->tx.execute(s->catalog.save(scope,actor,null,wine("")))));
   var one=(Product)jobs.get(0).get(20,TimeUnit.SECONDS);var two=(Product)jobs.get(1).get(20,TimeUnit.SECONDS);
   assertThat(one.code()).matches("[0-9]{1,5}").isNotEqualTo(two.code());
   var storage=new StorageProductCatalog(new CatalogRepository(jdbc),products,jdbc);
   var associated=tx.execute(s->storage.associate(scope,null,product.categoryId(),"收存新酒","",actor.actorId()));
   assertThat(associated.code()).matches("[0-9]{1,5}").isNotIn(one.code(),two.code());
  }finally{pool.shutdownNow();}
 }
}
