package com.rpb.reservation.customerauth.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.customerauth.application.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class TenantCustomerEmailSettingsPostgresTest {
 static JdbcTemplate root,db;
 static String schema="tenant_email_test_"+UUID.randomUUID().toString().replace("-","");
 static final UUID single=UUID.randomUUID(),conflict=UUID.randomUUID(),empty=UUID.randomUUID();
 static final Map<UUID,List<UUID>> stores=new HashMap<>();
 static CustomerAuthJdbcRepository repo;
 @BeforeAll static void start()throws Exception{
  String port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream().filter(l->l.startsWith("port=")).findFirst().orElseThrow().substring(5).trim();
  if(!port.matches("[0-9]{2,5}"))throw new IllegalStateException("Invalid runtime pointer");
  String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";
  root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));root.execute("create schema "+schema);
  db=new JdbcTemplate(new DriverManagerDataSource(url+"&currentSchema="+schema,"postgres",""));
  try(var files=Files.list(Path.of("src/main/resources/db/migration"))){for(var p:files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")&&!p.getFileName().toString().startsWith("V128__")).sorted().toList())db.execute(Files.readString(p));}
  for(UUID tenant:List.of(single,conflict,empty)){
   db.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"Email synthetic test");
   var ids=List.of(UUID.randomUUID(),UUID.randomUUID());stores.put(tenant,ids);
   for(UUID store:ids)db.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'Email test','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,store.toString());
  }
  for(UUID store:stores.get(single))legacy(single,store,"same-secret");
  legacy(conflict,stores.get(conflict).get(0),"secret-a");legacy(conflict,stores.get(conflict).get(1),"secret-b");
  db.update("insert into store_customer_email_settings(tenant_id,store_id) values(?,?)",empty,stores.get(empty).get(0));
  db.execute(Files.readString(Path.of("src/main/resources/db/migration/V128__tenant_customer_email_settings.sql")));
  repo=new CustomerAuthJdbcRepository(db);
 }
 static void legacy(UUID tenant,UUID store,String secret){db.update("insert into store_customer_email_settings(tenant_id,store_id,enabled,from_email,smtp_host,smtp_username,smtp_password_secret) values(?,?,true,'from@example.test','smtp.example.test','user',?)",tenant,store,secret);}
 @AfterAll static void close(){if(root!=null)root.execute("drop schema if exists "+schema+" cascade");}
 @Test void migrationPromotesOnlyUnambiguousAndPreservesAllOldRows(){
  assertThat(repo.emailSettingsScope(single,stores.get(single).get(1))).isEqualTo("tenant");
  assertThat(repo.findEmailSettings(single,stores.get(single).get(1)).orElseThrow().smtpPassword()).isEqualTo("same-secret");
  assertThat(repo.emailSettingsScope(conflict,stores.get(conflict).get(0))).isEqualTo("legacy_store");
  assertThat(repo.findEmailSettings(conflict,stores.get(conflict).get(0)).orElseThrow().smtpPassword()).isEqualTo("secret-a");
  assertThat(repo.findEmailSettings(conflict,stores.get(conflict).get(1)).orElseThrow().smtpPassword()).isEqualTo("secret-b");
  assertThat(db.queryForObject("select count(*) from store_customer_email_settings",Integer.class)).isEqualTo(5);
 }
 @Test void firstSaveIsSharedAcrossStoresBlankPasswordPreservedAndDisableDoesNotFallback(){
  var service=new CustomerAuthIntegrationAdminService(repo,repo,repo);
  UUID tenant=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
  db.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"New tenant");
  for(UUID store:List.of(a,b))db.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'New','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,store.toString());
  assertThat(service.getEmailSettings(new StoreScope(new TenantId(tenant),b)).enabled()).isFalse();
  var s=new StoreScope(new TenantId(tenant),a);var other=new StoreScope(new TenantId(tenant),b);
  var saved=service.saveEmailSettings(s,new CustomerEmailSettingsCommand(true,"new@example.test","Tenant","smtp.example.test",587,"user","new-secret",true));
  assertThat(saved.smtpPassword()).isNull();assertThat(saved.secretConfigured()).isTrue();
  assertThat(repo.findEmailSettings(tenant,b).orElseThrow().smtpPassword()).isEqualTo("new-secret");
  service.saveEmailSettings(other,new CustomerEmailSettingsCommand(false,"new@example.test","Tenant","smtp.example.test",587,"user","",true));
  assertThat(service.getEmailSettings(s).enabled()).isFalse();assertThat(repo.findEmailSettings(tenant,a).orElseThrow().smtpPassword()).isEqualTo("new-secret");
  service.saveEmailSettings(s,new CustomerEmailSettingsCommand(true,"new@example.test","Tenant","smtp.example.test",587,"user",null,true));
  assertThat(service.getEmailSettings(other).enabled()).isTrue();
  assertThat(db.queryForObject("select count(*) from tenant_customer_email_settings where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
 }
 @Test void tenantIsolationAndLegacyDisableCannotReactivateOldConfiguration(){
  assertThat(repo.findEmailSettings(single,stores.get(conflict).get(0))).isEmpty();
  assertThatThrownBy(()->repo.saveEmailSettings(new StoreScope(new TenantId(single),stores.get(conflict).get(0)),CustomerEmailSettings.disabled())).isInstanceOf(IllegalArgumentException.class);
  assertThat(repo.emailSettingsScope(empty,stores.get(empty).get(1))).isEqualTo("unconfigured");
  UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();
  db.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"Disabled test");
  db.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'New','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,store.toString());legacy(tenant,store,"legacy-secret");
  repo.saveEmailSettings(new StoreScope(new TenantId(tenant),store),CustomerEmailSettings.disabled());
  assertThat(repo.findEmailSettings(tenant,store).orElseThrow().enabled()).isFalse();
  assertThat(repo.emailSettingsScope(tenant,store)).isEqualTo("tenant");
 }
}
