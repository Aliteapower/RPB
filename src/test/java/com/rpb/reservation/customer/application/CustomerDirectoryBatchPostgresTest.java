package com.rpb.reservation.customer.application;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.customer.persistence.adapter.CustomerPersistenceAdapter;
import com.rpb.reservation.tenant.value.TenantId;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class CustomerDirectoryBatchPostgresTest {
 @Test void batchReadsOnlyRequestedActiveCustomersWithinTenant()throws Exception {
  String port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream().filter(x->x.startsWith("port=")).findFirst().orElseThrow().substring(5);
  var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified","postgres",""));
  String schema="customer_contact_test_"+UUID.randomUUID().toString().replace("-","");jdbc.execute("create schema "+schema);
  try {
   var scoped=new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified&currentSchema="+schema,"postgres",""));
   scoped.execute("create table customers(id uuid primary key,tenant_id uuid,customer_code text,display_name text,nickname text,phone_e164 text,email text,status text,created_at timestamptz default now(),updated_at timestamptz default now(),deleted_at timestamptz)");
   UUID tenant=UUID.randomUUID(),valid=UUID.randomUUID(),foreign=UUID.randomUUID(),archived=UUID.randomUUID(),deleted=UUID.randomUUID(),unrequested=UUID.randomUUID();
   for(UUID id:List.of(valid,foreign,archived,deleted,unrequested))scoped.update("insert into customers(id,tenant_id,customer_code,display_name,phone_e164,status,deleted_at) values(?,?,?,?,?,?,?::timestamptz)",id,id.equals(foreign)?UUID.randomUUID():tenant,"C", "陈小明","+6591234567",id.equals(archived)?"archived":"active",id.equals(deleted)?"2026-10-28T00:00:00Z":null);
   var directory=new CustomerDirectoryQuery(new CustomerPersistenceAdapter(null,null,null,scoped));
   var scope=new TenantScope(new TenantId(tenant));
   var result=directory.activeByIds(scope,Set.of(valid,foreign,archived,deleted,UUID.randomUUID()));
   assertThat(result).extracting(CustomerManagementItem::id).containsExactly(valid);
   assertThat(result.get(0).displayName()).isEqualTo("陈小明");assertThat(result.get(0).phoneE164()).isEqualTo("+6591234567");
   assertThat(directory.activeByIds(scope,Set.of())).isEmpty();
  }finally{jdbc.execute("drop schema "+schema+" cascade");}
 }
}
