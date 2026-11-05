package com.rpb.reservation.wallet.merchantorder;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.WalletAccess;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.integration.*;
import com.rpb.reservation.wallet.domain.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.*;
@Service
@Transactional(readOnly=true)
public class MerchantOrderService {
 private final MerchantOrderRepository orders;private final WalletAccountRepository accounts;private final WalletCommandRepository commands;private final WalletAccess access;private final WalletPointsAdapter points;private final WalletMerchantMoneyAdapter money;private final Clock clock;
 public MerchantOrderService(MerchantOrderRepository orders,WalletAccountRepository accounts,WalletCommandRepository commands,WalletAccess access,WalletPointsAdapter points,WalletMerchantMoneyAdapter money,Clock clock){this.orders=orders;this.accounts=accounts;this.commands=commands;this.access=access;this.points=points;this.money=money;this.clock=clock;}
 private UUID program(StoreScope s,boolean active){return accounts.requireProgram(s,active).id();}
 private void pending(MerchantOrderRepository.Order o){if(!o.status().equals("pending"))throw WalletException.conflict("本单已完成或取消");if(!o.expires().isAfter(clock.instant()))throw new WalletException("WALLET_MERCHANT_ORDER_EXPIRED",409,"商户码已过期，请重新生成");}
 private void own(MerchantOrderRepository.Order o,CustomerAuthPrincipal p){if(o.customer()!=null&&!o.customer().equals(p.customerId()))throw WalletException.missing();}
 private void staff(StoreScope s,CurrentActor a,String kind,boolean active){access.staff(s,a,kind.equals("points")?"wallet.points.redeem":"wallet.charge.create",active);access.staff(s,a,kind.equals("points")?"wallet.points.view":"wallet.view",false);}
 private void readAccess(StoreScope s,CurrentActor a){access.staff(s,a,a!=null&&a.hasPermission("wallet.view")?"wallet.view":"wallet.points.view",false);}
 private View view(MerchantOrderRepository.Order o,Long balance,Integer version){return new View(o.id(),o.kind(),o.amount(),o.quantity(),o.description(),o.status().equals("pending")&&!o.expires().isAfter(clock.instant())?"expired":o.status(),o.expires(),o.customer(),o.bill()!=null?"bill":o.entry()!=null?"points":null,o.bill()!=null?o.bill():o.entry(),balance,version);}
 private View customerView(StoreScope s,CustomerAuthPrincipal p,MerchantOrderRepository.Order o){own(o,p);if(o.kind().equals("points")){var a=points.own(s,p);return view(o,(long)a.balance(),a.version());}var a=accounts.customerAccounts(s,p.customerId()).stream().findFirst();return view(o,a.map(x->x.availableCents()).orElse(0L),a.map(x->x.version()).orElse(null));}
 @Transactional public View create(StoreScope s,CurrentActor a,Create c){
  if(c==null||!Set.of("money","points").contains(c.kind()==null?"":c.kind())||c.description()!=null&&c.description().length()>1000)throw WalletException.invalid();
  boolean point=c.kind().equals("points");Long amount=null;if(point){if(c.amount()!=null||c.quantity()==null||c.quantity()<1||c.quantity()>1_000_000_000||c.description()==null||c.description().isBlank())throw WalletException.invalid();}else{if(c.quantity()!=null)throw WalletException.invalid();amount=WalletMoney.cents(c.amount());}
  staff(s,a,c.kind(),true);UUID program=program(s,true);Long total=amount;
  return commands.run(s,a.actorId(),"merchant.order.create",c.idempotencyKey(),c,View.class,()->{if(point&&!points.context(s,a).transactionsEnabled())throw new WalletException("WALLET_POINTS_DISABLED",409,"积分功能尚未启用");UUID id=orders.create(s,program,a.actorId(),c.kind(),total,c.quantity(),c.description()==null?"":c.description().trim(),clock.instant().plusSeconds(120));commands.audit(s,a.actorId(),"merchant.order.create",id,Map.of("kind",c.kind()));return view(orders.get(s,program,id,false),null,null);});
 }
 public View get(StoreScope s,CurrentActor a,UUID id){readAccess(s,a);var o=orders.get(s,program(s,false),id,false);staff(s,a,o.kind(),false);return view(o,null,null);}
 public View own(StoreScope s,CustomerAuthPrincipal p,UUID id){access.customer(s,p,false);return customerView(s,p,orders.get(s,program(s,false),id,false));}
 @Transactional public Code issue(StoreScope s,CurrentActor a,UUID id){readAccess(s,a);UUID program=program(s,true);var o=orders.get(s,program,id,true);staff(s,a,o.kind(),true);pending(o);String code=WalletTokens.issue("WM");var expiry=clock.instant().plusSeconds(120);orders.issue(s,id,WalletTokens.hash(code),expiry);return new Code(code,expiry);}
 public View scan(StoreScope s,CustomerAuthPrincipal p,String code){access.customer(s,p,true);var o=orders.code(s,program(s,true),WalletTokens.required(code,"WM"));own(o,p);if(!o.status().equals("completed"))pending(o);return customerView(s,p,o);}
 @Transactional public View confirm(StoreScope s,CustomerAuthPrincipal p,UUID id,Confirm c){
  access.customer(s,p,false);if(c==null||c.expectedVersion()==null||c.expectedVersion()<0)throw WalletException.invalid();UUID program=program(s,false);
  return commands.run(s,p.customerId(),"merchant.order.confirm",c.idempotencyKey(),Map.of("id",id,"body",c),View.class,()->{
   var o=orders.get(s,program,id,true);own(o,p);if(o.status().equals("completed"))return customerView(s,p,o);pending(o);access.customer(s,p,true);program(s,true);
   UUID bill=null,entry=null;if(o.kind().equals("money"))bill=money.pay(s,p,o.creator(),id,o.amount(),c.expectedVersion());else entry=points.redeemConfirmed(s,p,o.creator(),id,o.quantity(),o.description(),c.expectedVersion()).entry().id();
   pending(o);orders.complete(s,id,p.customerId(),bill,entry,clock.instant());commands.audit(s,p.customerId(),"merchant.order.confirm",id,Map.of("kind",o.kind(),"sourceId",bill!=null?bill:entry));return customerView(s,p,orders.get(s,program,id,false));
  });
 }
 @Transactional public View cancel(StoreScope s,CurrentActor a,UUID id,Cancel c){if(c==null)throw WalletException.invalid();readAccess(s,a);UUID program=program(s,false);var initial=orders.get(s,program,id,false);staff(s,a,initial.kind(),false);return commands.run(s,a.actorId(),"merchant.order.cancel",c.idempotencyKey(),Map.of("id",id),View.class,()->{var o=orders.get(s,program,id,true);if(o.status().equals("completed"))throw WalletException.conflict("已完成，请通过原交易退款或冲正");orders.cancel(s,id);commands.audit(s,a.actorId(),"merchant.order.cancel",id,Map.of());return view(orders.get(s,program,id,false),null,null);});}
}
