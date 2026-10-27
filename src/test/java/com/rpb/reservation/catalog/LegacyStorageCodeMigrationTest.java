package com.rpb.reservation.catalog;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LegacyStorageCodeMigrationTest extends CatalogPostgresTest {
 private static final Path MIGRATION=Path.of("src/main/resources/db/migration/V085__legacy_storage_short_codes.sql");
 private void migrate() throws Exception {String sql=Files.readString(MIGRATION);tx.execute(s->{jdbc.execute(sql.substring(sql.indexOf("do $migration$")));return null;});}
 private UUID legacy(boolean proof,String custom){
  UUID id=UUID.randomUUID();String code=custom==null?"WS-"+id:custom;
  jdbc.update("insert into catalog_products(id,tenant_id,category_id,code,name_zh,name_en,kind,unit,status,base_price_cents,tax_mode,tax_category,tax_rate) values(?,?,?,?, '原酒','','physical','瓶','active',null,null,null,null)",id,tenant,product.categoryId(),code);
  if(proof)jdbc.update("insert into catalog_audit(tenant_id,store_id,actor_id,action,source_id) values(?,?,?,'storage.product.create',?)",tenant,store,actor.actorId(),id);
  return id;
 }
 private String row(UUID id){return jdbc.queryForObject("select to_jsonb(p)::text from catalog_products p where id=?",String.class,id);}
 private String history(){return jdbc.queryForObject("select coalesce(json_agg(to_jsonb(i) order by id),'[]'::json)::text from wine_storage_items i",String.class);}
 @Test void onlyProvenGeneratedCodesChangeAndHistoricalRowsRemainByteForByte() throws Exception {
  UUID id=legacy(true,null),manual=legacy(false,null),fake=legacy(true,"WS-"+UUID.randomUUID());legacy(false,"1");
  UUID app=UUID.randomUUID(),line=UUID.randomUUID(),rule=UUID.randomUUID();
  jdbc.update("insert into wine_storage_applications(id,tenant_id,store_id,customer_id,status,original,created_at,updated_at) values(?,?,?,?,'stored','{}',now(),now())",app,tenant,store,customer);
  jdbc.update("insert into wine_storage_application_lines(id,tenant_id,store_id,customer_id,application_id,original) values(?,?,?,?,?,'{}')",line,tenant,store,customer,app);
  jdbc.update("insert into wine_storage_rules(id,tenant_id,store_id,category_id,name,months,version,created_by) values(?,?,?,?,'保存',3,1,?)",rule,tenant,store,product.categoryId(),actor.actorId());
  jdbc.update("insert into wine_storage_items(id,tenant_id,store_id,customer_id,application_id,line_id,product_id,rule_id,snapshot,quantity,remaining,opened,source,location,label,received_at,expires_at) values(?,?,?,?,?,?,?,?,jsonb_build_object('productId',?::text,'code',?::text),3,2,false,'customer','原位置','原标签',now(),now()+interval '3 months')",UUID.randomUUID(),tenant,store,customer,app,line,id,rule,id.toString(),"WS-"+id);
  String original=history(),manualBefore=row(manual),fakeBefore=row(fake);migrate();
  assertThat(history()).isEqualTo(original);assertThat(row(manual)).isEqualTo(manualBefore);assertThat(row(fake)).isEqualTo(fakeBefore);
  assertThat(jdbc.queryForObject("select code from catalog_products where id=?",String.class,id)).matches("[0-9]{1,5}").isNotEqualTo("1");
  assertThat(jdbc.queryForObject("select old_code from catalog_legacy_storage_code_changes where product_id=?",String.class,id)).isEqualTo("WS-"+id);
  assertThat(jdbc.queryForObject("select version from catalog_products where id=?",Integer.class,id)).isEqualTo(1);
 }
 @Test void tenantsAllocateIndependentlyAndReexecutionDoesNotChangeMappedRows() throws Exception {
  UUID a=legacy(true,null);var tenantA=tenant;fixture();UUID b=legacy(true,null);migrate();String beforeA=row(a),beforeB=row(b);migrate();
  assertThat(row(a)).isEqualTo(beforeA);assertThat(row(b)).isEqualTo(beforeB);
  assertThat(jdbc.queryForObject("select count(*) from catalog_legacy_storage_code_changes where tenant_id in (?,?)",Integer.class,tenantA,tenant)).isEqualTo(2);
  assertThat(jdbc.queryForObject("select code from catalog_products where id=?",String.class,a)).isEqualTo(jdbc.queryForObject("select code from catalog_products where id=?",String.class,b));
 }
 @Test void exhaustionRollsBackEveryCodeAndMapping() throws Exception {
  UUID one=legacy(true,null),two=legacy(true,null);String beforeOne=row(one),beforeTwo=row(two);
  jdbc.update("insert into catalog_products(id,tenant_id,category_id,code,name_zh,name_en,kind,unit,status) select gen_random_uuid(),?,?,n::text,'保留','','physical','瓶','active' from generate_series(1,99998) n",tenant,product.categoryId());
  assertThatThrownBy(this::migrate).hasMessageContaining("short code range exhausted");
  assertThat(row(one)).isEqualTo(beforeOne);assertThat(row(two)).isEqualTo(beforeTwo);
  assertThat(jdbc.queryForObject("select count(*) from catalog_legacy_storage_code_changes where tenant_id=?",Integer.class,tenant)).isZero();
  // This class shares its disposable schema: don't leave the deliberately exhausted tenant for later cases.
  jdbc.update("delete from catalog_audit where tenant_id=? and source_id in (?,?)",tenant,one,two);
  jdbc.update("delete from catalog_products where tenant_id=? and id<>?",tenant,product.id());
 }
}
