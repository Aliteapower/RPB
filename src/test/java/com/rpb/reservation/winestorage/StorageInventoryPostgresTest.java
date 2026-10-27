package com.rpb.reservation.winestorage;

import com.rpb.reservation.catalog.CatalogPostgresTest;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.winestorage.persistence.StorageInventoryRepository;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageInventoryPostgresTest extends CatalogPostgresTest {
 final Instant now=Instant.parse("2026-10-27T00:00:00Z");
 UUID add(UUID owner,int quantity,int remaining,boolean opened,Instant expiry,String wine){
  UUID id=UUID.randomUUID(),app=UUID.randomUUID(),line=UUID.randomUUID(),rule=UUID.randomUUID();
  jdbc.update("insert into wine_storage_rules(id,tenant_id,store_id,category_id,name,months,version,created_by) values(?,?,?,?,'规则',6,?,?)",rule,tenant,store,product.categoryId(),jdbc.queryForObject("select count(*)+1 from wine_storage_rules where tenant_id=?",Integer.class,tenant),actor.actorId());
  jdbc.update("insert into wine_storage_applications(id,tenant_id,store_id,customer_id,status,original,created_at,updated_at) values(?,?,?,?,'stored','{}',?,?)",app,tenant,store,owner,Timestamp.from(now.minusSeconds(86400)),Timestamp.from(now));
  jdbc.update("insert into wine_storage_application_lines(id,tenant_id,store_id,customer_id,application_id,original) values(?,?,?,?,?,'{}')",line,tenant,store,owner,app);
  String snapshot="{\"nameZh\":\""+wine+"\",\"nameEn\":\"Wine\",\"storageLocation\":{\"name\":\"前台\"},\"custodian\":{\"name\":\"小兰\"}}";
  jdbc.update("insert into wine_storage_items(id,tenant_id,store_id,customer_id,application_id,line_id,product_id,rule_id,snapshot,quantity,remaining,opened,source,location,label,received_at,expires_at) values(?,?,?,?,?,?,?,?,?::jsonb,?,?,?,'customer','A柜','L1',?,?)",id,tenant,store,owner,app,line,product.id(),rule,snapshot,quantity,remaining,opened,Timestamp.from(now.minusSeconds(86400)),Timestamp.from(expiry));return id;
 }
 @Test void currentInventoryCountsPeopleRemainingOpenedAndExpiredAcrossAllPages(){
  UUID second=UUID.randomUUID();jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,status) values(?,?,'second','regular','李先生','active')",second,tenant);
  add(customer,8,3,false,now.plusSeconds(100),"茅台");add(customer,1,1,true,now,"红酒");add(second,4,2,false,now.minusSeconds(1),"啤酒");add(second,5,0,false,now.plusSeconds(100),"取完");
  var r=new StorageInventoryRepository(jdbc);var result=r.list(scope,"","all",0,1,now);
  assertThat(result.items()).hasSize(1);assertThat(result.hasMore()).isTrue();assertThat(result.summary().customerCount()).isEqualTo(2);assertThat(result.summary().bottleCount()).isEqualTo(6);assertThat(result.summary().openedBottleCount()).isEqualTo(1);assertThat(result.summary().expiredBottleCount()).isEqualTo(3);assertThat(result.summary().itemCount()).isEqualTo(3);
  var expired=r.list(scope,"","expired",0,50,now);assertThat(expired.items()).hasSize(2).allMatch(i->i.status().equals("expired"));assertThat(expired.summary().bottleCount()).isEqualTo(3);
  var stored=r.list(scope,"","stored",0,50,now);assertThat(stored.items()).singleElement().satisfies(i->{assertThat(i.nameZh()).isEqualTo("茅台");assertThat(i.customerName()).isEqualTo("顾客");assertThat(i.locationName()).isEqualTo("前台");assertThat(i.slot()).isEqualTo("A柜");assertThat(i.custodianName()).isEqualTo("小兰");assertThat(i.remaining()).isEqualTo(3);});
  var next=r.list(scope,"","all",1,1,now);assertThat(next.items().getFirst().id()).isNotEqualTo(result.items().getFirst().id());
 }
 @Test void literalSearchIsolationAndDisposedStock(){
  UUID id=add(customer,2,2,false,now.plusSeconds(100),"酒%_品");var r=new StorageInventoryRepository(jdbc);
  for(String q:List.of("%_","顾客","91234567","小兰","前台","A柜","L1","wine"))assertThat(r.list(scope,q,"all",0,50,now).summary().bottleCount()).as(q).isEqualTo(2);
  assertThat(r.list(scope,"不存在","all",0,50,now).items()).isEmpty();assertThat(r.list(new StoreScope(new TenantId(UUID.randomUUID()),store),"","all",0,50,now).summary().bottleCount()).isZero();assertThat(r.list(new StoreScope(new TenantId(tenant),UUID.randomUUID()),"","all",0,50,now).items()).isEmpty();
  jdbc.update("update wine_storage_items set physical_disposition='disposed' where id=?",id);assertThat(r.list(scope,"","all",0,50,now).summary().itemCount()).isZero();
 }
}
