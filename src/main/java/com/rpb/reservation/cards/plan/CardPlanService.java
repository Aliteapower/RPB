package com.rpb.reservation.cards.plan;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.cards.command.CardCommands;
import com.rpb.reservation.catalog.application.ProductCatalogQuery;
import com.rpb.reservation.catalog.pricing.TaxCalculator;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class CardPlanService {
 private final CardAccess access;private final CardPlanRepository r;private final ProductCatalogQuery products;private final CardCommands commands;
 public CardPlanService(CardAccess access,CardPlanRepository r,ProductCatalogQuery products,CardCommands commands){this.access=access;this.r=r;this.products=products;this.commands=commands;}
 public List<Plan> staff(StoreScope s,CurrentActor a){access.staff(s,a,"wallet.cards.view",false);return r.list(s);}
 public List<Plan> own(StoreScope s,CustomerAuthPrincipal p){access.customer(s,p,false);return r.list(s).stream().filter(x->x.status().equals("active")).toList();}
 @Transactional public Plan create(StoreScope s,CurrentActor a,PlanCreate c){access.staff(s,a,"wallet.cards.manage",true);validate(c);return commands.run(s,a.actorId(),"plan.create",c.idempotencyKey(),c,Plan.class,()->{var product=products.requireUsable(s,c.productId(),"cards");var p=new Plan(UUID.randomUUID(),s.storeId().value(),text(c.nameZh()),text(c.nameEn()),c.kind(),c.productId(),c.total(),c.dailyLimit(),c.validDays(),c.stampTarget(),c.repeatable(),c.rewardDays(),c.priceCents(),c.taxMode(),c.taxRate(),product,"active",0);r.create(s,a.actorId(),access.store(s).issuerId(),p);return p;});}
 @Transactional public Plan status(StoreScope s,CurrentActor a,UUID id,PlanStatus c){access.staff(s,a,"wallet.cards.manage",false);if(c==null||!Set.of("active","inactive").contains(text(c.status())))throw CardException.invalid();return commands.run(s,a.actorId(),"plan.status",c.idempotencyKey(),Map.of("id",id,"body",c),Plan.class,()->{r.status(s,id,c);return r.get(s,id,false);});}
 private void validate(PlanCreate c){if(c==null||!Set.of("stamp","count","period").contains(text(c.kind()))||text(c.nameZh()).isBlank()&&text(c.nameEn()).isBlank()||c.productId()==null||c.validDays()<1||c.validDays()>3660||c.rewardDays()<1||c.rewardDays()>3660||c.total()!=null&&(c.total()<1||c.total()>1000000)||c.dailyLimit()!=null&&(c.dailyLimit()<1||c.dailyLimit()>1000000)||c.kind().equals("count")&&c.total()==null||c.kind().equals("period")&&c.dailyLimit()==null||c.kind().equals("stamp")&&(c.stampTarget()<1||c.stampTarget()>1000000||c.priceCents()!=0)||!c.kind().equals("stamp")&&(c.stampTarget()!=0||c.priceCents()<1))throw CardException.invalid();try{TaxCalculator.calculate(c.priceCents(),1,c.taxMode(),c.taxRate());}catch(IllegalArgumentException e){throw CardException.invalid();}}
 private static String text(String s){return s==null?"":s.trim();}
}
