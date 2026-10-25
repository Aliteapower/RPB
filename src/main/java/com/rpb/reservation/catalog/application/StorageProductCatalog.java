package com.rpb.reservation.catalog.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.catalog.persistence.CatalogRepository;
import com.rpb.reservation.catalog.application.CatalogDtos.Product;
import com.rpb.reservation.catalog.domain.CatalogException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Trusted storage integration. Storage owns authorization; catalog owns product writes. */
@Service
@Transactional(readOnly=true)
public class StorageProductCatalog {
 private final CatalogRepository r;private final ProductCatalogQuery query;private final JdbcTemplate jdbc;
 public StorageProductCatalog(CatalogRepository r,ProductCatalogQuery query,JdbcTemplate jdbc){this.r=r;this.query=query;this.jdbc=jdbc;}
 public List<CatalogDtos.Category> categories(StoreScope s){return r.categories(s.tenantId().value());}
 public List<Product> search(StoreScope s,String value,boolean review){
  String q=value==null?"":value.trim().toLowerCase(Locale.ROOT);if(q.length()>120)throw CatalogException.invalid();
  // SQL filters before the bound; existing catalog list's global 500-row cap must not hide matches.
  var ids=jdbc.queryForList("select p.id from catalog_products p join catalog_categories c on c.id=p.category_id and c.tenant_id=p.tenant_id left join catalog_product_stores l on l.product_id=p.id and l.tenant_id=p.tenant_id and l.store_id=? where p.tenant_id=? and (strpos(lower(p.name_zh),?)>0 or strpos(lower(p.name_en),?)>0 or strpos(lower(p.code),?)>0)"+(review?"":" and p.kind='physical' and p.status='active' and c.enabled and l.enabled and 'storage'=any(l.channels)")+" order by p.code limit 100",UUID.class,s.storeId().value(),s.tenantId().value(),q,q,q);
  return ids.stream().map(id->r.get(s,id)).toList();
 }
 public record Options(List<Product> items,boolean hasMore,int page,List<CatalogDtos.Category> categories){}
 public Options options(StoreScope s,String value,UUID category,int page,int size,String barcode){
  String q=value==null?"":value.trim().toLowerCase(Locale.ROOT);if(q.length()>120||page<0||page>100000||size<1||size>12)throw CatalogException.invalid();
  var args=new ArrayList<Object>();args.add(s.storeId().value());args.add(s.tenantId().value());
  String sql="select p.id from catalog_products p join catalog_categories c on c.id=p.category_id and c.tenant_id=p.tenant_id join catalog_product_stores l on l.product_id=p.id and l.tenant_id=p.tenant_id and l.store_id=? where p.tenant_id=? and p.kind='physical' and p.status='active' and c.enabled and l.enabled and 'storage'=any(l.channels)";
  if(barcode!=null&&!barcode.isBlank()){sql+=" and exists(select 1 from catalog_product_barcodes b where b.tenant_id=p.tenant_id and b.product_id=p.id and b.barcode=?)";args.add(ProductBarcode.normalize(barcode));}
  else {sql+=" and (strpos(lower(p.name_zh),?)>0 or strpos(lower(p.name_en),?)>0 or strpos(lower(p.code),?)>0)";args.add(q);args.add(q);args.add(q);}
  if(category!=null){sql+=" and p.category_id=?";args.add(category);}
  args.add(size+1);args.add(page*size);
  var ids=jdbc.queryForList(sql+" order by c.sort_order,p.code,p.id limit ? offset ?",UUID.class,args.toArray());
  var cats=categories(s).stream().filter(CatalogDtos.Category::enabled).toList();
  return new Options(ids.stream().limit(size).map(id->r.get(s,id)).toList(),ids.size()>size,page,cats);
 }
 public Product selectable(StoreScope s,UUID id){return query.requireUsable(s,id,"storage");}
 @Transactional
 public Product associate(StoreScope s,UUID id,UUID category,String zh,String en,UUID actor){
  if(id!=null){var p=r.get(s,id);if(!p.kind().equals("physical"))throw CatalogException.invalid();return p;}
  if(category==null||categories(s).stream().noneMatch(c->c.id().equals(category)&&c.enabled())||(zh==null||zh.isBlank())&&(en==null||en.isBlank()))throw CatalogException.invalid();
  UUID product=UUID.randomUUID();String code="WS-"+product;
  jdbc.update("insert into catalog_products(id,tenant_id,category_id,code,name_zh,name_en,kind,unit,status,base_price_cents,tax_mode,tax_category,tax_rate) values(?,?,?,?,?,?,'physical','瓶','active',null,null,null,null)",product,s.tenantId().value(),category,code,text(zh),text(en));
  jdbc.update("insert into catalog_product_stores(tenant_id,store_id,product_id,price_cents,channels) values(?,?,?,null,array['storage'])",s.tenantId().value(),s.storeId().value(),product);
  jdbc.update("insert into catalog_audit(tenant_id,store_id,actor_id,action,source_id) values(?,?,?,'storage.product.create',?)",s.tenantId().value(),s.storeId().value(),actor,product);
  return r.get(s,product);
 }
 private String text(String v){return v==null?"":v.trim();}
}
