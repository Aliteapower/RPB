package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.domain.*;
import com.rpb.reservation.winestorage.persistence.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;
import static com.rpb.reservation.winestorage.persistence.StorageRepository.at;

@Service
@Transactional(readOnly=true)
public class StorageWithdrawalService {
 private final StorageAccess access;private final StorageRepository r;private final StorageCommands commands;private final Clock clock;
 public StorageWithdrawalService(StorageAccess access,StorageRepository r,StorageCommands commands,Clock clock){this.access=access;this.r=r;this.commands=commands;this.clock=clock;}
 @Transactional public Intent create(StoreScope s,CustomerAuthPrincipal p,IntentWrite c){access.customer(s,p,false);return create(s,p.customerId(),p.customerId(),"customer",c);}
 @Transactional public Intent create(StoreScope s,CurrentActor a,IntentWrite c){access.staff(s,a,"wine_storage.deliver",false);if(c==null)throw StorageException.invalid();access.customerId(s,c.customerId());return create(s,c.customerId(),a.actorId(),"staff",c);}
 @Transactional public Intent directDeliver(StoreScope s,CurrentActor a,IntentWrite c){
  access.staff(s,a,"wine_storage.deliver",false);if(c==null)throw StorageException.invalid();access.customerId(s,c.customerId());
  return commands.run(s,a.actorId(),"staff","direct.deliver",c.idempotencyKey(),c,Intent.class,()->{
   r.lock(s,"settings");if(!r.settings(s).operationMode().equals("staff_registration"))throw StorageException.conflict("本店使用顾客核销模式，请先取得顾客授权");
   var i=create(s,c.customerId(),a.actorId(),"staff",c,true);
   var result=deliver(s,a,i.id(),new Key(c.idempotencyKey(),"商户直接登记实物交付"));
   r.event(s,c.customerId(),i.id(),"staff.delivery_registered",a.actorId(),Map.of("operationMode","staff_registration","selections",c.selections()),clock.instant());
   return result;
  });
 }
 private Intent create(StoreScope s,UUID customer,UUID actor,String origin,IntentWrite c){return create(s,customer,actor,origin,c,false);}
 private Intent create(StoreScope s,UUID customer,UUID actor,String origin,IntentWrite c,boolean direct){
  if(c==null||c.selections()==null||c.selections().isEmpty()||c.selections().size()>50||c.selections().stream().anyMatch(x->x==null||x.itemId()==null||x.quantity()<1)||c.selections().stream().map(Selection::itemId).distinct().count()!=c.selections().size()||c.customerId()!=null&&!c.customerId().equals(customer))throw StorageException.invalid();
  return commands.run(s,actor,origin,"intent",c.idempotencyKey(),c,Intent.class,()->{
   if(!direct)r.requireCustomerMode(s);
   validate(s,customer,c.selections(),false);UUID id=UUID.randomUUID();byte[] random=new byte[32];new SecureRandom().nextBytes(random);String code="WS."+Base64.getUrlEncoder().withoutPadding().encodeToString(random);
   var now=clock.instant();r.update(s,"insert into wine_storage_intents(tenant_id,store_id,id,customer_id,origin,status,code_hash,expires_at,created_at) values(?,?,?,?,?,?,?,?,?)",id,customer,origin,origin.equals("customer")||direct?"authorized":"awaiting_authorization",StorageCommands.digest(code),at(now.plusSeconds(300)),at(now));
   for(var line:c.selections())r.update(s,"insert into wine_storage_intent_lines(tenant_id,store_id,customer_id,intent_id,item_id,quantity) values(?,?,?,?,?,?)",customer,id,line.itemId(),line.quantity());
   r.event(s,customer,id,"intent.created",actor,Map.of("origin",origin,"selections",c.selections(),"operationMode",direct?"staff_registration":"customer_verification"),now);
   var intent=r.intent(s,id,false);return new Intent(intent.id(),intent.customerId(),intent.origin(),intent.status(),intent.expiresAt(),null,intent.selections(),code);
  });
 }
 public Intent scan(StoreScope s,CustomerAuthPrincipal p,String code){access.customer(s,p,false);var i=resolve(s,code);access.own(i.customerId(),p.customerId());if(!i.origin().equals("staff"))throw StorageException.conflict("请扫描商户领取确认码");return i;}
 public Intent scan(StoreScope s,CurrentActor a,String code){access.staff(s,a,"wine_storage.deliver",false);var i=resolve(s,code);if(!i.origin().equals("customer"))throw StorageException.conflict("请扫描顾客领取授权码");return i;}
 private Intent resolve(StoreScope s,String code){if(code==null||!code.matches("WS\\.[A-Za-z0-9_-]{43}"))throw StorageException.invalid();var i=r.byCode(s,StorageCommands.digest(code));live(i);return i;}
 @Transactional public Intent authorize(StoreScope s,CustomerAuthPrincipal p,UUID id,Authorize c){
  access.customer(s,p,false);if(c==null)throw StorageException.invalid();return commands.run(s,p.customerId(),"customer","authorize",c.idempotencyKey(),Map.of("id",id,"command",c),Intent.class,()->{
   var i=r.intent(s,id,true);access.own(i.customerId(),p.customerId());var coded=resolve(s,c.code());if(!coded.id().equals(id)||!i.origin().equals("staff"))throw StorageException.missing();live(i);
   if(!Set.of("awaiting_authorization","authorized").contains(i.status()))throw StorageException.conflict("领取意图已关闭");
   validate(s,p.customerId(),i.selections(),false);r.change(s,"intents",id,"status='authorized'");r.event(s,p.customerId(),id,"authorized",p.customerId(),Map.of("selections",i.selections()),clock.instant());return r.intent(s,id,false);
  });
 }
 @Transactional public Intent cancel(StoreScope s,CustomerAuthPrincipal p,UUID id,Key c){access.customer(s,p,false);return cancel(s,p.customerId(),"customer",p.customerId(),id,c);}
 @Transactional public Intent cancel(StoreScope s,CurrentActor a,UUID id,Key c){access.staff(s,a,"wine_storage.deliver",false);return cancel(s,a.actorId(),"staff",null,id,c);}
 private Intent cancel(StoreScope s,UUID actor,String kind,UUID customer,UUID id,Key c){if(c==null)throw StorageException.invalid();return commands.run(s,actor,kind,"cancel.intent",c.idempotencyKey(),Map.of("id",id,"command",c),Intent.class,()->{
  var i=r.intent(s,id,true);if(customer!=null)access.own(i.customerId(),customer);if(!Set.of("authorized","awaiting_authorization").contains(i.status()))throw StorageException.conflict("领取意图已关闭");r.change(s,"intents",id,"status='cancelled'");r.event(s,i.customerId(),id,"intent.cancelled",actor,Map.of("reason",StorageApplicationService.text(c.reason())),clock.instant());return r.intent(s,id,false);
 });}
 @Transactional public Intent deliver(StoreScope s,CurrentActor a,UUID id,Key c){
  access.staff(s,a,"wine_storage.deliver",false);if(c==null)throw StorageException.invalid();return commands.run(s,a.actorId(),"staff","deliver",c.idempotencyKey(),Map.of("id",id,"command",c),Intent.class,()->{
   var i=r.intent(s,id,true);if(!i.status().equals("authorized"))throw StorageException.conflict("须顾客授权且未完成实际交付");
   // Always lock in UUID order, then read time. Requests never reserve bottles or eligibility.
   var locked=validate(s,i.customerId(),i.selections(),true);live(i);var now=clock.instant();
   // Recheck the expiry at the final mutation boundary as well as after all locks.
   check(locked,i.selections(),now);
   for(var line:i.selections()){
    var item=locked.get(line.itemId());r.change(s,"items",item.id(),"remaining=remaining-?",line.quantity());
    r.event(s,i.customerId(),item.id(),"delivered",a.actorId(),Map.of("intentId",id,"quantity",line.quantity(),"before",item.remaining(),"after",item.remaining()-line.quantity()),now);
   }
   r.change(s,"intents",id,"status='completed',completed_at=?",at(now));return r.intent(s,id,false);
  });
 }
 private Map<UUID,Item> validate(StoreScope s,UUID customer,List<Selection> selections,boolean lock){
  var items=new LinkedHashMap<UUID,Item>();for(var selection:selections.stream().sorted(Comparator.comparing(x->x.itemId().toString())).toList()){
   var item=r.item(s,selection.itemId(),lock,clock.instant());access.own(item.customerId(),customer);items.put(item.id(),item);
  }
  check(items,selections,clock.instant());return items;
 }
 private void check(Map<UUID,Item> items,List<Selection> selections,Instant now){
  List<Problem> problems=new ArrayList<>();for(var selection:selections){var item=items.get(selection.itemId());
   if(!now.isBefore(item.expiresAt()))problems.add(new Problem(item.id(),"EXPIRED"));
   else if(!StorageRules.available(item.remaining(),item.opened(),item.expiresAt(),now,selection.quantity()))problems.add(new Problem(item.id(),"QUANTITY_OR_STATE_CHANGED"));
  }
  if(!problems.isEmpty())throw new StorageException("STORAGE_BATCH_CONFLICT",409,"整批未交付，请调整问题明细后重新确认",problems);
 }
 private void live(Intent i){if(!clock.instant().isBefore(i.expiresAt()))throw StorageException.conflict("领取授权已超时，请重新申请");if(Set.of("cancelled","completed").contains(i.status()))throw StorageException.conflict("领取意图已关闭");}
}
