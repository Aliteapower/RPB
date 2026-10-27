package com.rpb.reservation.winestorage;

import com.rpb.reservation.catalog.CatalogPostgresTest;
import com.rpb.reservation.winestorage.persistence.StorageRepository;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageEventNamesPostgresTest extends CatalogPostgresTest {
 void account(UUID id,UUID owner,String name,String username){jdbc.update("insert into auth_accounts(id,tenant_id,username,display_name,actor_type,status,password_hash) values(?,?,?,?,'tenant_admin','active','not-a-login')",id,owner,username,name);}
 @Test void eventNamesResolveStaffCustomersAndRetainedAccountsWithoutRewritingAudit(){
  var r=new StorageRepository(jdbc,json);UUID staff=UUID.randomUUID(),source=UUID.randomUUID();account(staff,tenant,"店长王","staff-"+staff);
  r.event(scope,customer,source,"received",staff,Map.of(),Instant.now());
  r.event(scope,customer,source,"applied",customer,Map.of(),Instant.now());
  assertThat(r.events(scope,customer)).anySatisfy(e->{assertThat(e.actorId()).isEqualTo(staff);assertThat(e.actorName()).isEqualTo("店长王");}).anySatisfy(e->{assertThat(e.actorId()).isEqualTo(customer);assertThat(e.actorName()).isEqualTo("顾客");});
  jdbc.update("update auth_accounts set status='disabled',deleted_at=now() where id=?",staff);
  assertThat(r.events(scope,customer)).anyMatch(e->"店长王".equals(e.actorName()));
  jdbc.update("update auth_accounts set display_name='   ' where id=?",staff);
  assertThat(r.events(scope,customer)).anyMatch(e->("staff-"+staff).equals(e.actorName()));
  assertThat(r.rows(scope,"select details::text from wine_storage_events where tenant_id=? and store_id=?")).allMatch(row->row.get("details").equals("{}"));
 }
 @Test void namesNeverEscapeTenantStoreOrCustomerScopeAndActorKindsDoNotCollide(){
  var r=new StorageRepository(jdbc,json);UUID otherTenant=UUID.randomUUID(),foreign=UUID.randomUUID(),source=UUID.randomUUID();
  jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",otherTenant,otherTenant.toString(),"Other");
  account(foreign,otherTenant,"外租户姓名","foreign-"+foreign);account(customer,tenant,"同号员工","same-"+customer);
  r.event(scope,customer,source,"received",foreign,Map.of(),Instant.now());
  r.event(scope,customer,source,"applied",customer,Map.of(),Instant.now());
  r.event(scope,customer,source,"received",customer,Map.of(),Instant.now());
  var events=r.events(scope,customer);assertThat(events).anySatisfy(e->{if(e.actorId().equals(foreign))assertThat(e.actorName()).isNull();else throw new AssertionError();});
  assertThat(events).anyMatch(e->e.action().equals("applied")&&"顾客".equals(e.actorName()));
  assertThat(events).anyMatch(e->e.action().equals("received")&&"同号员工".equals(e.actorName()));
  assertThat(r.events(scope,UUID.randomUUID())).isEmpty();
  assertThat(r.events(new StoreScope(new TenantId(otherTenant),store),null)).isEmpty();
  assertThat(r.events(new StoreScope(new TenantId(tenant),UUID.randomUUID()),null)).isEmpty();
 }
}
