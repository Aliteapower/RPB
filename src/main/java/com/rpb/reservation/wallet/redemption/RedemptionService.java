package com.rpb.reservation.wallet.redemption;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.WalletAccess;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.integration.WalletPointsAdapter;
import com.rpb.reservation.wallet.domain.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.redemption.RedemptionDtos.*;
@Service
@Transactional(readOnly=true)
public class RedemptionService {
 private final RedemptionRepository requests;private final WalletAccountRepository accounts;private final WalletCommandRepository commands;private final WalletAccess access;private final WalletPointsAdapter points;private final Clock clock;
 public RedemptionService(RedemptionRepository requests,WalletAccountRepository accounts,WalletCommandRepository commands,WalletAccess access,WalletPointsAdapter points,Clock clock){this.requests=requests;this.accounts=accounts;this.commands=commands;this.access=access;this.points=points;this.clock=clock;}
 private UUID program(StoreScope s,boolean active){return accounts.requireProgram(s,active).id();}
 private void pending(RedemptionRepository.Request r){if(!r.status().equals("pending"))throw WalletException.conflict("积分兑换已完成或取消");if(!r.expiresAt().isAfter(clock.instant()))throw new WalletException("WALLET_REDEMPTION_EXPIRED",409,"积分兑换码已过期，请商户重新生成");}
 private View view(StoreScope s,RedemptionRepository.Request r,com.rpb.reservation.loyalty.application.PointsViews.Account a){return new View(r.id(),s.storeId().value(),r.customer(),r.quantity(),r.description(),r.status().equals("pending")&&!r.expiresAt().isAfter(clock.instant())?"expired":r.status(),r.expiresAt(),r.entry(),a.balance(),a.version());}
 private void own(RedemptionRepository.Request r,CustomerAuthPrincipal p){if(!r.customer().equals(p.customerId()))throw WalletException.missing();}
 @Transactional public View create(StoreScope s,CurrentActor actor,Create c){
  access.staff(s,actor,"wallet.points.redeem",true);if(c==null||c.customerId()==null||c.quantity()==null||c.quantity()<1||c.quantity()>1_000_000_000||c.description()==null||c.description().isBlank()||c.description().length()>1000)throw WalletException.invalid();
  UUID p=program(s,true);var balance=points.get(s,actor,c.customerId());
  return commands.run(s,actor.actorId(),"points.request.create",c.idempotencyKey(),c,View.class,()->{if(!points.context(s,actor).transactionsEnabled())throw new WalletException("WALLET_POINTS_DISABLED",409,"积分功能尚未启用");if(balance.balance()<c.quantity())throw new WalletException("POINTS_INSUFFICIENT",409,"顾客积分不足");UUID id=requests.create(s,p,c.customerId(),actor.actorId(),c.quantity(),c.description().trim(),clock.instant().plusSeconds(120));commands.audit(s,actor.actorId(),"points.request.create",id,Map.of("customerId",c.customerId(),"quantity",c.quantity()));return view(s,requests.get(s,p,id,false),balance);});
 }
 public View get(StoreScope s,CurrentActor actor,UUID id){access.staff(s,actor,"wallet.points.view",false);var r=requests.get(s,program(s,false),id,false);return view(s,r,points.get(s,actor,r.customer()));}
 public View own(StoreScope s,CustomerAuthPrincipal p,UUID id){access.customer(s,p,false);var r=requests.get(s,program(s,false),id,false);own(r,p);return view(s,r,points.own(s,p));}
 @Transactional public Code issue(StoreScope s,CurrentActor actor,UUID id){access.staff(s,actor,"wallet.points.redeem",true);UUID p=program(s,true);var r=requests.get(s,p,id,true);pending(r);String token=WalletTokens.issue("WR");var expires=clock.instant().plusSeconds(120);requests.issue(s,id,WalletTokens.hash(token),expires);return new Code(token,expires);}
 public View scan(StoreScope s,CustomerAuthPrincipal p,String code){access.customer(s,p,false);var r=requests.code(s,program(s,false),WalletTokens.required(code,"WR"));own(r,p);if(!r.status().equals("completed")){access.customer(s,p,true);program(s,true);pending(r);}return view(s,r,points.own(s,p));}
 @Transactional public View confirm(StoreScope s,CustomerAuthPrincipal p,UUID id,Confirm c){
  access.customer(s,p,false);if(c==null||c.expectedVersion()==null||c.expectedVersion()<0)throw WalletException.invalid();UUID program=program(s,false);own(requests.get(s,program,id,false),p);
  return commands.run(s,p.customerId(),"points.request.confirm",c.idempotencyKey(),Map.of("id",id,"body",c),View.class,()->{var r=requests.get(s,program,id,true);own(r,p);if(r.status().equals("completed"))return view(s,r,points.own(s,p));pending(r);access.customer(s,p,true);program(s,true);
   var result=points.redeemConfirmed(s,p,r.creator(),id,r.quantity(),r.description(),c.expectedVersion());pending(r);requests.complete(s,id,result.entry().id(),clock.instant());commands.audit(s,p.customerId(),"points.request.confirm",id,Map.of("quantity",r.quantity(),"entryId",result.entry().id(),"createdBy",r.creator()));return view(s,requests.get(s,program,id,false),result.account());});
 }
 @Transactional public View cancel(StoreScope s,CurrentActor actor,UUID id,Cancel c){access.staff(s,actor,"wallet.points.redeem",false);if(c==null)throw WalletException.invalid();UUID p=program(s,false);return commands.run(s,actor.actorId(),"points.request.cancel",c.idempotencyKey(),Map.of("id",id),View.class,()->{var r=requests.get(s,p,id,true);if(r.status().equals("completed"))throw WalletException.conflict("已兑换，请通过原积分记录冲正");requests.cancel(s,id);commands.audit(s,actor.actorId(),"points.request.cancel",id,Map.of());return get(s,actor,id);});}
}
