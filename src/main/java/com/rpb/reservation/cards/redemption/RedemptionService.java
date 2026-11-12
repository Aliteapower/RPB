package com.rpb.reservation.cards.redemption;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.cards.command.CardCommands;
import com.rpb.reservation.cards.plan.CardPlanRepository;
import com.rpb.reservation.cards.entitlement.*;
import com.rpb.reservation.cards.stamp.StampService;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service("cardRedemptionService")
@Transactional(readOnly=true)
public class RedemptionService {
 private final CardAccess access;private final RedemptionRepository requests;private final CardPlanRepository plans;private final CardHoldingRepository cards;private final CardCommands commands;private final StampService stamps;private final Clock clock;
 public RedemptionService(CardAccess access,RedemptionRepository requests,CardPlanRepository plans,CardHoldingRepository cards,CardCommands commands,StampService stamps,Clock clock){this.access=access;this.requests=requests;this.plans=plans;this.cards=cards;this.commands=commands;this.stamps=stamps;this.clock=clock;}
 @Transactional public Presentation present(StoreScope s,CustomerAuthPrincipal p,UUID id,PresentationCreate c){
  access.customer(s,p,true);
  if(c==null||c.quantity()<1||c.quantity()>1000000)throw CardException.invalid();
  return commands.run(s,p.customerId(),"request.present",c.idempotencyKey(),Map.of("card",id,"body",c),Presentation.class,()->{
   var h=cards.get(s,id,true,clock.instant());owner(h,p.customerId());StampService.requireActive(h,clock.instant());
   if(h.kind().equals("stamp")&&c.quantity()!=1)throw CardException.invalid();
   var day=CardRules.day(clock.instant(),h.timezone());
   if(!day.toString().equals(c.businessDay()))throw new CardException("CARD_DAY_CHANGED",409,"业务日期已变化，请重新确认");
   if(CardRules.available(h.remaining(),h.dailyLimit(),cards.used(s,id,day))<c.quantity())throw new CardException("CARD_QUOTA_INSUFFICIENT",409,"剩余次数或今日额度不足");
   var expiry=clock.instant().plusSeconds(120);
   var r=requests.create(s,p.customerId(),new RequestCreate(h.kind().equals("stamp")?"stamp":"redeem",h.planId(),id,c.quantity(),c.idempotencyKey()),expiry);
   String token=CardTokens.issue("CA");requests.authorize(s,r.id(),p.customerId(),id,CardTokens.hash(token,"CA"),day);
   commands.audit(s,p.customerId(),"request.present",Map.of("request",r.id(),"card",id,"quantity",c.quantity()));
   return new Presentation(r.id(),token,expiry);
  });
 }
 @Transactional public Request create(StoreScope s,CurrentActor a,RequestCreate c){if(c==null||c.kind()==null||!Set.of("stamp","redeem").contains(c.kind())||c.quantity()<1||c.quantity()>1000000||c.kind().equals("stamp")&&c.quantity()!=1)throw CardException.invalid();access.staff(s,a,permission(c.kind()),true);return commands.run(s,a.actorId(),"request.create",c.idempotencyKey(),c,Request.class,()->{var p=plans.get(s,c.planId(),false);if(c.kind().equals("stamp")&&!p.kind().equals("stamp"))throw CardException.invalid();if(c.cardId()!=null){var h=cards.get(s,c.cardId(),false,clock.instant());if(!h.planId().equals(p.id()))throw CardException.missing();}return requests.create(s,a.actorId(),c,clock.instant().plusSeconds(120));});}
 @Transactional public Code issue(StoreScope s,CurrentActor a,UUID id){var r=requests.get(s,id,true);access.staff(s,a,permission(r.kind()),true);pending(r);String token=CardTokens.issue("CK");var expiry=clock.instant().plusSeconds(120);requests.issue(s,id,CardTokens.hash(token,"CK"),expiry);return new Code(token,expiry);}
 public Request get(StoreScope s,CurrentActor a,UUID id){var r=requests.get(s,id,false);access.staff(s,a,"wallet.cards.view",false);return r;}
 public Request own(StoreScope s,CustomerAuthPrincipal p,UUID id){access.customer(s,p,false);var r=requests.get(s,id,false);own(r,p.customerId());if(r.cardId()!=null)owner(cards.get(s,r.cardId(),false,clock.instant()),p.customerId());return r;}
 public Request scan(StoreScope s,CustomerAuthPrincipal p,String code){access.customer(s,p,false);var r=requests.code(s,CardTokens.hash(code,"CK"),false,false);own(r,p.customerId());if(r.cardId()!=null)owner(cards.get(s,r.cardId(),false,clock.instant()),p.customerId());if(!r.status().equals("completed")){access.customer(s,p,true);pending(r);}return r;}
 @Transactional public Request confirm(StoreScope s,CustomerAuthPrincipal p,UUID id,Confirm c){access.customer(s,p,false);if(c==null||c.cardId()==null)throw CardException.invalid();return commands.run(s,p.customerId(),"request.confirm",c.idempotencyKey(),Map.of("id",id,"body",c),Request.class,()->{var r=requests.get(s,id,true);own(r,p.customerId());if(r.createdBy().equals(p.customerId()))throw CardException.forbidden();if(r.status().equals("completed"))return r;access.customer(s,p,true);return apply(s,r,p.customerId(),c.cardId(),c.businessDay(),r.createdBy());});}
 @Transactional public Code authorize(StoreScope s,CustomerAuthPrincipal p,UUID id,Confirm c){access.customer(s,p,true);if(c==null||c.cardId()==null)throw CardException.invalid();var r=requests.get(s,id,true);own(r,p.customerId());if(r.createdBy().equals(p.customerId()))throw CardException.forbidden();pending(r);var h=cards.get(s,c.cardId(),true,clock.instant());validateCard(r,h,p.customerId());if(!r.kind().equals("redeem"))throw CardException.invalid();StampService.requireActive(h,clock.instant());var day=CardRules.day(clock.instant(),h.timezone());if(!day.toString().equals(c.businessDay()))throw new CardException("CARD_DAY_CHANGED",409,"业务日期已变化，请重新确认");String token=CardTokens.issue("CA");requests.authorize(s,id,p.customerId(),h.id(),CardTokens.hash(token,"CA"),day);commands.audit(s,p.customerId(),"request.authorize",Map.of("request",id,"card",h.id()));return new Code(token,r.expiresAt());}
 @Transactional public Request authorized(StoreScope s,CurrentActor a,Authorized c){access.staff(s,a,a!=null&&a.hasPermission("wallet.cards.redeem")?"wallet.cards.redeem":"wallet.cards.stamp",true);if(c==null)throw CardException.invalid();String hash=CardTokens.hash(c.authorization(),"CA");return commands.run(s,a.actorId(),"request.authorized",c.idempotencyKey(),Map.of("hash",hash,"day",c.businessDay()==null?"":c.businessDay()),Request.class,()->{var r=requests.code(s,hash,true,true);access.staff(s,a,permission(r.kind()),true);if(r.customerId()==null||r.cardId()==null)throw CardException.invalid();if(r.authorizationDay()==null)throw CardException.invalid();return apply(s,r,r.customerId(),r.cardId(),r.authorizationDay().toString(),a.actorId());});}
 private Request apply(StoreScope s,Request r,UUID customer,UUID card,String expectedDay,UUID actor){pending(r);var h=cards.get(s,card,true,clock.instant());validateCard(r,h,customer);var now=clock.instant();pending(r);StampService.requireActive(h,now);LocalDate day=CardRules.day(now,h.timezone());if(!day.toString().equals(expectedDay))throw new CardException("CARD_DAY_CHANGED",409,"业务日期已变化，请重新核对");UUID entry;
  if(r.kind().equals("stamp")){stamps.apply(s,h.id(),"request:"+r.id(),actor,customer);entry=cards.entries(s,customer).stream().filter(e->e.kind().equals("stamp")&&e.source().equals("request:"+r.id())).findFirst().orElseThrow().id();}
  else {if(h.kind().equals("stamp"))throw CardException.invalid();int used=cards.used(s,h.id(),day);if(CardRules.available(h.remaining(),h.dailyLimit(),used)<r.quantity())throw new CardException("CARD_QUOTA_INSUFFICIENT",409,"剩余次数或今日额度不足");Integer remaining=h.remaining()==null?null:h.remaining()-r.quantity();cards.usage(s,h.id(),day,r.quantity());cards.update(s,h,remaining,h.progress(),h.round());entry=cards.entry(s,h,"redeem",r.quantity(),h.remaining(),remaining,day,used,used+r.quantity(),r.id().toString(),null,actor,customer,"卡消费",now);}
  pending(r);requests.complete(s,r.id(),customer,h.id(),entry);return requests.get(s,r.id(),false);
 }
 @Transactional public Request cancel(StoreScope s,CurrentActor a,UUID id,Key c){access.staff(s,a,"wallet.cards.redeem",false);return commands.run(s,a.actorId(),"request.cancel",c.idempotencyKey(),Map.of("id",id),Request.class,()->{var r=requests.get(s,id,true);if(r.status().equals("completed"))throw CardException.conflict("已核销，请通过原单冲正");requests.cancel(s,id);return requests.get(s,id,false);});}
 @Transactional public Holding reverse(StoreScope s,CurrentActor a,UUID entryId,Reverse c){access.staff(s,a,"wallet.cards.reverse",false);if(c==null||c.reason()==null||c.reason().isBlank())throw CardException.invalid();return commands.run(s,a.actorId(),"redeem.reverse",c.idempotencyKey(),Map.of("id",entryId,"body",c),Holding.class,()->{var e=cards.entry(s,entryId);var h=cards.get(s,e.cardId(),true,clock.instant());if(!e.kind().equals("redeem")||cards.reversed(s,entryId)||!h.status().equals("active"))throw CardException.conflict("原单不可冲正");int used=cards.used(s,h.id(),e.businessDay());Integer remaining=h.remaining()==null?null:Math.addExact(h.remaining(),e.quantity());cards.usage(s,h.id(),e.businessDay(),-e.quantity());cards.update(s,h,remaining,h.progress(),h.round());cards.entry(s,h,"reverse",e.quantity(),h.remaining(),remaining,e.businessDay(),used,used-e.quantity(),"reverse:"+entryId,entryId,a.actorId(),null,c.reason(),clock.instant());cards.expireRestoredUse(s,h.id(),e.businessDay(),e.quantity(),entryId,clock.instant());return cards.get(s,h.id(),false,clock.instant());});}
 private void validateCard(Request r,Holding h,UUID customer){owner(h,customer);if(!h.planId().equals(r.planId())||r.cardId()!=null&&!r.cardId().equals(h.id()))throw CardException.missing();}
 private static void owner(Holding h,UUID customer){if(!h.customerId().equals(customer))throw CardException.missing();}
 private static void own(Request r,UUID customer){if(r.customerId()!=null&&!r.customerId().equals(customer))throw CardException.missing();}
 private void pending(Request r){if(!r.status().equals("pending"))throw CardException.conflict("操作已完成或取消");if(!r.expiresAt().isAfter(clock.instant()))throw new CardException("CARD_CODE_EXPIRED",409,"操作码已过期，请重新发起");}
 private static String permission(String kind){return kind.equals("stamp")?"wallet.cards.stamp":"wallet.cards.redeem";}
}
