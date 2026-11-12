package com.rpb.reservation.workforce;
import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
class WorkforceBulkScheduleMigrationTest {
 @Test void actualV92UpgradePreservesBusinessTablesAndReceiptForeignKeys()throws Exception{
  var pointer=new HashMap<String,String>();for(var line:Files.readAllLines(Path.of("target/local-postgres-current.txt"))){var p=line.split("=",2);if(p.length==2)pointer.put(p[0],p[1]);}
  assertThat(Path.of(pointer.get("runtimeWorktree")).toAbsolutePath()).isEqualTo(Path.of("").toAbsolutePath());
  String schema="workforce_bulk_upgrade_"+UUID.randomUUID().toString().replace("-",""),url="jdbc:postgresql://127.0.0.1:"+Integer.parseInt(pointer.get("port"))+"/postgres?stringtype=unspecified";
  var root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));var ds=new DriverManagerDataSource(url+"&currentSchema="+schema,"postgres","");var jdbc=new JdbcTemplate(ds);
  try{
   Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("92").load().migrate();
   var tables=List.of("workforce_employees","workforce_schedules","workforce_pay_batches","workforce_pay_payments");var before=new HashMap<String,List<String>>();for(var table:tables)before.put(table,jdbc.queryForList("select to_jsonb(t)::text from "+table+" t order by 1",String.class));
   var flyway=Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("93").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);assertThat(flyway.validateWithResult().validationSuccessful).isTrue();assertThat(flyway.migrate().migrationsExecuted).isZero();
   for(var table:tables)assertThat(jdbc.queryForList("select to_jsonb(t)::text from "+table+" t order by 1",String.class)).isEqualTo(before.get(table));
   assertThat(jdbc.queryForObject("select count(*) from information_schema.table_constraints where table_schema=? and table_name='workforce_bulk_schedule_commands' and constraint_type='FOREIGN KEY'",Integer.class,schema)).isEqualTo(3);
  }finally{if(schema.matches("workforce_bulk_upgrade_[a-f0-9]{32}"))root.execute("drop schema if exists "+schema+" cascade");}
 }
}
