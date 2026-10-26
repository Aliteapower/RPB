package com.rpb.reservation.catalog;
import com.rpb.reservation.catalog.access.CatalogAccess;
import com.rpb.reservation.catalog.application.*;
import com.rpb.reservation.catalog.persistence.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
public class CatalogBarcodePostgresTest extends CatalogPostgresTest {
 private CatalogBarcodeService service(CatalogAccess a){return new CatalogBarcodeService(a,new CatalogRepository(jdbc),new CatalogCommands(jdbc,json),jdbc);}
 private StorageProductCatalog options(){return new StorageProductCatalog(new CatalogRepository(jdbc),products,jdbc);}
 @Test void aliasesReplayConflictAndRemoval(){
  var service=service(mock(CatalogAccess.class));var command=new CatalogBarcodeService.Write("036000291452",false,key());
  var saved=tx.execute(s->service.save(scope,actor,product.id(),command));assertThat(saved.barcodes()).containsExactly("036000291452");
  CatalogBarcodeService.Barcodes replay=tx.execute(s->service.save(scope,actor,product.id(),command));assertThat(replay).isEqualTo(saved);
  var second=tx.execute(s->new StorageProductCatalog(new CatalogRepository(jdbc),products,jdbc).associate(scope,null,product.categoryId(),"啤酒","Beer",actor.actorId()));
  assertThatThrownBy(()->tx.execute(s->service.save(scope,actor,second.id(),new CatalogBarcodeService.Write("0036000291452",false,key())))).hasMessageContaining("其他商品");
  assertThatThrownBy(()->service.list(new StoreScope(new TenantId(UUID.randomUUID()),store),actor,product.id())).hasMessageContaining("不存在");
  assertThat(tx.execute(s->service.save(scope,actor,product.id(),new CatalogBarcodeService.Write(command.barcode(),true,key()))).barcodes()).isEmpty();
 }
 @Test void barcodeAndPagingRespectStoreLifecycleAndCategory(){
  var service=service(mock(CatalogAccess.class));tx.execute(s->service.save(scope,actor,product.id(),new CatalogBarcodeService.Write("6901035601030",false,key())));
  assertThat(options().options(scope,"",null,0,6,"6901035601030").items()).isEmpty();
  jdbc.update("update catalog_product_stores set channels=array['storage'] where product_id=?",product.id());
  assertThat(options().options(scope,"",null,0,6,"6901035601030").items()).hasSize(1);
  assertThat(options().options(scope,"",UUID.randomUUID(),0,6,"").items()).isEmpty();
  var second=tx.execute(s->options().associate(scope,null,product.categoryId(),"啤酒","Beer",actor.actorId()));
  var first=options().options(scope,"",null,0,1,"");assertThat(first.items()).hasSize(1);assertThat(first.hasMore()).isTrue();
  assertThat(options().options(scope,"",null,1,1,"").items().getFirst().id()).isNotEqualTo(first.items().getFirst().id());
  jdbc.update("update catalog_products set status='inactive' where id=?",product.id());assertThat(options().options(scope,"",null,0,6,"6901035601030").items()).isEmpty();
  assertThat(options().options(new StoreScope(new TenantId(UUID.randomUUID()),store),"",null,0,6,"").items()).isEmpty();
  assertThatThrownBy(()->options().options(scope,"",null,0,13,"")).isInstanceOf(RuntimeException.class);
 }
 @Test void invalidCheckDigitsAndPermissions(){
  assertThat(ProductBarcode.normalize("0036000291452")).isEqualTo(ProductBarcode.normalize("036000291452"));
  assertThatThrownBy(()->ProductBarcode.normalize("036000291453")).isInstanceOf(RuntimeException.class);
  var access=mock(CatalogAccess.class);doThrow(new IllegalStateException("denied")).when(access).requireGlobal(scope,actor,"catalog.manage");
  assertThatThrownBy(()->service(access).save(scope,actor,product.id(),new CatalogBarcodeService.Write("036000291452",false,key()))).hasMessage("denied");
 }
 @Test void replacementIsAtomicAndPreservesOldCodeOnConflict(){
  var service=service(mock(CatalogAccess.class));String old="036000291452",next="6901035601030";
  tx.execute(s->service.save(scope,actor,product.id(),new CatalogBarcodeService.Write(old,false,key())));
  var change=new CatalogBarcodeService.Write(next,false,key(),old);
  var result=tx.execute(s->service.save(scope,actor,product.id(),change));assertThat(result.barcodes()).containsExactly(next);
  CatalogBarcodeService.Barcodes replay=tx.execute(s->service.save(scope,actor,product.id(),change));assertThat(replay).isEqualTo(result);
  var second=tx.execute(s->options().associate(scope,null,product.categoryId(),"啤酒","Beer",actor.actorId()));
  tx.execute(s->service.save(scope,actor,second.id(),new CatalogBarcodeService.Write(old,false,key())));
  assertThatThrownBy(()->tx.execute(s->service.save(scope,actor,product.id(),new CatalogBarcodeService.Write(old,false,key(),next)))).hasMessageContaining("其他商品");
  assertThat(service.list(scope,actor,product.id()).barcodes()).containsExactly(next);
  assertThatThrownBy(()->tx.execute(s->service.save(scope,actor,product.id(),new CatalogBarcodeService.Write("4006381333931",false,key(),old)))).hasMessageContaining("变化");
  assertThat(service.list(scope,actor,product.id()).barcodes()).containsExactly(next);
  assertThatThrownBy(()->tx.execute(s->service.save(scope,actor,product.id(),new CatalogBarcodeService.Write(next,true,key(),next)))).hasMessageContaining("格式");
 }
 @Test void omittedReplacementPreservesLegacyRequestHash()throws Exception{
  var c=new CatalogBarcodeService.Write("036000291452",false,"legacy-key");
  assertThat(json.readTree(json.writeValueAsString(c))).isEqualTo(json.readTree("{\"barcode\":\"036000291452\",\"remove\":false,\"idempotencyKey\":\"legacy-key\"}"));
 }
}
