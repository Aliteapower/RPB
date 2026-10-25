package com.rpb.reservation.cards.entitlement;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.cards.plan.CardPlanRepository;
import com.rpb.reservation.cards.command.CardCommands;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class CardHoldingService {
 private final com.rpb.reservation.catalog.application.ProductCatalogQuery products;private final CardAccess access;private final CardPlanRepository plans;private final CardHoldingRepository r;private final CardCommands commands;private final Clock clock;
 public CardHoldingService(CardAccess access,CardPlanRepository plans,CardHoldingRepository r,CardCommands commands,Clock clock,com.rpb.reservation.catalog.application.ProductCatalogQuery products){this.products=products;this.access=access;this.plans=plans;this.r=r;this.commands=commands;this.clock=clock;}
 public List<Holding> own(StoreScope s,CustomerAuthPrincipal p){return own(s,p,null);}
 public List<Holding> own(StoreScope s,CustomerAuthPrincipal p,UUID before){access.customer(s,p,false);return r.list(s,p.customerId(),clock.instant(),before);}
 public List<Holding> staff(StoreScope s,CurrentActor a,UUID customer){return staff(s,a,customer,null);}
 public List<Holding> staff(StoreScope s,CurrentActor a,UUID customer,UUID before){access.staff(s,a,"wallet.cards.view",false);if(customer!=null)access.requireCustomer(s,customer);return r.list(s,customer,clock.instant(),before);}
 public List<Entry> entries(StoreScope s,CurrentActor a,UUID customer){return entries(s,a,customer,null);}
 public List<Entry> entries(StoreScope s,CurrentActor a,UUID customer,UUID before){access.staff(s,a,"wallet.cards.view",false);return r.entries(s,customer,before);}
 public List<Entry> ownEntries(StoreScope s,CustomerAuthPrincipal p){return ownEntries(s,p,null);}
 public List<Entry> ownEntries(StoreScope s,CustomerAuthPrincipal p,UUID before){access.customer(s,p,false);return r.entries(s,p.customerId(),before);}
 @Transactional public Holding enroll(StoreScope s,CustomerAuthPrincipal p,Enroll c){access.customer(s,p,true);if(c==null||c.planId()==null)throw CardException.invalid();return commands.run(s,p.customerId(),"enroll",c.idempotencyKey(),c,Holding.class,()->enrollFor(s,p.customerId(),c.planId()));}
 @Transactional public Holding staffEnroll(StoreScope s,CurrentActor a,UUID customer,Enroll c){access.staff(s,a,"wallet.cards.stamp",true);if(customer==null||c==null)throw CardException.invalid();return commands.run(s,a.actorId(),"enroll.staff",c.idempotencyKey(),Map.of("customer",customer,"body",c),Holding.class,()->enrollFor(s,customer,c.planId()));}
 public Holding enrollFor(StoreScope s,UUID customer,UUID plan){var p=plans.get(s,plan,true);if(!p.kind().equals("stamp")||!p.status().equals("active"))throw CardException.conflict("该卡不能免费领取");products.requireUsable(s,p.productId(),"cards");access.requireCustomer(s,customer);r.enrollmentLock(s,customer,plan);return r.enrollment(s,customer,plan,clock.instant()).orElseGet(()->{var now=clock.instant();var zone=access.store(s).timezone();var h=r.issue(s,customer,p,"stamp",null,p.dailyLimit(),zone,now,CardRules.expiry(now,zone,p.validDays()));r.entry(s,h,"grant",1,null,null,CardRules.day(now,zone),0,0,"enroll:"+h.id(),null,customer,customer,"免费领卡",now);return h;});}
}
