package com.rpb.reservation.workforce;
import java.nio.file.*;import java.util.*;
import org.junit.jupiter.api.Test;import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
class WorkforcePayrollComputationMigrationTest {
 @Test void upgradeAddsOnlyEmptyEvidenceTablesAndPreservesHistoricalWages()throws Exception{
  var pointer=new HashMap<String,String>();for(var line:Files.readAllLines(Path.of("target/local-postgres-current.txt"))){var p=line.split("=",2);if(p.length==2)pointer.put(p[0],p[1]);}
  assertThat(Path.of(pointer.get("runtimeWorktree")).toAbsolutePath()).isEqualTo(Path.of("").toAbsolutePath());
  var schema="workforce_compute_upgrade_"+UUID.randomUUID().toString().replace("-","");var url="jdbc:postgresql://127.0.0.1:"+Integer.parseInt(pointer.get("port"))+"/postgres?stringtype=unspecified";
  var root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));var ds=new DriverManagerDataSource(url+"&currentSchema="+schema,"postgres","");var jdbc=new JdbcTemplate(ds);
  try{
   Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("104").load().migrate();
   var tenant=UUID.randomUUID();var company=UUID.randomUUID();var actor=UUID.randomUUID();var batch=UUID.randomUUID();
   jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,'COMPUTE-UPGRADE','Old','active')",tenant);
   jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'OLD','Old','active')",company,tenant);
   jdbc.update("insert into auth_accounts(id,tenant_id,username,display_name,actor_type,status,password_hash) values(?,?,'compute-upgrade','Old','staff','active','unchanged')",actor,tenant);
   jdbc.update("insert into workforce_pay_batches(id,tenant_id,company_id,company_name,period_month,status,version,calculation,created_by,confirmed_by,confirmed_at) values(?,?,?,'Old','2026-09-01','confirmed',3,'{\"totalGross\":\"3000.00\"}',?,?,now())",batch,tenant,company,actor,actor);
   var before=jdbc.queryForObject("select to_jsonb(b)::text from workforce_pay_batches b where id=?",String.class,batch);
   var upgrade=Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("105").load();assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();assertThat(upgrade.migrate().migrationsExecuted).isZero();
   assertThat(jdbc.queryForObject("select to_jsonb(b)::text from workforce_pay_batches b where id=?",String.class,batch)).isEqualTo(before);
   assertThat(jdbc.queryForObject("select count(*) from workforce_pay_computations",Integer.class)).isZero();assertThat(jdbc.queryForObject("select count(*) from workforce_pay_bulk_operations",Integer.class)).isZero();
  }finally{if(schema.matches("workforce_compute_upgrade_[a-f0-9]{32}"))root.execute("drop schema if exists "+schema+" cascade");}
 }
}
