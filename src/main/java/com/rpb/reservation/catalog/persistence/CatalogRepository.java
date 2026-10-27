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
 public List<Category> categories(UUID tenant){return jdbc.query("select * from catalog_categories where tenant_id=? order by sort_order,code",(r,n)->new Category(r.getObject("id",UUID.class),r.getString("code"),r.getString("name_zh"),r.getString("name_en"),r.getBoolean("enabled"),r.getInt("sort_order"),r.getInt("version")),tenant);}
 public UUID category(StoreScope s,CategoryWrite c){
  UUID id=UUID.randomUUID();jdbc.update("insert into catalog_categories(id,tenant_id,code,name_zh,name_en,enabled,sort_order) values(?,?,?,?,?,?,?)",id,s.tenantId().value(),c.code(),text(c.nameZh()),text(c.nameEn()),c.enabled(),c.sortOrder());return id;
 }
 public void categoryUpdate(StoreScope s,UUID id,CategoryWrite c){if(jdbc.update("update catalog_categories set name_zh=?,name_en=?,enabled=?,sort_order=?,version=version+1,updated_at=now() where tenant_id=? and id=? and version=?",text(c.nameZh()),text(c.nameEn()),c.enabled(),c.sortOrder(),s.tenantId().value(),id,c.expectedVersion())!=1)throw CatalogException.stale();}
 public List<Product> list(StoreScope s){return jdbc.query(select()+" where p.tenant_id=? order by p.code limit 500",this::map,s.storeId().value(),s.tenantId().value());}
 public Product get(StoreScope s,UUID id){return jdbc.query(select()+" where p.tenant_id=? and p.id=?",this::map,s.storeId().value(),s.tenantId().value(),id).stream().findFirst().orElseThrow(CatalogException::missing);}
 public Product lock(StoreScope s,UUID id){jdbc.queryForList("select id from catalog_products where tenant_id=? and id=? for update",s.tenantId().value(),id);return get(s,id);}
 public UUID create(StoreScope s,ProductWrite c){var codes=new CatalogProductCodes(jdbc);codes.lock(s);String code=c.code()==null||c.code().isBlank()?codes.next(s):c.code();UUID id=UUID.randomUUID();jdbc.update("insert into catalog_products(id,tenant_id,category_id,code,name_zh,name_en,description_zh,description_en,kind,unit,image_url,base_price_cents,tax_mode,tax_category,tax_rate) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,s.tenantId().value(),c.categoryId(),code,text(c.nameZh()),text(c.nameEn()),text(c.descriptionZh()),text(c.descriptionEn()),c.kind(),c.unit(),text(c.imageUrl()),c.priceCents(),c.taxMode(),c.taxCategory(),c.taxRate());listing(s,id,c);return id;}
 public void update(StoreScope s,UUID id,ProductWrite c){if(jdbc.update("update catalog_products set base_price_cents=?,category_id=?,name_zh=?,name_en=?,description_zh=?,description_en=?,kind=?,unit=?,image_url=?,tax_mode=?,tax_category=?,tax_rate=?,version=version+1,updated_at=now() where tenant_id=? and id=? and version=? and status<>'archived'",c.priceCents(),c.categoryId(),text(c.nameZh()),text(c.nameEn()),text(c.descriptionZh()),text(c.descriptionEn()),c.kind(),c.unit(),text(c.imageUrl()),c.taxMode(),c.taxCategory(),c.taxRate(),s.tenantId().value(),id,c.expectedVersion())!=1)throw CatalogException.stale();listing(s,id,c);}
 private void listing(StoreScope s,UUID id,ProductWrite c){jdbc.update("insert into catalog_product_stores(tenant_id,product_id,store_id,price_cents,channels) values(?,?,?,?,?::text[]) on conflict(tenant_id,product_id,store_id) do update set price_cents=excluded.price_cents,channels=excluded.channels,version=catalog_product_stores.version+1,updated_at=now()",s.tenantId().value(),id,s.storeId().value(),c.priceCents(),"{"+String.join(",",c.channels())+"}");}
 public void listingUpdate(StoreScope s,UUID id,Listing c){int count=jdbc.update("insert into catalog_product_stores(tenant_id,product_id,store_id,price_cents,channels,enabled,version) values(?,?,?,?,?::text[],?,1) on conflict(tenant_id,product_id,store_id) do update set price_cents=excluded.price_cents,channels=excluded.channels,enabled=excluded.enabled,version=catalog_product_stores.version+1,updated_at=now() where catalog_product_stores.version=?",s.tenantId().value(),id,s.storeId().value(),c.priceCents(),"{"+String.join(",",c.channels())+"}",c.enabled(),c.expectedVersion());if(count!=1)throw CatalogException.stale();}
 public void status(StoreScope s,UUID id,Status c){if(jdbc.update("update catalog_products set status=?,version=version+1,updated_at=now() where tenant_id=? and id=? and version=? and status<>'archived'",c.status(),s.tenantId().value(),id,c.expectedVersion())!=1)throw CatalogException.stale();}
 private String select(){return "select p.*,coalesce(s.price_cents,p.base_price_cents) price_cents,coalesce(s.channels,'{}'::text[]) channels,coalesce(s.enabled,false) listing_enabled,coalesce(s.version,0) listing_version from catalog_products p left join catalog_product_stores s on s.product_id=p.id and s.tenant_id=p.tenant_id and s.store_id=?";}
 private Product map(ResultSet r,int n)throws SQLException{return new Product(r.getObject("id",UUID.class),r.getObject("category_id",UUID.class),r.getString("code"),r.getString("name_zh"),r.getString("name_en"),r.getString("description_zh"),r.getString("description_en"),r.getString("kind"),r.getString("unit"),r.getString("image_url"),r.getString("status"),r.getInt("version"),r.getObject("price_cents",Long.class),r.getString("currency"),r.getString("tax_mode"),r.getString("tax_category"),r.getBigDecimal("tax_rate"),List.of((String[])r.getArray("channels").getArray()),r.getBoolean("listing_enabled"),r.getInt("listing_version"));}
 private static String text(String s){return s==null?"":s.trim();}
}
