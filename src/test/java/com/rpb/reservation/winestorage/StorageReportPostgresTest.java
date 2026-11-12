package com.rpb.reservation.winestorage;

import com.rpb.reservation.winestorage.persistence.StorageReportRepository;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageReportPostgresTest extends StorageInventoryPostgresTest {
 @Test void reportsCountBillsOncePreserveCollectedHistoryAndPageAfterFiltering(){
  UUID one=add(customer,3,0,false,now.plusSeconds(10),"青岛%_黑啤");
  UUID app=jdbc.queryForObject("select application_id from wine_storage_items where id=?",UUID.class,one);
  jdbc.update("insert into wine_storage_items(id,tenant_id,store_id,customer_id,application_id,line_id,product_id,rule_id,snapshot,quantity,remaining,opened,source,location,label,received_at,expires_at,physical_disposition) select gen_random_uuid(),tenant_id,store_id,customer_id,application_id,line_id,product_id,rule_id,snapshot,2,2,opened,source,location,label,received_at,expires_at,physical_disposition from wine_storage_items where id=?",one);
  add(customer,4,1,false,now.plusSeconds(10),"蓝带");
  var r=new StorageReportRepository(jdbc);
  var all=r.list(scope,"deposit","","all",null,null,0,1,now);
  assertThat(all.items()).hasSize(1);assertThat(all.hasMore()).isTrue();assertThat(all.summary().recordCount()).isEqualTo(2);assertThat(all.summary().bottleCount()).isEqualTo(9);
  assertThat(r.list(scope,"deposit","","all",null,null,1,1,now).items().getFirst().id()).isNotEqualTo(all.items().getFirst().id());
  var filtered=r.list(scope,"deposit","%_","stored",null,null,0,20,now);
  assertThat(filtered.items()).singleElement().satisfies(row->{assertThat(row.id()).isEqualTo(app);assertThat(row.quantity()).isEqualTo(5);assertThat(row.customerName()).isEqualTo("顾客");});
  assertThat(filtered.summary().recordCount()).isEqualTo(1);
  assertThat(r.list(scope,"deposit","","all",now,now.plusSeconds(86400),0,20,now).items()).isEmpty();
  assertThat(r.list(new StoreScope(new TenantId(UUID.randomUUID()),store),"deposit","","all",null,null,0,20,now).items()).isEmpty();
  assertThat(r.list(new StoreScope(new TenantId(tenant),UUID.randomUUID()),"deposit","","all",null,null,0,20,now).summary().recordCount()).isZero();
 }
 @Test void pickupsUseHandoverTimeAndActualSelectionsAndExpiryNeverChangesCompleted(){
  UUID item=add(customer,3,1,false,now.plusSeconds(10),"啤酒");UUID done=UUID.randomUUID(),pending=UUID.randomUUID();
  for(UUID id:List.of(done,pending)){
   jdbc.update("insert into wine_storage_intents(id,tenant_id,store_id,customer_id,origin,status,code_hash,expires_at,created_at,completed_at) values(?,?,?,?,'customer',?,?,?, ?,?)",id,tenant,store,customer,id.equals(done)?"completed":"authorized",id.toString(),Timestamp.from(now.minusSeconds(1)),Timestamp.from(now.minusSeconds(100)),id.equals(done)?Timestamp.from(now):null);
   jdbc.update("insert into wine_storage_intent_lines(tenant_id,store_id,customer_id,intent_id,item_id,quantity) values(?,?,?,?,?,?)",tenant,store,customer,id,item,2);
  }
  var r=new StorageReportRepository(jdbc);var all=r.list(scope,"pickup","","all",null,null,0,20,now);
  assertThat(all.summary().recordCount()).isEqualTo(2);assertThat(all.summary().completedCount()).isEqualTo(1);assertThat(all.summary().bottleCount()).isEqualTo(2);
  assertThat(all.items().getFirst().occurredAt()).isEqualTo(now);assertThat(all.items().getFirst().status()).isEqualTo("completed");
  assertThat(r.list(scope,"pickup","","expired",null,null,0,20,now).items()).singleElement().satisfies(x->assertThat(x.id()).isEqualTo(pending));
  assertThat(r.list(scope,"pickup","","completed",now,now.plusSeconds(1),0,20,now).items()).hasSize(1);
 }
 @Test void requestedOrCancelledQuantitiesDoNotBecomeActualReceipts(){
  UUID app=UUID.randomUUID(),line=UUID.randomUUID();jdbc.update("insert into wine_storage_applications(id,tenant_id,store_id,customer_id,status,original,created_at,updated_at) values(?,?,?,?,'pending','{}',?,?)",app,tenant,store,customer,Timestamp.from(now),Timestamp.from(now));
  jdbc.update("insert into wine_storage_application_lines(id,tenant_id,store_id,customer_id,application_id,original) values(?,?,?,?,?,?::jsonb)",line,tenant,store,customer,app,"{\"nameZh\":\"待核实酒品\",\"quantity\":3}");
  var r=new StorageReportRepository(jdbc);var pending=r.list(scope,"deposit","待核实","pending",now,now.plusSeconds(1),0,20,now);assertThat(pending.items()).singleElement().satisfies(i->assertThat(i.quantity()).isEqualTo(3));assertThat(pending.summary().bottleCount()).isZero();assertThat(pending.summary().completedCount()).isZero();
  jdbc.update("update wine_storage_applications set status='cancelled' where id=?",app);assertThat(r.list(scope,"deposit","","cancelled",null,null,0,20,now).summary().bottleCount()).isZero();
 }
}
