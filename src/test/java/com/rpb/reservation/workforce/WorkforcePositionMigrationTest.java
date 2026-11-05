package com.rpb.reservation.workforce;
import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
class WorkforcePositionMigrationTest {
 @Test void actualV91UpgradeBackfillsDeduplicatedPositionsWithoutChangingLegacyEmployeeData()throws Exception{
  var pointer=new HashMap<String,String>();for(var line:Files.readAllLines(Path.of("target/local-postgres-current.txt"))){var p=line.split("=",2);if(p.length==2)pointer.put(p[0],p[1]);}
  assertThat(Path.of(pointer.get("runtimeWorktree")).toAbsolutePath()).isEqualTo(Path.of("").toAbsolutePath());
  String schema="workforce_position_upgrade_"+UUID.randomUUID().toString().replace("-",""),url="jdbc:postgresql://127.0.0.1:"+Integer.parseInt(pointer.get("port"))+"/postgres?stringtype=unspecified";
  var root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));var ds=new DriverManagerDataSource(url+"&currentSchema="+schema,"postgres","");var jdbc=new JdbcTemplate(ds);
  try{
   Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("91").load().migrate();
   UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),store=UUID.randomUUID();
   jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Position migration','active')",tenant,"t-"+tenant);
   jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'POS','Company','active')",company,tenant);
   jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'POS','Store','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,company);
   int i=0;for(String title:List.of("Service"," service ","前厅",""))jdbc.update("insert into workforce_employees(id,tenant_id,employee_no,name,job_title,hire_date,status,company_id,company_effective_date,main_store_id,version) values(?,?,?,'Employee',?,'2026-09-01','active',?,'2026-09-01',?,7)",UUID.randomUUID(),tenant,"000"+(++i),title,company,store);
   var before=jdbc.queryForList("select to_jsonb(e)::text from workforce_employees e order by employee_no",String.class);
   var flyway=Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("92").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
   assertThat(jdbc.queryForList("select (to_jsonb(e)-'position_id')::text from workforce_employees e order by employee_no",String.class)).isEqualTo(before);
   assertThat(jdbc.queryForObject("select count(*) from workforce_positions",Integer.class)).isEqualTo(2);
   assertThat(jdbc.queryForObject("select count(*) from workforce_employees where position_id is not null",Integer.class)).isEqualTo(3);
   assertThat(jdbc.queryForObject("select count(distinct position_id) from workforce_employees where lower(btrim(job_title))='service'",Integer.class)).isEqualTo(1);
   assertThat(flyway.migrate().migrationsExecuted).isZero();
  }finally{if(schema.matches("workforce_position_upgrade_[a-f0-9]{32}"))root.execute("drop schema if exists "+schema+" cascade");}
 }
}
