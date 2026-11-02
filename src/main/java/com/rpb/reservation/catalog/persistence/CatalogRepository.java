package com.rpb.reservation.catalog.persistence;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.catalog.application.CatalogDtos.*;
import com.rpb.reservation.catalog.domain.CatalogException;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class CatalogRepository {
 public ProductPage storageProducts(StoreScope scope,String query,int page,int size){
  String where=" where p.tenant_id=? and 'storage'=any(s.channels) and strpos(lower(concat_ws(' ',p.code,p.name_zh,p.name_en)),lower(?))>0";
  Object[] args={scope.storeId().value(),scope.tenantId().value(),query};
  long total=jdbc.queryForObject("select count(*) from ("+select()+where+") storage_products",Long.class,args);
  var items=jdbc.query(select()+where+" order by p.code,p.id limit ? offset ?",this::map,scope.storeId().value(),scope.tenantId().value(),query,size,(long)page*size);
  return new ProductPage(items,total,page,size,((long)page+1)*size<total);
 }
 private final JdbcTemplate jdbc;
 public CatalogRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Category> categories(UUID tenant){return jdbc.query("select * from catalog_categories where tenant_id=? order by sort_order,code",(r,n)->new Category(r.getObject("id",UUID.class),r.getString("code"),r.getString("name_zh"),r.getString("name_en"),r.getBoolean("enabled"),r.getInt("sort_order"),r.getInt("version"),List.of((String[])r.getArray("product_lines").getArray())),tenant);}
 public UUID category(StoreScope s,CategoryWrite c){
  UUID id=UUID.randomUUID();jdbc.update("insert into catalog_categories(id,tenant_id,code,name_zh,name_en,enabled,sort_order,product_lines) values(?,?,?,?,?,?,?,?::text[])",id,s.tenantId().value(),c.code(),text(c.nameZh()),text(c.nameEn()),c.enabled(),c.sortOrder(),array(c.productLines()));return id;
 }
 public void categoryUpdate(StoreScope s,UUID id,CategoryWrite c){
  lockCategory(s,id);
  if(c.productLines()!=null){var used=jdbc.queryForList("select distinct unnest(l.channels) from catalog_products p join catalog_product_stores l on l.tenant_id=p.tenant_id and l.product_id=p.id where p.tenant_id=? and p.category_id=?",String.class,s.tenantId().value(),id);
   if(!com.rpb.reservation.catalog.application.CatalogProductLines.covers(c.productLines(),used))throw new CatalogException("CATALOG_CATEGORY_IN_USE",409,"该分类仍有商品使用此产品线，请先调整商品渠道 / Category product line is still in use");}
  if(jdbc.update("update catalog_categories set name_zh=?,name_en=?,enabled=?,sort_order=?,product_lines=coalesce(?::text[],product_lines),version=version+1,updated_at=now() where tenant_id=? and id=? and version=?",text(c.nameZh()),text(c.nameEn()),c.enabled(),c.sortOrder(),c.productLines()==null?null:array(c.productLines()),s.tenantId().value(),id,c.expectedVersion())!=1)throw CatalogException.stale();}
 public List<Product> list(StoreScope s){return jdbc.query(select()+" where p.tenant_id=? order by p.code limit 500",this::map,s.storeId().value(),s.tenantId().value());}
 public Product get(StoreScope s,UUID id){return jdbc.query(select()+" where p.tenant_id=? and p.id=?",this::map,s.storeId().value(),s.tenantId().value(),id).stream().findFirst().orElseThrow(CatalogException::missing);}
 public Product lock(StoreScope s,UUID id){jdbc.queryForList("select id from catalog_products where tenant_id=? and id=? for update",s.tenantId().value(),id);return get(s,id);}
 public UUID create(StoreScope s,ProductWrite c){requireCategory(s,c.categoryId(),c.channels());var codes=new CatalogProductCodes(jdbc);codes.lock(s);String code=c.code()==null||c.code().isBlank()?codes.next(s):c.code();UUID id=UUID.randomUUID();jdbc.update("insert into catalog_products(id,tenant_id,category_id,code,name_zh,name_en,description_zh,description_en,kind,unit,image_url,base_price_cents,tax_mode,tax_category,tax_rate) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,s.tenantId().value(),c.categoryId(),code,text(c.nameZh()),text(c.nameEn()),text(c.descriptionZh()),text(c.descriptionEn()),c.kind(),c.unit(),text(c.imageUrl()),c.priceCents(),c.taxMode(),c.taxCategory(),c.taxRate());listing(s,id,c);return id;}
 public void update(StoreScope s,UUID id,ProductWrite c){lock(s,id);var channels=new HashSet<>(c.channels());channels.addAll(jdbc.queryForList("select distinct unnest(channels) from catalog_product_stores where tenant_id=? and product_id=? and store_id<>?",String.class,s.tenantId().value(),id,s.storeId().value()));requireCategory(s,c.categoryId(),List.copyOf(channels));if(jdbc.update("update catalog_products set base_price_cents=?,category_id=?,name_zh=?,name_en=?,description_zh=?,description_en=?,kind=?,unit=?,image_url=?,tax_mode=?,tax_category=?,tax_rate=?,version=version+1,updated_at=now() where tenant_id=? and id=? and version=? and status<>'archived'",c.priceCents(),c.categoryId(),text(c.nameZh()),text(c.nameEn()),text(c.descriptionZh()),text(c.descriptionEn()),c.kind(),c.unit(),text(c.imageUrl()),c.taxMode(),c.taxCategory(),c.taxRate(),s.tenantId().value(),id,c.expectedVersion())!=1)throw CatalogException.stale();listing(s,id,c);}
 private void listing(StoreScope s,UUID id,ProductWrite c){jdbc.update("insert into catalog_product_stores(tenant_id,product_id,store_id,price_cents,channels) values(?,?,?,?,?::text[]) on conflict(tenant_id,product_id,store_id) do update set price_cents=excluded.price_cents,channels=excluded.channels,version=catalog_product_stores.version+1,updated_at=now()",s.tenantId().value(),id,s.storeId().value(),c.priceCents(),"{"+String.join(",",c.channels())+"}");}
 public void listingUpdate(StoreScope s,UUID id,Listing c){requireCategory(s,lock(s,id).categoryId(),c.channels());int count=jdbc.update("insert into catalog_product_stores(tenant_id,product_id,store_id,price_cents,channels,enabled,version) values(?,?,?,?,?::text[],?,1) on conflict(tenant_id,product_id,store_id) do update set price_cents=excluded.price_cents,channels=excluded.channels,enabled=excluded.enabled,version=catalog_product_stores.version+1,updated_at=now() where catalog_product_stores.version=?",s.tenantId().value(),id,s.storeId().value(),c.priceCents(),"{"+String.join(",",c.channels())+"}",c.enabled(),c.expectedVersion());if(count!=1)throw CatalogException.stale();}
 public void status(StoreScope s,UUID id,Status c){if(jdbc.update("update catalog_products set status=?,version=version+1,updated_at=now() where tenant_id=? and id=? and version=? and status<>'archived'",c.status(),s.tenantId().value(),id,c.expectedVersion())!=1)throw CatalogException.stale();}
 private String select(){return "select p.*,coalesce(s.price_cents,p.base_price_cents) price_cents,coalesce(s.channels,'{}'::text[]) channels,coalesce(s.enabled,false) listing_enabled,coalesce(s.version,0) listing_version from catalog_products p left join catalog_product_stores s on s.product_id=p.id and s.tenant_id=p.tenant_id and s.store_id=?";}
 private Product map(ResultSet r,int n)throws SQLException{return new Product(r.getObject("id",UUID.class),r.getObject("category_id",UUID.class),r.getString("code"),r.getString("name_zh"),r.getString("name_en"),r.getString("description_zh"),r.getString("description_en"),r.getString("kind"),r.getString("unit"),r.getString("image_url"),r.getString("status"),r.getInt("version"),r.getObject("price_cents",Long.class),r.getString("currency"),r.getString("tax_mode"),r.getString("tax_category"),r.getBigDecimal("tax_rate"),List.of((String[])r.getArray("channels").getArray()),r.getBoolean("listing_enabled"),r.getInt("listing_version"));}
 public void lockCategory(StoreScope s,UUID category){if(jdbc.queryForList("select id from catalog_categories where tenant_id=? and id=? for update",UUID.class,s.tenantId().value(),category).isEmpty())throw CatalogException.missing();}
 public void requireCategory(StoreScope s,UUID category,List<String> channels){lockCategory(s,category);var c=categories(s.tenantId().value()).stream().filter(x->x.id().equals(category)).findFirst().orElseThrow(CatalogException::missing);if(!com.rpb.reservation.catalog.application.CatalogProductLines.covers(c.productLines(),channels))throw new CatalogException("CATALOG_INVALID",400,"所选分类不适用于商品产品线 / Category does not support the selected product lines");}
 private static String array(List<String> values){return "{"+String.join(",",values)+"}";}
 private static String text(String s){return s==null?"":s.trim();}
}
