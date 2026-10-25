package com.rpb.reservation.catalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.catalog.access.CatalogAccess;
import com.rpb.reservation.catalog.application.*;
import com.rpb.reservation.catalog.application.CatalogDtos.*;
import com.rpb.reservation.catalog.persistence.*;
import com.rpb.reservation.common.scope.*;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
public class CatalogPostgresTest {
 protected static JdbcTemplate jdbc,root;protected static TransactionTemplate tx;
 protected static final String SCHEMA="catalog_cards_"+UUID.randomUUID().toString().replace("-","");
 protected UUID tenant,store,customer,issuer;protected StoreScope scope;protected CurrentActor actor;
 protected CatalogService catalog;protected ProductCatalogQuery products;protected Product product;
 protected final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 @BeforeAll static void database()throws Exception{
  String port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream().filter(x->x.startsWith("port=")).findFirst().orElseThrow().substring(5);
  String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";
  root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));root.execute("create schema "+SCHEMA);
  var ds=new DriverManagerDataSource(url+"&currentSchema="+SCHEMA+",public","postgres","");jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  try(var files=Files.list(Path.of("src/main/resources/db/migration"))){for(var f:files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList())jdbc.execute(Files.readString(f));}
 }
 @AfterAll static void cleanup(){if(root!=null)root.execute("drop schema if exists "+SCHEMA+" cascade");}
 @BeforeEach protected void fixture(){
  tenant=UUID.randomUUID();store=UUID.randomUUID();customer=UUID.randomUUID();issuer=UUID.randomUUID();
  jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"Catalog test");
  jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'issuer','Issuer','active')",issuer,tenant);
  jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'main','Store','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,issuer);
  jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,phone_e164,status) values(?,?,'customer','regular','顾客','+6591234567','active')",customer,tenant);
  scope=new StoreScope(new TenantId(tenant),store);actor=new CurrentActor(tenant,UUID.randomUUID(),"tenant_admin",Set.of("tenant_admin"),Set.of("catalog.view","catalog.manage","catalog.publish"),Set.of(store));
  var repo=new CatalogRepository(jdbc);products=new ProductCatalogQuery(repo);var access=mock(CatalogAccess.class);
  catalog=new CatalogService(access,repo,new CatalogCommands(jdbc,json),products);
  var cat=tx.execute(s->catalog.category(scope,actor,null,new CategoryWrite("C","饮品","Drinks",true,0,null,key())));
  product=tx.execute(s->catalog.save(scope,actor,null,write(cat.id(),null)));
  product=tx.execute(s->catalog.status(scope,actor,product.id(),new Status("active",0,key())));
 }
 protected String key(){return UUID.randomUUID().toString();}
 protected ProductWrite write(UUID category,Integer version){return new ProductWrite("P",category,"咖啡","Coffee","说明","Details","physical","杯","",500,"inclusive","taxable",new BigDecimal("0.10"),List.of("cards"),version,key());}
 @Test void bilingualDataIdempotencyAndStaleVersions(){
  assertThat(product.displayName("en")).isEqualTo("Coffee");assertThat(product.displayName("zh")).isEqualTo("咖啡");
  var c=write(product.categoryId(),product.version());var first=tx.execute(s->catalog.save(scope,actor,product.id(),c));
  Product replay=tx.execute(s->catalog.save(scope,actor,product.id(),c));assertThat(replay).isEqualTo(first);
  assertThatThrownBy(()->tx.execute(s->catalog.save(scope,actor,product.id(),write(product.categoryId(),0)))).hasMessageContaining("变化");
 }
 @Test void tenantAndChannelBoundariesAndHistory(){
  assertThatThrownBy(()->products.get(new StoreScope(new TenantId(UUID.randomUUID()),store),product.id())).hasMessageContaining("不存在");
  assertThatThrownBy(()->products.requireUsable(scope,product.id(),"shopping")).hasMessageContaining("适用");
  tx.execute(s->catalog.status(scope,actor,product.id(),new Status("inactive",product.version(),key())));
  assertThat(products.get(scope,product.id()).nameEn()).isEqualTo("Coffee");assertThatThrownBy(()->products.requireUsable(scope,product.id(),"cards")).hasMessageContaining("上架");
 }
 @Test void compositeForeignKeysPreventCrossTenantCategory(){assertThatThrownBy(()->tx.execute(s->catalog.save(scope,actor,null,write(UUID.randomUUID(),null)))).hasMessageContaining("不存在");}
 @Test void disablingOneStoreListingDoesNotWithdrawSiblingProduct(){UUID sibling=UUID.randomUUID();jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'sibling','Sibling','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",sibling,tenant,issuer);var other=new StoreScope(new TenantId(tenant),sibling);
  tx.execute(s->catalog.listing(other,actor,product.id(),new Listing(700,List.of("cards"),true,0,key())));
  tx.execute(s->catalog.listing(scope,actor,product.id(),new Listing(500,List.of("cards"),false,product.listingVersion(),key())));
  assertThatThrownBy(()->products.requireUsable(scope,product.id(),"cards")).hasMessageContaining("适用");assertThat(products.requireUsable(other,product.id(),"cards").priceCents()).isEqualTo(700);
 }
}
