package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** Actual local Flyway paths; immutable deployed history is never repaired. */
class WorkforceAttendanceMigrationUpgradeTest {
 record Database(String schema,DriverManagerDataSource ds,JdbcTemplate jdbc) {
  Flyway flyway(String target){return Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();}
 }
 Database database()throws Exception{
  var pointer=new HashMap<String,String>();
  for(String line:Files.readAllLines(Path.of("target/local-postgres-current.txt"))){var pair=line.split("=",2);if(pair.length==2)pointer.put(pair[0],pair[1]);}
  assertThat(Path.of(pointer.get("runtimeWorktree")).toAbsolutePath()).isEqualTo(Path.of("").toAbsolutePath());
  String schema="attendance_upgrade_"+UUID.randomUUID().toString().replace("-","");
  var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:"+Integer.parseInt(pointer.get("port"))+"/postgres?stringtype=unspecified&currentSchema="+schema,"postgres","");
  return new Database(schema,ds,new JdbcTemplate(ds));
 }
 @Test void freshInstallValidatesAndRepeatedMigrationDoesNothing()throws Exception{
  var db=database();var flyway=db.flyway("125");assertThat(flyway.migrate().migrationsExecuted).isEqualTo(125);
  assertThat(flyway.validateWithResult().validationSuccessful).isTrue();assertThat(flyway.migrate().migrationsExecuted).isZero();
  assertDeployedChecksums(db.jdbc());assertNewTablesAndInitiatorConstraint(db.jdbc());
 }
 @Test void deployedV120UpgradesOnlyFourTimesAndPreservesExistingDataAndGuards()throws Exception{
  upgrade("120",5,true);
 }
 @Test void formerV113BaselineUpgradesThroughReleasedMigrationsBeforeAttendance()throws Exception{
  upgrade("113",12,false);
 }
 void upgrade(String version,int count,boolean contracts)throws Exception{
  var db=database();db.flyway(version).migrate();seed(db.jdbc(),contracts);
  var before=snapshot(db.jdbc());var history=db.jdbc().queryForList("select installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success from flyway_schema_history order by installed_rank");
  var flyway=db.flyway("125");assertThat(flyway.migrate().migrationsExecuted).isEqualTo(count);
  assertThat(flyway.validateWithResult().validationSuccessful).isTrue();assertThat(flyway.migrate().migrationsExecuted).isZero();
  for(var e:before.entrySet())assertThat(rows(db.jdbc(),e.getKey())).as(e.getKey()).isEqualTo(e.getValue());
  assertThat(db.jdbc().queryForList("select installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success from flyway_schema_history where installed_rank<=? order by installed_rank",((Number)history.getLast().get("installed_rank")).intValue())).isEqualTo(history);
  assertDeployedChecksums(db.jdbc());assertNewTablesAndInitiatorConstraint(db.jdbc());
  if(contracts)assertThatThrownBy(()->db.jdbc().update("update workforce_employment_contracts set snapshot='{}'::jsonb")).hasMessageContaining("immutable");
 }
 void assertDeployedChecksums(JdbcTemplate jdbc)throws Exception{
  var fixture=new ObjectMapper().readTree(Path.of("src/test/resources/workforce/released-v120-migrations.json").toFile());
  for(var expected:fixture.path("migrations")){
   var actual=jdbc.queryForMap("select script,checksum,success from flyway_schema_history where version::int=?",expected.path("version").asInt());
   assertThat(actual.get("script")).isEqualTo(expected.path("script").asText());assertThat(actual.get("checksum")).isEqualTo(expected.path("checksum").asInt());assertThat(actual.get("success")).isEqualTo(true);
  }
 }
 Map<String,List<String>> snapshot(JdbcTemplate jdbc){
  var result=new LinkedHashMap<String,List<String>>();
  for(String table:jdbc.queryForList("select tablename from pg_tables where schemaname=current_schema() and tablename<>'flyway_schema_history' order by tablename",String.class))result.put(table,rows(jdbc,table));
  return result;
 }
 List<String> rows(JdbcTemplate jdbc,String table){assertThat(table).matches("[a-z_][a-z0-9_]*");return jdbc.queryForList("select to_jsonb(t)::text from \""+table+"\" t order by 1",String.class);}
 void assertNewTablesAndInitiatorConstraint(JdbcTemplate jdbc){
  for(String table:List.of("workforce_attendance_policies","workforce_attendance_policy_commands","workforce_attendance_days","workforce_attendance_day_versions","workforce_attendance_exceptions","workforce_leave_entitlements","workforce_leave_entitlement_commands"))assertThat(jdbc.queryForObject("select count(*) from "+table,Integer.class)).as(table).isZero();
  assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=current_schema() and table_name='workforce_attendance_exceptions' and column_name='medical_reference' and character_maximum_length=500",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=current_schema() and table_name='workforce_attendance_day_versions' and column_name='actor_id' and is_nullable='YES'",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from pg_constraint where connamespace=(select oid from pg_namespace where nspname=current_schema()) and conname='workforce_attendance_day_initiator'",Integer.class)).isEqualTo(1);
 }
 void seed(JdbcTemplate jdbc,boolean contracts){
  UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),store=UUID.randomUUID(),actor=UUID.randomUUID(),employee=UUID.randomUUID();
  jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Migration fixture','active')",tenant,"migration-"+tenant);
  jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'MIG','Migration company','active')",company,tenant);
  jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'MIG','Migration store','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,company);
  jdbc.update("insert into auth_accounts(id,tenant_id,username,display_name,actor_type,status,password_hash) values(?,?,?,'Migration actor','staff','active','synthetic-only')",actor,tenant,"migration-"+actor);
  jdbc.update("insert into workforce_employees(id,tenant_id,employee_no,name,hire_date,status,company_id,company_effective_date,main_store_id,version) values(?,?,'MIG001','Migration employee','2026-01-01','active',?,'2026-01-01',?,1)",employee,tenant,company,store);
  jdbc.update("insert into workforce_pay_batches(id,tenant_id,company_id,company_name,period_month,status,version,calculation,created_by,confirmed_by,published_by,confirmed_at,published_at) values(?,?,?,'Migration company','2026-09-01','published',1,'{\"preserved\":\"synthetic published evidence\"}'::jsonb,?,?,?,now(),now())",UUID.randomUUID(),tenant,company,actor,actor,actor);
  if(!contracts)return;
  UUID policy=UUID.randomUUID(),contract=UUID.randomUUID(),account=UUID.randomUUID(),lot=UUID.randomUUID();
  jdbc.update("insert into workforce_compliance_policies(id,tenant_id,version,name,effective_from,effective_to,status,snapshot,created_by) values(?,?,1,'Preserved policy','2026-01-01','2026-12-31','published','{\"agreedOvertime\":\"included\"}',?)",policy,tenant,actor);
  jdbc.update("insert into workforce_employment_contracts(id,tenant_id,employee_id,company_id,version,effective_from,policy_id,policy_version,snapshot,created_by) values(?,?,?,?,1,'2026-01-01',?,1,'{\"agreedTerms\":\"preserved\"}',?)",contract,tenant,employee,company,policy,actor);
  jdbc.update("insert into workforce_leave_accounts(id,tenant_id,company_id,employee_id,account_year,kind,unit,contract_id,opening_on,baseline_accrual,version,created_by) values(?,?,?,?,2026,'annual','day',?,'2026-01-01',14,1,?)",account,tenant,company,employee,contract,actor);
  jdbc.update("insert into workforce_leave_credit_lots(id,tenant_id,account_id,origin,source_key,expires_on,quantity,entitlement_year,contract_id,account_version) values(?,?,?,'manual','migration-lot','2026-12-31',14,2026,?,1)",lot,tenant,account,contract);
  jdbc.update("insert into workforce_leave_ledger(id,tenant_id,account_id,lot_id,source_key,operation,quantity,contract_id,account_version,reason,actor_id) values(?,?,?,?,'migration-credit','credit',14,?,1,'Preserved opening',?)",UUID.randomUUID(),tenant,account,lot,contract,actor);
 }
}
