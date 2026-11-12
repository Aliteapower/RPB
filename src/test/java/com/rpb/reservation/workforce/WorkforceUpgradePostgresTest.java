package com.rpb.reservation.workforce;

import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** Actual Flyway V087 -> V091 upgrade, with legacy data present before V088 grants. */
class WorkforceUpgradePostgresTest {
 @Test void legacyUpgradeBackfillsOnlyIntendedPermissionsAndPreservesBusinessData()throws Exception{
  var pointer=new HashMap<String,String>();for(var line:Files.readAllLines(Path.of("target/local-postgres-current.txt"))){var pair=line.split("=",2);if(pair.length==2)pointer.put(pair[0],pair[1]);}
  assertThat(Path.of(pointer.get("runtimeWorktree")).toAbsolutePath()).isEqualTo(Path.of("").toAbsolutePath());
  String schema="workforce_upgrade_"+UUID.randomUUID().toString().replace("-","");boolean keepForAcceptance=Boolean.getBoolean("rpb.workforce.acceptance.keep-upgrade-schema");
  String url="jdbc:postgresql://127.0.0.1:"+Integer.parseInt(pointer.get("port"))+"/postgres?stringtype=unspecified";
  var root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));
  var ds=new DriverManagerDataSource(url+"&currentSchema="+schema,"postgres","");var jdbc=new JdbcTemplate(ds);
  try {
   var prior=Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(MigrationVersion.fromVersion("87")).load();
   var initial=prior.migrate();assertThat(MigrationVersion.fromVersion(initial.targetSchemaVersion).compareTo(MigrationVersion.fromVersion("87"))).isZero();
   UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),store=UUID.randomUUID();
   jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Legacy tenant','active')",tenant,"legacy-"+tenant);
   jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'LEGACY','Legacy company','active')",company,tenant);
   jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'LEGACY','Legacy store','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,company);
   var accounts=new LinkedHashMap<String,UUID>();
   for(String label:List.of("head","manager","platform","staff","deleted","roleDeleted")){
    UUID id=UUID.randomUUID();accounts.put(label,id);
    jdbc.update("insert into auth_accounts(id,tenant_id,username,display_name,actor_type,status,password_hash,deleted_at) values(?,?,?,?,?,'active','legacy-test-hash',case when ? then now() else null end)",id,tenant,"legacy-"+id,label,label.equals("platform")?"platform_admin":"staff",label.equals("deleted"));
    jdbc.update("insert into auth_account_roles(account_id,role_code,deleted_at) values(?,?,case when ? then now() else null end)",id,label.equals("staff")?"store_staff":"tenant_admin",label.equals("roleDeleted"));
    if(label.equals("manager"))jdbc.update("insert into auth_account_roles(account_id,role_code) values(?,'store_manager')",id);
    jdbc.update("insert into auth_account_permissions(account_id,permission_code) values(?,'wallet.view')",id);
    jdbc.update("insert into auth_account_store_access(account_id,tenant_id,store_id) values(?,?,?)",id,tenant,store);
   }
   jdbc.update("insert into auth_staff_store_permissions(tenant_id,store_id,account_id,permissions,version) values(?,?,?,array['wallet.view'],1)",tenant,store,accounts.get("staff"));
   jdbc.update("insert into card_settings(tenant_id,store_id,enabled) values(?,?,false)",tenant,store);
   jdbc.update("insert into tenant_app_entitlements(tenant_id,app_key,status) values(?,'wallet','enabled')",tenant);
   jdbc.update("insert into store_app_settings(tenant_id,store_id,app_key,is_enabled,entry_visible) values(?,?,'wallet',true,true)",tenant,store);
   var before=new LinkedHashMap<String,List<String>>();
   for(String table:List.of("tenants","operating_entities","stores","auth_accounts","auth_account_roles","auth_account_store_access","auth_staff_store_permissions","card_settings","tenant_app_entitlements","store_app_settings","tenant_product_subscriptions","tenant_product_subscription_items"))before.put(table,jdbc.queryForList("select to_jsonb(t)::text from "+table+" t order by 1",String.class));
   var legacyPermissions=jdbc.queryForList("select * from auth_account_permissions order by id");
   // This regression documents the original phase-four V087 -> V091 upgrade.
   // Later migrations have dedicated upgrade tests with their own legacy fixtures.
   var full=Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("91").load();
   var migrated=full.migrate();assertThat(migrated.migrationsExecuted).isEqualTo(4);assertThat(full.validateWithResult().validationSuccessful).isTrue();
   for(var entry:before.entrySet())assertThat(jdbc.queryForList("select to_jsonb(t)::text from "+entry.getKey()+" t order by 1",String.class)).as(entry.getKey()).isEqualTo(entry.getValue());
   assertThat(jdbc.queryForList("select * from auth_account_permissions where permission_code not like 'workforce.%' order by id")).isEqualTo(legacyPermissions);
   assertThat(jdbc.queryForList("select permission_code from auth_account_permissions where account_id=? and permission_code like 'workforce.%' order by permission_code",String.class,accounts.get("head"))).containsExactly("workforce.admin.manage","workforce.view");
   assertThat(jdbc.queryForList("select permission_code from auth_account_permissions where account_id=? and permission_code like 'workforce.%'",String.class,accounts.get("manager"))).containsExactly("workforce.view");
   for(String label:List.of("platform","staff","deleted","roleDeleted"))assertThat(jdbc.queryForObject("select count(*) from auth_account_permissions where account_id=? and permission_code like 'workforce.%'",Integer.class,accounts.get(label))).as(label).isZero();
   assertThat(jdbc.queryForObject("select count(*) from tenant_app_entitlements where app_key='workforce'",Integer.class)).isZero();
   assertThat(jdbc.queryForObject("select count(*) from workforce_administrators",Integer.class)).isZero();
   assertThat(jdbc.queryForList("select version from flyway_schema_history where success and version::int between 88 and 91 order by installed_rank",String.class).stream().map(Integer::parseInt).toList()).containsExactly(88,89,90,91);
   assertThat(full.migrate().migrationsExecuted).isZero();
   var result=new LinkedHashMap<String,Object>();result.put("passed",true);result.put("baseline","087");result.put("target","091");result.put("migrationsExecuted",4);result.put("flywayValidated",true);result.put("legacyDataUnchanged",true);result.put("grantBackfillVerified",true);result.put("rerunMigrations",0);result.put("keptForAcceptance",keepForAcceptance);result.put("schema",schema);result.put("tenant",tenant.toString());result.put("store",store.toString());result.put("head",accounts.get("head").toString());
   Files.writeString(Path.of("target/workforce-acceptance-20261029/upgrade-result.json"),new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
  } finally {if(!keepForAcceptance&&schema.matches("workforce_upgrade_[a-f0-9]{32}"))root.execute("drop schema if exists "+schema+" cascade");}
 }
}
