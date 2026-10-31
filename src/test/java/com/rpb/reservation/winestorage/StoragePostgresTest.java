package com.rpb.reservation.winestorage;

import com.rpb.reservation.catalog.CatalogPostgresTest;
import com.rpb.reservation.catalog.application.*;
import com.rpb.reservation.catalog.application.CatalogDtos.Listing;
import com.rpb.reservation.catalog.persistence.CatalogRepository;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.winestorage.application.*;
import com.rpb.reservation.winestorage.domain.StorageException;
import com.rpb.reservation.winestorage.persistence.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;

class StoragePostgresTest extends CatalogPostgresTest {
 StorageRepository r;StorageApplicationService app;StorageWithdrawalService withdrawals;StoragePhotoService photos;StorageProductCatalog storageCatalog;StorageAccess access;
 CustomerAuthPrincipal principal;Rule rule;MutableClock clock;
 static class MutableClock extends Clock {volatile Instant now=Instant.parse("2026-01-31T13:30:00Z");public Instant instant(){return now;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}}
 @BeforeEach void storageFixture(){
  clock=new MutableClock();r=new StorageRepository(jdbc,json);var commands=new StorageCommands(r);access=mock(StorageAccess.class);when(access.timezone(scope)).thenReturn("Asia/Singapore");when(access.enabled(scope)).thenReturn(true);doCallRealMethod().when(access).own(any(),any());
  principal=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),customer,"test@example.com","顾客");storageCatalog=new StorageProductCatalog(new CatalogRepository(jdbc),products,jdbc);
  photos=new StoragePhotoService(access,r);app=new StorageApplicationService(access,r,commands,storageCatalog,photos,clock,custodians(),locations());withdrawals=new StorageWithdrawalService(access,r,commands,clock);
  product=tx.execute(s->catalog.listing(scope,actor,product.id(),new Listing(500,List.of("cards","storage"),true,product.listingVersion(),key())));
  rule=tx.execute(s->app.rule(scope,actor,new RuleWrite(product.categoryId(),"自然月",3,key())));
 }
 UUID photo(boolean receipt)throws Exception {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return tx.execute(s->receipt?photos.upload(scope,actor,customer,"receipt","image/png",out.toByteArray()):photos.upload(scope,principal,"image/png",out.toByteArray())).id();}
 Apply command(int qty,boolean opened)throws Exception{return new Apply(List.of(new Line(null,product.id(),"","",qty,opened,"customer",List.of(photo(false)),"原申请")),true,null,null,key());}
 Application apply(int qty,boolean opened)throws Exception {var c=command(qty,opened);return tx.execute(s->app.apply(scope,principal,c));}
 Application receive(Application original,int qty,boolean opened)throws Exception{
  var c=new Receive(List.of(new ReceiptLine(original.lines().getFirst().id(),product.id(),null,"","",qty,opened,"customer",rule.id(),List.of(photo(true)),"现场核对","A1","L1")),"现场核对后确认",key());return tx.execute(s->app.receive(scope,actor,original.id(),c));
 }
 Item stored(int qty,boolean opened)throws Exception {var a=apply(qty,opened);receive(a,qty,opened);return r.items(scope,customer,clock.instant()).stream().filter(i->i.applicationId().equals(a.id())).findFirst().orElseThrow();}
 Intent intent(Item item,int qty){return tx.execute(s->withdrawals.create(scope,principal,new IntentWrite(null,List.of(new Selection(item.id(),qty)),key())));}
 Intent deliver(Intent intent,String key){return tx.execute(s->withdrawals.deliver(scope,actor,intent.id(),new Key(key,"现场交付")));}
 StorageSettingsService settings(){return new StorageSettingsService(access,r,new StorageCommands(r));}
 void directMode(){tx.execute(s->settings().save(scope,actor,new SettingsWrite("staff_registration",r.settings(scope).version(),key())));}
 @Test void directModeDefaultsSettingsIsolationVersionsAndReplay(){
  assertThat(r.settings(scope).operationMode()).isEqualTo("customer_verification");
  var c=new SettingsWrite("staff_registration",0,key());var saved=tx.execute(s->settings().save(scope,actor,c));
  Settings replay=tx.execute(s->settings().save(scope,actor,c));assertThat(replay).isEqualTo(saved);
  assertThat(r.settings(new StoreScope(new TenantId(tenant),UUID.randomUUID())).operationMode()).isEqualTo("customer_verification");
  assertThat(r.settings(new StoreScope(new TenantId(UUID.randomUUID()),store)).operationMode()).isEqualTo("customer_verification");
  assertThatThrownBy(()->tx.execute(s->settings().save(scope,actor,new SettingsWrite("customer_verification",0,key())))).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->tx.execute(s->settings().save(scope,actor,new SettingsWrite("invalid",1,key())))).isInstanceOf(StorageException.class);
  var staff=new com.rpb.reservation.walkin.api.CurrentActor(tenant,UUID.randomUUID(),"store_staff",Set.of("store_staff"),Set.of("wine_storage.rules.manage"),Set.of(store));
  assertThatThrownBy(()->tx.execute(s->settings().save(scope,staff,new SettingsWrite("customer_verification",1,key())))).isInstanceOf(StorageException.class);
  doThrow(StorageException.forbidden()).when(access).staff(scope,actor,"wine_storage.rules.manage",true);
  assertThatThrownBy(()->tx.execute(s->settings().save(scope,actor,new SettingsWrite("customer_verification",1,key())))).isInstanceOf(StorageException.class);
 }
 @Test void directDeliveryRequiresModeAndReplaysEvenAfterModeSwitch()throws Exception{
  var item=stored(4,false);var c=new IntentWrite(customer,List.of(new Selection(item.id(),2)),key());
  assertThatThrownBy(()->tx.execute(s->withdrawals.directDeliver(scope,actor,c))).isInstanceOf(StorageException.class);
  directMode();var result=tx.execute(s->withdrawals.directDeliver(scope,actor,c));
  assertThat(result.status()).isEqualTo("completed");assertThat(result.code()).isNull();
  assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isEqualTo(2);
  assertThat(r.events(scope,customer)).anyMatch(e->e.action().equals("staff.delivery_registered")&&e.actorId().equals(actor.actorId())).noneMatch(e->e.action().equals("authorized"));
  tx.execute(s->settings().save(scope,actor,new SettingsWrite("customer_verification",1,key())));
  Intent replay=tx.execute(s->withdrawals.directDeliver(scope,actor,c));assertThat(replay).isEqualTo(result);
  assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isEqualTo(2);
  assertThatThrownBy(()->tx.execute(s->withdrawals.directDeliver(scope,actor,new IntentWrite(customer,c.selections(),key())))).isInstanceOf(StorageException.class);
 }
 @Test void directDeliveryRejectsExpiredOverdrawForeignCustomerAndDeniedStaff()throws Exception{
  var item=stored(2,false);directMode();
  assertThatThrownBy(()->tx.execute(s->withdrawals.create(scope,principal,new IntentWrite(null,List.of(new Selection(item.id(),1)),key())))).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->tx.execute(s->withdrawals.directDeliver(scope,actor,new IntentWrite(UUID.randomUUID(),List.of(new Selection(item.id(),1)),key())))).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->tx.execute(s->withdrawals.directDeliver(scope,actor,new IntentWrite(customer,List.of(new Selection(item.id(),3)),key())))).isInstanceOf(StorageException.class);
  clock.now=item.expiresAt();
  assertThatThrownBy(()->tx.execute(s->withdrawals.directDeliver(scope,actor,new IntentWrite(customer,List.of(new Selection(item.id(),1)),key())))).isInstanceOf(StorageException.class);
  assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isEqualTo(2);
  doThrow(StorageException.forbidden()).when(access).staff(scope,actor,"wine_storage.deliver",false);
  assertThatThrownBy(()->tx.execute(s->withdrawals.directDeliver(scope,actor,new IntentWrite(customer,List.of(new Selection(item.id(),1)),key())))).isInstanceOf(StorageException.class);
 }
 @Test void directDeliveryBatchRollbackAndConcurrentCommandsNeverOverdraw()throws Exception{
  var item=stored(3,false);var other=stored(2,false);var pending=tx.execute(s->withdrawals.create(scope,actor,new IntentWrite(customer,List.of(new Selection(item.id(),1)),key())));directMode();
  assertThatThrownBy(()->deliver(pending,key())).hasMessageContaining("授权");
  assertThatThrownBy(()->tx.execute(s->withdrawals.directDeliver(scope,actor,new IntentWrite(customer,List.of(new Selection(item.id(),1),new Selection(other.id(),3)),key())))).isInstanceOf(StorageException.class);
  assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isEqualTo(3);
  int before=r.intents(scope,customer).size();
  assertThatThrownBy(()->tx.execute(s->app.apply(scope,principal,new Apply(List.of(new Line(null,product.id(),"","",1,false,"customer",List.of(),"")),true,null,null,key())))).isInstanceOf(StorageException.class);
  try(var pool=Executors.newFixedThreadPool(2)){
   var start=new CountDownLatch(1);Callable<Boolean> task=()->{start.await();try{tx.execute(s->withdrawals.directDeliver(scope,actor,new IntentWrite(customer,List.of(new Selection(item.id(),2)),key())));return true;}catch(StorageException e){return false;}};
   var one=pool.submit(task);var two=pool.submit(task);start.countDown();assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
  }
  assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isEqualTo(1);assertThat(r.intents(scope,customer)).hasSize(before+1);
 }
 @Test void applicationReceiptAndRedepositCanOmitPhotos()throws Exception{
  var c=new Apply(List.of(new Line(null,product.id(),"","",2,false,"customer",List.of(),"无照片申请")),true,null,null,key());
  var pending=tx.execute(s->app.apply(scope,principal,c));
  var receipt=new Receive(List.of(new ReceiptLine(pending.lines().getFirst().id(),product.id(),product.categoryId(),"","",2,false,"customer",rule.id(),List.of(),"","","")),"已核对实物，无照片",key());
  var received=tx.execute(s->app.receive(scope,actor,pending.id(),receipt));assertThat(received.status()).isEqualTo("stored");
  Application replay=tx.execute(s->app.receive(scope,actor,pending.id(),receipt));assertThat(replay).isEqualTo(received);
  var item=r.items(scope,customer,clock.instant()).getFirst();assertThat(item.snapshot().applicationPhotos()).isEmpty();assertThat(item.snapshot().receiptPhotos()).isEmpty();
  assertThat(r.rows(scope,"select photo_id from wine_storage_photo_links where tenant_id=? and store_id=? and customer_id=?",customer)).isEmpty();
  var delivered=intent(item,1);deliver(delivered,key());
  var again=new Apply(List.of(new Line(null,product.id(),"","",1,false,"customer",List.of(),"无照片再次寄存")),true,delivered.id(),item.id(),key());
  var redeposit=tx.execute(s->app.apply(scope,principal,again));assertThat(redeposit.status()).isEqualTo("pending");
 }
 @Test void staffRegistrationWithoutPhotosRetainsAttestationRulesAndIdempotency(){
  var line=new ReceiptLine(null,product.id(),product.categoryId(),"","",2,false,"customer",rule.id(),List.of(),"","","");
  var c=new Registration(customer,List.of(line),"确认实物",true,key());
  var result=tx.execute(s->registrations().register(scope,actor,c));Application replay=tx.execute(s->registrations().register(scope,actor,c));assertThat(replay).isEqualTo(result);
  assertThat(r.items(scope,customer,clock.instant())).hasSize(1);assertThat(r.items(scope,customer,clock.instant()).getFirst().snapshot().receiptPhotos()).isEmpty();
  assertThatThrownBy(()->tx.execute(s->registrations().register(scope,actor,new Registration(customer,List.of(line),"确认实物",false,key())))).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->tx.execute(s->app.addPhotos(scope,actor,r.items(scope,customer,clock.instant()).getFirst().id(),new PhotoAdd(List.of(),"",key())))).isInstanceOf(StorageException.class);
 }
 @Test void pendingConfirmRejectCancelAndImmutableSnapshots()throws Exception{
  var c=command(6,false);var pending=tx.execute(s->app.apply(scope,principal,c));assertThat(r.items(scope,customer,clock.instant())).isEmpty();
  assertThat(tx.execute(s->app.apply(scope,principal,c)).id()).isEqualTo(pending.id());receive(pending,5,false);
  var item=r.items(scope,customer,clock.instant()).getFirst();assertThat(item.remaining()).isEqualTo(5);assertThat(item.expiresAt()).isEqualTo(Instant.parse("2026-04-30T13:30:00Z"));
  assertThat(r.application(scope,pending.id(),false).lines().getFirst().quantity()).isEqualTo(6);
  tx.execute(s->app.rule(scope,actor,new RuleWrite(product.categoryId(),"新规则",6,key())));assertThat(r.item(scope,item.id(),false,clock.instant()).snapshot().rule().months()).isEqualTo(3);
  var rename=new com.rpb.reservation.catalog.application.CatalogDtos.ProductWrite("P",product.categoryId(),"新名称","Renamed","","","physical","瓶","",500,"inclusive","taxable",new java.math.BigDecimal("0.10"),List.of("storage","cards"),product.version(),key());
  tx.execute(s->catalog.save(scope,actor,product.id(),rename));assertThat(r.item(scope,item.id(),false,clock.instant()).snapshot().nameEn()).isEqualTo("Coffee");
  var cancelled=apply(1,false);assertThat(tx.execute(s->app.cancel(scope,principal,cancelled.id(),new Key(key(),"撤销"))).status()).isEqualTo("cancelled");
  var rejected=apply(1,false);assertThat(tx.execute(s->app.reject(scope,actor,rejected.id(),new Key(key(),"未见实物"))).status()).isEqualTo("rejected");
  assertThatThrownBy(()->tx.execute(s->app.receive(scope,actor,rejected.id(),new Receive(List.of(),"核对",key())))).isInstanceOf(StorageException.class);
 }
 @Test void partialOpenedBatchAndReplay()throws Exception{
  var whole=stored(6,false);var opened=stored(1,true);var c=new IntentWrite(null,List.of(new Selection(whole.id(),2),new Selection(opened.id(),1)),key());
  var intent=tx.execute(s->withdrawals.create(scope,principal,c));assertThat(withdrawals.scan(scope,actor,intent.code()).selections()).hasSize(2);String retry=key();deliver(intent,retry);deliver(intent,retry);
  assertThat(r.item(scope,whole.id(),false,clock.instant()).remaining()).isEqualTo(4);assertThat(r.item(scope,opened.id(),false,clock.instant()).remaining()).isZero();
  assertThat(r.item(scope,whole.id(),false,clock.instant()).expiresAt()).isEqualTo(whole.expiresAt());
  assertThatThrownBy(()->deliver(intent,key())).isInstanceOf(StorageException.class);
  assertThat(r.events(scope,customer).stream().filter(e->e.action().equals("delivered"))).hasSize(2);
 }
 @Test void preExpiryIntentCannotDeliverAtExpiryAndBatchRollsBack()throws Exception{
  var first=stored(3,false);clock.now=clock.now.plusSeconds(60);var second=stored(3,false);
  clock.now=first.expiresAt().minusSeconds(1);var i=tx.execute(s->withdrawals.create(scope,principal,new IntentWrite(null,List.of(new Selection(first.id(),1),new Selection(second.id(),1)),key())));
  clock.now=first.expiresAt();assertThatThrownBy(()->deliver(i,key())).isInstanceOfSatisfying(StorageException.class,e->assertThat(e.problems().contains(new Problem(first.id(),"EXPIRED"))).isTrue());
  assertThat(r.items(scope,customer,clock.instant())).allMatch(item->item.remaining()==3);
 }
 @Test void merchantIntentRequiresCustomerAuthorizationAndExpires()throws Exception{
  var item=stored(2,false);var i=tx.execute(s->withdrawals.create(scope,actor,new IntentWrite(customer,List.of(new Selection(item.id(),1)),key())));
  assertThatThrownBy(()->deliver(i,key())).hasMessageContaining("授权");assertThat(withdrawals.scan(scope,principal,i.code()).id()).isEqualTo(i.id());
  tx.execute(s->withdrawals.authorize(scope,principal,i.id(),new Authorize(i.code(),key())));deliver(i,key());
  var expired=intent(item,1);clock.now=clock.now.plusSeconds(300);assertThatThrownBy(()->deliver(expired,key())).hasMessageContaining("超时");
  assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isEqualTo(1);
 }
 @Test void simultaneousIntentsNeverOverdraw()throws Exception{
  var item=stored(3,false);var one=intent(item,2);var two=intent(item,2);
  try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);Callable<Boolean> a=()->{start.await();try{deliver(one,key());return true;}catch(StorageException e){return false;}};Callable<Boolean> b=()->{start.await();try{deliver(two,key());return true;}catch(StorageException e){return false;}};var x=pool.submit(a);var y=pool.submit(b);start.countDown();assertThat(List.of(x.get(15,TimeUnit.SECONDS),y.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);}
  assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isEqualTo(1);
 }
 @Test void suggestionsArePrivateUnpricedAndTenantStoreCustomerPhotosAreIsolated()throws Exception{
  UUID img=photo(false);var c=new Apply(List.of(new Line(null,null,"茅台","Moutai",1,false,"customer",List.of(img),"")),true,null,null,key());var pending=tx.execute(s->app.apply(scope,principal,c));
  assertThat(storageCatalog.search(scope,"Moutai",false)).isEmpty();
  var rc=new Receive(List.of(new ReceiptLine(pending.lines().getFirst().id(),null,product.categoryId(),"茅台","Moutai",1,false,"customer",rule.id(),List.of(photo(true)),"","","")),"新酒审核",key());
  var accepted=tx.execute(s->app.receive(scope,actor,pending.id(),rc));Application replay=tx.execute(s->app.receive(scope,actor,pending.id(),rc));assertThat(replay).isEqualTo(accepted);
  assertThatThrownBy(()->tx.execute(s->app.receive(scope,actor,pending.id(),new Receive(rc.lines(),rc.reason(),key())))).isInstanceOf(StorageException.class);
  var created=storageCatalog.search(scope,"Moutai",false).getFirst();assertThat(created.priceCents()).isNull();assertThat(created.channels()).containsExactly("storage");
  assertThat(storageCatalog.search(scope,"茅台",false)).hasSize(1);assertThat(storageCatalog.search(scope,created.code(),false)).hasSize(1);assertThatThrownBy(()->products.requireUsable(scope,created.id(),"cards")).isInstanceOf(RuntimeException.class);
  var other=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),UUID.randomUUID(),"other","Other");assertThatThrownBy(()->photos.image(scope,other,img)).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->r.application(new StoreScope(new TenantId(UUID.randomUUID()),store),pending.id(),false)).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->r.application(new StoreScope(new TenantId(tenant),UUID.randomUUID()),pending.id(),false)).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->tx.execute(s->app.cancel(scope,other,pending.id(),new Key(key(),"")))) .isInstanceOf(StorageException.class);
 }
 @Test void delistingDoesNotBlockDeliveryAndRedepositCreatesNewPending()throws Exception{
  var item=stored(3,false);var i=intent(item,2);deliver(i,key());
  var c=new Apply(List.of(new Line(null,product.id(),"","",2,false,"customer",List.of(photo(false)),"重新收存")),true,i.id(),item.id(),key());
  var again=tx.execute(s->app.apply(scope,principal,c));assertThat(again.id()).isNotEqualTo(item.applicationId());assertThat(again.status()).isEqualTo("pending");
  assertThatThrownBy(()->tx.execute(s->app.apply(scope,principal,new Apply(c.lines(),true,i.id(),item.id(),key())))).hasMessageContaining("超过");
  tx.execute(s->catalog.status(scope,actor,product.id(),new com.rpb.reservation.catalog.application.CatalogDtos.Status("inactive",product.version(),key())));
  deliver(intent(item,1),key());assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isZero();
 }
 @Test void changedQuantityCancelsAndForeignAuthorizationDoNotDebit()throws Exception{
  var first=stored(2,false);var second=stored(2,false);
  var batch=tx.execute(s->withdrawals.create(scope,principal,new IntentWrite(null,List.of(new Selection(first.id(),2),new Selection(second.id(),2)),key())));
  deliver(intent(second,1),key());assertThatThrownBy(()->deliver(batch,key())).isInstanceOf(StorageException.class);
  assertThat(r.item(scope,first.id(),false,clock.instant()).remaining()).isEqualTo(2);
  var cancelled=intent(first,1);tx.execute(s->withdrawals.cancel(scope,principal,cancelled.id(),new Key(key(),"取消")));assertThatThrownBy(()->deliver(cancelled,key())).isInstanceOf(StorageException.class);
  var merchant=tx.execute(s->withdrawals.create(scope,actor,new IntentWrite(customer,List.of(new Selection(first.id(),1)),key())));
  var other=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),UUID.randomUUID(),"other","Other");assertThatThrownBy(()->tx.execute(s->withdrawals.authorize(scope,other,merchant.id(),new Authorize(merchant.code(),key())))).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->withdrawals.scan(scope,actor,"WI."+"a".repeat(43))).isInstanceOf(StorageException.class);
  assertThat(r.item(scope,first.id(),false,clock.instant()).remaining()).isEqualTo(2);
 }
 @Test void concurrentRetryOfSameHandoverReturnsOneCommittedResult()throws Exception{
  var item=stored(3,false);var intent=intent(item,2);String sameKey=key();
  try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);Callable<Intent> attempt=()->{start.await();return deliver(intent,sameKey);};var one=pool.submit(attempt);var two=pool.submit(attempt);start.countDown();assertThat(one.get(15,TimeUnit.SECONDS)).isEqualTo(two.get(15,TimeUnit.SECONDS));}
  assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isEqualTo(1);assertThat(r.events(scope,customer).stream().filter(e->e.action().equals("delivered"))).hasSize(1);
 }
 @Test void verifiedRedepositQuantityDoesNotConsumeBottlesThatWereNotReceivedAgain()throws Exception{
  var item=stored(3,false);var pickup=intent(item,2);deliver(pickup,key());
  var c=new Apply(List.of(new Line(null,product.id(),"","",2,false,"customer",List.of(photo(false)),"")),true,pickup.id(),item.id(),key());
  var pending=tx.execute(s->app.apply(scope,principal,c));receive(pending,1,false);
  var next=new Apply(List.of(new Line(null,product.id(),"","",1,false,"customer",List.of(photo(false)),"")),true,pickup.id(),item.id(),key());
  assertThat(tx.execute(s->app.apply(scope,principal,next)).status()).isEqualTo("pending");
 }
 @Test void equivalentObjectKeyOrderReplaysTheOriginalCommittedCommand(){
  var first=new LinkedHashMap<String,Object>();first.put("quantity",1);first.put("item","one");var reordered=new LinkedHashMap<String,Object>();reordered.put("item","one");reordered.put("quantity",1);
  var commands=new StorageCommands(r);String key=key();String original=tx.execute(s->commands.run(scope,actor.actorId(),"staff","canonical-test",key,first,String.class,()->"committed"));
  String replay=tx.execute(s->commands.run(scope,actor.actorId(),"staff","canonical-test",key,reordered,String.class,()->"must-not-execute"));assertThat(replay).isEqualTo(original);
  assertThatThrownBy(()->tx.execute(s->commands.run(scope,actor.actorId(),"staff","canonical-test",key,Map.of("item","one","quantity",2),String.class,()->"must-not-execute"))).isInstanceOf(StorageException.class).hasMessageContaining("幂等键");
 }
 @Test void wrongPhotoPhaseReusedEvidenceAndOpenedQuantitiesAreRejected()throws Exception{
  var invalid=command(2,true);assertThatThrownBy(()->tx.execute(s->app.apply(scope,principal,invalid))).isInstanceOf(StorageException.class);
  UUID receipt=photo(true);var wrong=new Apply(List.of(new Line(null,product.id(),"","",1,false,"customer",List.of(receipt),"")),true,null,null,key());assertThatThrownBy(()->tx.execute(s->app.apply(scope,principal,wrong))).isInstanceOf(StorageException.class);
  var original=command(1,false);tx.execute(s->app.apply(scope,principal,original));assertThatThrownBy(()->tx.execute(s->app.apply(scope,principal,new Apply(original.lines(),true,null,null,key())))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->tx.execute(s->photos.upload(scope,principal,"image/png",new byte[]{1,2,3}))).isInstanceOf(StorageException.class);
 }
 @Test void newNameCanBeLinkedAndCorrectionsRemainAppendOnly()throws Exception{
  var c=new Apply(List.of(new Line(null,null,"咖啡","Coffee",1,false,"customer",List.of(photo(false)),"用户原文")),true,null,null,key());var a=tx.execute(s->app.apply(scope,principal,c));receive(a,1,false);
  assertThat(r.rows(scope,"select status,product_id from wine_storage_suggestions where tenant_id=? and store_id=? and line_id=?",a.lines().getFirst().id()).getFirst().get("status")).isEqualTo("linked");
  var item=r.items(scope,customer,clock.instant()).getFirst();var event=tx.execute(s->app.correct(scope,actor,item.id(),new Correction("标签误写","实际标签 A2",key())));assertThat(event.action()).isEqualTo("correction");assertThat(r.item(scope,item.id(),false,clock.instant())).isEqualTo(item);
  for(String table:List.of("wallet_accounts","wallet_entries","loyalty_accounts","loyalty_entries","customer_cards","card_entries","card_purchase_orders","card_payment_links")){
   assertThat(jdbc.queryForObject("select count(*) from "+table+" where tenant_id=?",Integer.class,tenant)).isZero();
  }
 }

 Registration registration(UUID photo,String key){return new Registration(customer,List.of(new ReceiptLine(null,product.id(),product.categoryId(),"","",2,false,"customer",rule.id(),List.of(photo),"代登记","A1","STAFF")),"已核对实物并向顾客说明规则",true,key);}
 StorageRegistrationService registrations(){return new StorageRegistrationService(access,r,new StorageCommands(r),app,clock);}
 @Test void staffRegistrationStoresAtomicallyAndReplaysWithoutImpersonatingCustomer()throws Exception{
  var c=registration(photo(true),key());var service=registrations();var result=tx.execute(s->service.register(scope,actor,c));
  assertThat(result.status()).isEqualTo("stored");assertThat(result.original().rulesAccepted()).isFalse();assertThat(result.lines().getFirst().photos()).isEmpty();
  var items=r.items(scope,customer,clock.instant());assertThat(items).hasSize(1);assertThat(items.getFirst().remaining()).isEqualTo(2);assertThat(items.getFirst().snapshot().receiptPhotos()).isEqualTo(c.lines().getFirst().photos());
  Application replay=tx.execute(s->service.register(scope,actor,c));assertThat(replay).isEqualTo(result);assertThat(r.items(scope,customer,clock.instant())).hasSize(1);
  assertThat(r.events(scope,customer)).anySatisfy(e->{assertThat(e.action()).isEqualTo("staff.registered");assertThat(e.actorId()).isEqualTo(actor.actorId());assertThat(e.details()).containsEntry("rulesConfirmed",true);});
  verify(access,atLeastOnce()).staff(scope,actor,"wine_storage.receive",true);verify(access,atLeastOnce()).customerId(scope,customer);
  assertThatThrownBy(()->tx.execute(s->service.register(scope,actor,new Registration(customer,c.lines(),"不同内容",true,c.idempotencyKey())))).isInstanceOf(StorageException.class);
 }
 @Test void staffRegistrationRequiresAttestationAndRollsBackInvalidEvidence()throws Exception{
  var service=registrations();var c=registration(photo(false),key());
  assertThatThrownBy(()->tx.execute(s->service.register(scope,actor,new Registration(customer,c.lines(),c.reason(),false,key())))).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->tx.execute(s->service.register(scope,actor,c))).isInstanceOf(StorageException.class);
  assertThat(r.items(scope,customer,clock.instant())).isEmpty();assertThat(r.applications(scope,customer)).isEmpty();assertThat(r.events(scope,customer)).isEmpty();
  var good=registration(photo(true),key());var line=good.lines().getFirst();var bad=new ReceiptLine(null,line.productId(),line.categoryId(),line.nameZh(),line.nameEn(),line.quantity(),line.opened(),line.source(),UUID.randomUUID(),line.photos(),line.note(),line.location(),line.label());
  assertThatThrownBy(()->tx.execute(s->service.register(scope,actor,new Registration(customer,List.of(bad),good.reason(),true,key())))).isInstanceOf(StorageException.class);
  assertThat(r.applications(scope,customer)).isEmpty();assertThat(r.events(scope,customer)).isEmpty();
  // A failed transaction must leave the receipt photo available for the corrected command.
  Application corrected=tx.execute(s->service.register(scope,actor,good));assertThat(corrected.status()).isEqualTo("stored");

 }
 @Test void staffRegistrationRejectsWrongCustomerAndDeniedAccessBeforeWriting()throws Exception{
  var service=registrations();var c=registration(photo(true),key());
  doThrow(StorageException.missing()).when(access).customerId(scope,customer);
  assertThatThrownBy(()->tx.execute(s->service.register(scope,actor,c))).isInstanceOf(StorageException.class);
  doThrow(StorageException.forbidden()).when(access).staff(scope,actor,"wine_storage.receive",true);
  assertThatThrownBy(()->tx.execute(s->service.register(scope,actor,c))).isInstanceOf(StorageException.class);
  assertThat(r.applications(scope,customer)).isEmpty();assertThat(r.items(scope,customer,clock.instant())).isEmpty();
 }

 StorageCustodianService custodians(){return new StorageCustodianService(access,r,new StorageCommands(r));}
 StorageLocationService locations(){return new StorageLocationService(access,r,new StorageCommands(r));}
 @Test void locationsAreReadOnlyByDefaultAndNamesAreScopedVersionedAndIdempotent(){
  var service=locations();var initial=service.list(scope,actor);assertThat(initial).hasSize(1);var front=initial.getFirst();assertThat(front.name()).isEqualTo("前台寄存处");assertThat(front.defaultLocation()).isTrue();
  assertThat(jdbc.queryForObject("select count(*) from wine_storage_locations where tenant_id=?",Integer.class,tenant)).isZero();
  var body=new LocationWrite("酒柜 A",null,key());var created=tx.execute(s->service.save(scope,actor,null,body));StorageLocation replay=tx.execute(s->service.save(scope,actor,null,body));assertThat(replay).isEqualTo(created);
  var renamed=tx.execute(s->service.save(scope,actor,front.id(),new LocationWrite("前台储藏室",0,key())));assertThat(renamed.version()).isEqualTo(1);
  assertThatThrownBy(()->tx.execute(s->service.save(scope,actor,front.id(),new LocationWrite("旧版本",0,key())))).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->tx.execute(s->service.save(scope,actor,null,new LocationWrite(" 酒柜 A ",null,key())))).isInstanceOf(StorageException.class);
  var foreign=new StoreScope(new TenantId(UUID.randomUUID()),store);assertThatThrownBy(()->tx.execute(s->service.resolve(foreign,created.id()))).isInstanceOf(StorageException.class);
  var other=new StoreScope(new TenantId(tenant),UUID.randomUUID());assertThatThrownBy(()->tx.execute(s->service.resolve(other,front.id()))).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->tx.execute(s->service.save(other,actor,front.id(),new LocationWrite("前台寄存处",0,key())))).isInstanceOfSatisfying(StorageException.class,e->assertThat(e.status()).isEqualTo(404));
  for(String bad:List.of(" ","A".repeat(101)))assertThatThrownBy(()->tx.execute(s->service.save(scope,actor,null,new LocationWrite(bad,null,key())))).isInstanceOf(StorageException.class);
  var staff=new com.rpb.reservation.walkin.api.CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("store_staff"),Set.of("wine_storage.rules.manage"),Set.of(store));
  assertThatThrownBy(()->tx.execute(s->service.save(scope,staff,null,new LocationWrite("越权",null,key())))).isInstanceOf(StorageException.class);
  doThrow(StorageException.forbidden()).when(access).staff(scope,actor,"wine_storage.rules.manage",true);
  assertThatThrownBy(()->tx.execute(s->service.save(scope,actor,null,new LocationWrite("停用",null,key())))).isInstanceOf(StorageException.class);
 }
 @Test void optionalStorageLocationAndSlotAreFrozenOnReceiptAndRegistration()throws Exception{
  var service=locations();var front=service.list(scope,actor).getFirst();var pending=apply(1,false);
  var line=new ReceiptLine(pending.lines().getFirst().id(),product.id(),product.categoryId(),"","",1,false,"customer",rule.id(),List.of(photo(true)),"","","",front.id(),new LocationRef(front.id(),"客户端伪造名称"));
  var accepted=tx.execute(s->app.receive(scope,actor,pending.id(),new Receive(List.of(line),"已核实",key())));
  var item=r.items(scope,customer,clock.instant()).getFirst();assertThat(item.location()).isEmpty();assertThat(item.snapshot().storageLocation().name()).isEqualTo("前台寄存处");assertThat(accepted.confirmation().lines().getFirst().storageLocation().name()).isEqualTo("前台寄存处");
  tx.execute(s->service.save(scope,actor,front.id(),new LocationWrite("新位置名",0,key())));
  assertThat(r.item(scope,item.id(),false,clock.instant()).snapshot().storageLocation().name()).isEqualTo("前台寄存处");
  var registrationLine=new ReceiptLine(UUID.randomUUID(),product.id(),product.categoryId(),"","",1,false,"customer",rule.id(),List.of(photo(true)),"","A-08","",front.id());
  tx.execute(s->registrations().register(scope,actor,new Registration(customer,List.of(registrationLine),"已核实",true,key())));
  assertThat(r.items(scope,customer,clock.instant())).anySatisfy(h->{assertThat(h.location()).isEqualTo("A-08");assertThat(h.snapshot().storageLocation().name()).isEqualTo("新位置名");});
  LocationRef none=tx.execute(s->service.resolve(scope,null));assertThat(none).isNull();
 }
 @Test void custodianSelectionIsOptionalAndHistoricalSnapshotSurvivesRenameDisable()throws Exception{
  var team=custodians();var write=new CustodianWrite("小兰","hostess",true,null,key());var person=tx.execute(s->team.save(scope,actor,null,write));
  assertThat(tx.<Custodian>execute(s->team.save(scope,actor,null,write)).id()).isEqualTo(person.id());
  var c=registration(photo(true),key());var assigned=new Registration(customer,c.lines(),c.reason(),true,c.idempotencyKey(),person.id());
  var result=tx.execute(s->registrations().register(scope,actor,assigned));var item=r.items(scope,customer,clock.instant()).getFirst();assertThat(item.snapshot().custodian().name()).isEqualTo("小兰");
  tx.execute(s->team.save(scope,actor,person.id(),new CustodianWrite("兰姐","hostess",false,person.version(),key())));
  assertThat(team.list(scope,actor,false)).isEmpty();assertThat(team.list(scope,actor,true)).hasSize(1);assertThat(r.item(scope,item.id(),false,clock.instant()).snapshot().custodian().name()).isEqualTo("小兰");
  Application replay=tx.execute(s->registrations().register(scope,actor,assigned));assertThat(replay).isEqualTo(result);
  var fresh=registration(photo(true),key());assertThatThrownBy(()->tx.execute(s->registrations().register(scope,actor,new Registration(customer,fresh.lines(),fresh.reason(),true,fresh.idempotencyKey(),person.id())))).isInstanceOf(StorageException.class);
  assertThat(r.items(scope,customer,clock.instant())).hasSize(1);
 }
 @Test void custodianVersionScopeAndAdministratorBoundariesAreEnforced(){
  var team=custodians();var p=tx.execute(s->team.save(scope,actor,null,new CustodianWrite("保管一号","custodian",true,null,key())));
  assertThatThrownBy(()->tx.execute(s->team.save(scope,actor,p.id(),new CustodianWrite("改名","custodian",true,99,key())))).isInstanceOf(StorageException.class);
  var other=new StoreScope(scope.tenantId(),UUID.randomUUID());assertThatThrownBy(()->tx.execute(s->team.resolve(other,p.id()))).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->tx.execute(s->team.resolve(new StoreScope(new TenantId(UUID.randomUUID()),store),p.id()))).isInstanceOf(StorageException.class);
  var employee=new com.rpb.reservation.walkin.api.CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("staff"),Set.of("wine_storage.custodians.manage"),Set.of(store));assertThatThrownBy(()->tx.execute(s->team.save(scope,employee,null,new CustodianWrite("擅自新增","hostess",true,null,key())))).isInstanceOf(StorageException.class);
 }
 @Test void inboxReturnsOwnStorePendingApplicationsWithCorrectCustomerIdentity()throws Exception{
  var pending=apply(3,false);var done=apply(1,false);receive(done,1,false);clearInvocations(access);var inbox=new StoragePendingQuery(access,r);
  var queue=inbox.list(scope,actor);assertThat(queue).hasSize(1);assertThat(queue.getFirst().application().id()).isEqualTo(pending.id());assertThat(queue.getFirst().customer().id()).isEqualTo(customer);assertThat(queue.getFirst().customer().displayName()).isEqualTo("顾客");
  assertThat(inbox.list(new StoreScope(scope.tenantId(),UUID.randomUUID()),actor)).isEmpty();
  verify(access).staff(scope,actor,"wine_storage.receive",false);
 }

 @Test void existingApplicationReceiptAlsoSupportsOptionalCustodian()throws Exception{
  var person=tx.execute(s->custodians().save(scope,actor,null,new CustodianWrite("保管员A","custodian",true,null,key())));var a=apply(2,false);
  var c=new Receive(List.of(new ReceiptLine(a.lines().getFirst().id(),product.id(),product.categoryId(),"","",2,false,"customer",rule.id(),List.of(photo(true)),"核对","A1","L1")),"确认",key(),person.id());
  Application accepted=tx.execute(s->app.receive(scope,actor,a.id(),c));assertThat(accepted.confirmation().custodianId()).isEqualTo(person.id());assertThat(r.items(scope,customer,clock.instant()).getFirst().snapshot().custodian().kind()).isEqualTo("custodian");
 }

 StorageScanDeliveryService scanDeliveries(){return new StorageScanDeliveryService(access,r,new StorageCommands(r),withdrawals);}
 Intent scanDeliver(Intent intent,String key){return tx.execute(s->scanDeliveries().deliver(scope,actor,new ScanDelivery(intent.code(),key)));}
 @Test void customerCodeScanImmediatelyDeliversOnceAndLostResponseCanBeReplayed()throws Exception{
  var item=stored(5,false);var code=intent(item,2);String retry=key();var done=scanDeliver(code,retry);assertThat(done.status()).isEqualTo("completed");assertThat(done.code()).isNull();assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isEqualTo(3);
  assertThatThrownBy(()->scanDeliver(code,key())).isInstanceOf(StorageException.class);assertThatThrownBy(()->withdrawals.scan(scope,actor,code.code())).isInstanceOf(StorageException.class);
  clock.now=clock.now.plusSeconds(301);assertThat(scanDeliver(code,retry)).isEqualTo(done);assertThat(r.events(scope,customer).stream().filter(e->e.action().equals("delivered"))).hasSize(1);
 }
 @Test void concurrentScansOfOneCustomerCodeCannotDoubleDebit()throws Exception{
  var item=stored(4,false);var code=intent(item,1);
  try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);Callable<Boolean> attempt=()->{start.await();try{scanDeliver(code,key());return true;}catch(StorageException e){return false;}};var a=pool.submit(attempt);var b=pool.submit(attempt);start.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);}
  assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isEqualTo(3);assertThat(r.events(scope,customer).stream().filter(e->e.action().equals("delivered"))).hasSize(1);
 }
 @Test void scanRejectsMerchantOriginCancellationExpiryAndWrongStoreWithoutDeducting()throws Exception{
  var item=stored(5,false);var merchant=tx.execute(s->withdrawals.create(scope,actor,new IntentWrite(customer,List.of(new Selection(item.id(),1)),key())));assertThatThrownBy(()->scanDeliver(merchant,key())).isInstanceOf(StorageException.class);
  var cancelled=intent(item,1);tx.execute(s->withdrawals.cancel(scope,principal,cancelled.id(),new Key(key(),"cancel")));assertThatThrownBy(()->scanDeliver(cancelled,key())).isInstanceOf(StorageException.class);
  var expired=intent(item,1);clock.now=expired.expiresAt();assertThatThrownBy(()->scanDeliver(expired,key())).isInstanceOf(StorageException.class);
  var other=intent(item,1);assertThatThrownBy(()->tx.execute(s->scanDeliveries().deliver(new StoreScope(new TenantId(UUID.randomUUID()),store),actor,new ScanDelivery(other.code(),key())))).isInstanceOf(StorageException.class);assertThatThrownBy(()->tx.execute(s->scanDeliveries().deliver(new StoreScope(scope.tenantId(),UUID.randomUUID()),actor,new ScanDelivery(other.code(),key())))).isInstanceOf(StorageException.class);
  assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isEqualTo(5);
 }
 @Test void scannedBatchUsesOriginalStockValidationAndRollsBackAllLines()throws Exception{
  var first=stored(2,false);var second=stored(2,false);var batch=tx.execute(s->withdrawals.create(scope,principal,new IntentWrite(null,List.of(new Selection(first.id(),1),new Selection(second.id(),2)),key())));deliver(intent(second,1),key());
  assertThatThrownBy(()->scanDeliver(batch,key())).isInstanceOf(StorageException.class);assertThat(r.item(scope,first.id(),false,clock.instant()).remaining()).isEqualTo(2);assertThat(r.intent(scope,batch.id(),false).status()).isEqualTo("authorized");
 }

 @Test void concurrentRetryOfOneScanCommandReturnsSameCommittedResult()throws Exception{
  var item=stored(3,false);var code=intent(item,1);String same=key();
  try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);Callable<Intent> attempt=()->{start.await();return scanDeliver(code,same);};var a=pool.submit(attempt);var b=pool.submit(attempt);start.countDown();assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));}
  assertThat(r.item(scope,item.id(),false,clock.instant()).remaining()).isEqualTo(2);assertThat(r.events(scope,customer).stream().filter(e->e.action().equals("scan.delivered"))).hasSize(1);
 }
 @Test void closedStorageChannelBlocksNewReceiptsButKeepsExistingBottlesCollectible()throws Exception{
  var existing=stored(2,false);var pending=apply(1,false);
  tx.execute(t->catalog.listing(scope,actor,product.id(),new Listing(500,List.of("cards"),true,product.listingVersion(),key())));
  assertThat(storageCatalog.search(scope,"",true)).isEmpty();
  assertThatThrownBy(()->receive(pending,1,false)).hasMessageContaining("不适用");
  assertThat(r.application(scope,pending.id(),false).status()).isEqualTo("pending");assertThat(r.items(scope,customer,clock.instant())).hasSize(1);
  var registration=registration(photo(true),key());assertThatThrownBy(()->tx.execute(t->registrations().register(scope,actor,registration))).hasMessageContaining("不适用");
  assertThat(r.items(scope,customer,clock.instant())).hasSize(1);deliver(intent(existing,1),key());assertThat(r.item(scope,existing.id(),false,clock.instant()).remaining()).isEqualTo(1);
 }
}
