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
 public record Write(String barcode,boolean remove,String idempotencyKey,
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String replaceBarcode){
  public Write(String barcode,boolean remove,String idempotencyKey){this(barcode,remove,idempotencyKey,null);}
 }
 private final CatalogAccess access;private final CatalogRepository products;private final CatalogCommands commands;private final JdbcTemplate jdbc;
 public CatalogBarcodeService(CatalogAccess a,CatalogRepository p,CatalogCommands c,JdbcTemplate j){access=a;products=p;commands=c;jdbc=j;}
 public Barcodes list(StoreScope s,CurrentActor a,UUID id){access.require(s,a,"catalog.view");products.get(s,id);return read(s,id);}
 public List<String> values(StoreScope s,UUID id){return jdbc.queryForList("select coalesce(raw_barcode,barcode) from catalog_product_barcodes where tenant_id=? and product_id=? order by barcode",String.class,s.tenantId().value(),id);}
 private static CatalogException singleBarcode(){return new CatalogException("CATALOG_SINGLE_BARCODE",409,"每个商品只能保留一个条码 / Only one barcode per product");}
 private Barcodes read(StoreScope s,UUID id){return new Barcodes(id,values(s,id));}
 @Transactional public Barcodes save(StoreScope s,CurrentActor a,UUID id,Write c){
  access.requireGlobal(s,a,"catalog.manage");if(c==null)throw CatalogException.invalid();String code=ProductBarcode.normalize(c.barcode());String old=c.replaceBarcode()==null?null:ProductBarcode.normalize(c.replaceBarcode());if(c.remove()&&old!=null)throw CatalogException.invalid();
  return commands.run(s,a.actorId(),"product.barcode",c.idempotencyKey(),Map.of("productId",id,"body",c),Barcodes.class,()->{
   var p=products.lock(s,id);if(!p.kind().equals("physical")||p.status().equals("archived"))throw CatalogException.invalid();
   var locks=new TreeSet<String>();locks.add(code);if(old!=null)locks.add(old);
   for(String value:locks)jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","barcode:"+s.tenantId().value()+value);
   if(old!=null&&jdbc.queryForObject("select count(*) from catalog_product_barcodes where tenant_id=? and product_id=? and barcode=?",Integer.class,s.tenantId().value(),id,old)!=1)throw CatalogException.stale();
   if(c.remove())jdbc.update("delete from catalog_product_barcodes where tenant_id=? and product_id=? and barcode=?",s.tenantId().value(),id,code);
   else {var owners=jdbc.queryForList("select product_id from catalog_product_barcodes where tenant_id=? and barcode=?",UUID.class,s.tenantId().value(),code);
    if(!owners.isEmpty()&&!owners.getFirst().equals(id))throw new CatalogException("CATALOG_BARCODE_CONFLICT",409,"该条码已关联其他商品 / Barcode belongs to another product");
    var existing=jdbc.queryForList("select barcode from catalog_product_barcodes where tenant_id=? and product_id=?",String.class,s.tenantId().value(),id);
    if(existing.stream().anyMatch(value->!value.equals(code)&&!value.equals(old)))throw singleBarcode();
    jdbc.update("insert into catalog_product_barcodes(tenant_id,barcode,product_id,created_by,raw_barcode) values(?,?,?,?,?) on conflict(tenant_id,barcode) do update set raw_barcode=excluded.raw_barcode where catalog_product_barcodes.product_id=excluded.product_id",s.tenantId().value(),code,id,a.actorId(),c.barcode().trim());
    if(old!=null&&!old.equals(code))jdbc.update("delete from catalog_product_barcodes where tenant_id=? and product_id=? and barcode=?",s.tenantId().value(),id,old);
   }
   jdbc.update("insert into catalog_audit(tenant_id,store_id,actor_id,action,source_id) values(?,?,?,?,?)",s.tenantId().value(),s.storeId().value(),a.actorId(),c.remove()?"product.barcode.remove":old!=null?"product.barcode.replace":"product.barcode.add",id);
   return read(s,id);
  });
 }
 @Transactional public void replace(StoreScope s,CurrentActor a,UUID id,List<String> expected,List<String> desired){
  access.requireGlobal(s,a,"catalog.manage");if(expected==null||desired==null||expected.size()>50)throw CatalogException.invalid();
  if(desired.size()>1)throw singleBarcode();
  var p=products.lock(s,id);if(!desired.isEmpty()&&!p.kind().equals("physical"))throw CatalogException.invalid();
  expected.forEach(ProductBarcode::normalize);
  var actual=values(s,id);if(!actual.stream().sorted().toList().equals(expected.stream().sorted().toList()))throw CatalogException.stale();
  var wanted=new TreeMap<String,String>();for(String value:desired){String key=ProductBarcode.normalize(value);if(wanted.put(key,value.trim())!=null)throw CatalogException.invalid();}
  var locks=new TreeSet<String>(wanted.keySet());actual.forEach(v->locks.add(ProductBarcode.normalize(v)));
  for(String key:locks)jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","barcode:"+s.tenantId().value()+key);
  for(String key:wanted.keySet()){var owners=jdbc.queryForList("select product_id from catalog_product_barcodes where tenant_id=? and barcode=?",UUID.class,s.tenantId().value(),key);if(!owners.isEmpty()&&!owners.getFirst().equals(id))throw new CatalogException("CATALOG_BARCODE_CONFLICT",409,"该条码已关联其他商品 / Barcode belongs to another product");}
  jdbc.update("delete from catalog_product_barcodes where tenant_id=? and product_id=? and not(barcode=any(?::text[]))",s.tenantId().value(),id,"{"+String.join(",",wanted.keySet())+"}");
  wanted.forEach((key,raw)->jdbc.update("insert into catalog_product_barcodes(tenant_id,barcode,product_id,created_by,raw_barcode) values(?,?,?,?,?) on conflict(tenant_id,barcode) do update set raw_barcode=excluded.raw_barcode where catalog_product_barcodes.product_id=excluded.product_id",s.tenantId().value(),key,id,a.actorId(),raw));
  if(!actual.stream().sorted().toList().equals(desired.stream().map(String::trim).sorted().toList()))jdbc.update("insert into catalog_audit(tenant_id,store_id,actor_id,action,source_id) values(?,?,?,'product.barcodes.edit',?)",s.tenantId().value(),s.storeId().value(),a.actorId(),id);
 }
}
