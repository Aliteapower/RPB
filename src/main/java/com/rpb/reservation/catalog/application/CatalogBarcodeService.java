package com.rpb.reservation.catalog.application;
import com.rpb.reservation.catalog.access.CatalogAccess;
import com.rpb.reservation.catalog.domain.CatalogException;
import com.rpb.reservation.catalog.persistence.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
public class CatalogBarcodeService {
 public record Barcodes(UUID productId,List<String> barcodes){}
 public record Write(String barcode,boolean remove,String idempotencyKey){}
 private final CatalogAccess access;private final CatalogRepository products;private final CatalogCommands commands;private final JdbcTemplate jdbc;
 public CatalogBarcodeService(CatalogAccess a,CatalogRepository p,CatalogCommands c,JdbcTemplate j){access=a;products=p;commands=c;jdbc=j;}
 public Barcodes list(StoreScope s,CurrentActor a,UUID id){access.require(s,a,"catalog.view");products.get(s,id);return read(s,id);}
 private Barcodes read(StoreScope s,UUID id){return new Barcodes(id,jdbc.queryForList("select barcode from catalog_product_barcodes where tenant_id=? and product_id=? order by barcode",String.class,s.tenantId().value(),id));}
 @Transactional public Barcodes save(StoreScope s,CurrentActor a,UUID id,Write c){
  access.requireGlobal(s,a,"catalog.manage");if(c==null)throw CatalogException.invalid();String code=ProductBarcode.normalize(c.barcode());
  return commands.run(s,a.actorId(),"product.barcode",c.idempotencyKey(),Map.of("productId",id,"body",c),Barcodes.class,()->{
   var p=products.lock(s,id);if(!p.kind().equals("physical")||p.status().equals("archived"))throw CatalogException.invalid();
   jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","barcode:"+s.tenantId().value()+code);
   if(c.remove())jdbc.update("delete from catalog_product_barcodes where tenant_id=? and product_id=? and barcode=?",s.tenantId().value(),id,code);
   else {var owners=jdbc.queryForList("select product_id from catalog_product_barcodes where tenant_id=? and barcode=?",UUID.class,s.tenantId().value(),code);
    if(!owners.isEmpty()&&!owners.getFirst().equals(id))throw new CatalogException("CATALOG_BARCODE_CONFLICT",409,"该条码已关联其他商品 / Barcode belongs to another product");
    jdbc.update("insert into catalog_product_barcodes(tenant_id,barcode,product_id,created_by) values(?,?,?,?) on conflict do nothing",s.tenantId().value(),code,id,a.actorId());}
   jdbc.update("insert into catalog_audit(tenant_id,store_id,actor_id,action,source_id) values(?,?,?,?,?)",s.tenantId().value(),s.storeId().value(),a.actorId(),c.remove()?"product.barcode.remove":"product.barcode.add",id);
   return read(s,id);
  });
 }
}
