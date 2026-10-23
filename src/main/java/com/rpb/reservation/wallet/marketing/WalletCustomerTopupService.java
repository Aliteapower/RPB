package com.rpb.reservation.wallet.marketing;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.domain.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.marketing.MarketingDtos.*;

/** Customer identity remains a customer throughout; receipt confirmation is a separate staff use case. */
@Service
@Transactional
public class WalletCustomerTopupService {
 private final WalletMarketingService marketing;private final MarketingRepository r;private final WalletAccess access;private final WalletAccountRepository accounts;private final WalletTopupRepository topups;private final WalletCommandRepository commands;private final WalletCollectionPort collection;
 @Value("${rpb.wallet.marketing.apply-per-minute:5}") private int applyLimit=5;
 @Value("${rpb.wallet.marketing.apply-ip-per-minute:30}") private int ipLimit=30;
 public WalletCustomerTopupService(WalletMarketingService marketing,MarketingRepository r,WalletAccess access,WalletAccountRepository accounts,WalletTopupRepository topups,WalletCommandRepository commands,WalletCollectionPort collection){this.marketing=marketing;this.r=r;this.access=access;this.accounts=accounts;this.topups=topups;this.commands=commands;this.collection=collection;}
 public WalletViews.Topup apply(StoreScope s,CustomerAuthPrincipal a,Apply c,String ip){
  access.customer(s,a,true);if(c==null||c.quoteId()==null)throw WalletException.invalid();
  return commands.run(s,a.customerId(),"customer.topup.create",c.idempotencyKey(),c,WalletViews.Topup.class,()->{
   var p=marketing.context(s,a.customerId(),true);marketing.requireSelf(s,p.id());
   r.rate(s.tenantId().value(),p.id(),"customer:"+a.customerId(),"apply",applyLimit);r.rate(s.tenantId().value(),p.id(),"ip:"+Objects.toString(ip,"unknown"),"apply",ipLimit);
   // This owner/plan transaction lock serializes distinct idempotency keys and all stores in the plan.
   r.jdbc().queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","wallet.customer.topup:"+s.tenantId().value()+":"+p.id()+":"+a.customerId());
   var reused=r.jdbc().queryForList("select consumed_topup_id from wallet_topup_quotes where tenant_id=? and program_id=? and store_id=? and principal_type='customer' and principal_id=? and id=?",s.tenantId().value(),p.id(),s.storeId().value(),a.customerId(),c.quoteId());
   if(!reused.isEmpty()&&reused.getFirst().get("consumed_topup_id")!=null)return own(s,a,(UUID)reused.getFirst().get("consumed_topup_id"),false);
   var pending=r.jdbc().queryForList("select id from wallet_topups where tenant_id=? and program_id=? and customer_id=? and origin='customer' and status in ('pending_collection','verified_pending_credit')",s.tenantId().value(),p.id(),a.customerId());
   if(!pending.isEmpty())throw new PendingTopupException((UUID)pending.getFirst().get("id"));
   var prepared=marketing.customerPrepare(s,p,a,c.quoteId());var account=accounts.open(s,p,a.customerId());account=accounts.account(s,account.id(),true);access.own(account,a);accounts.requireActive(account);
   var topup=topups.create(s,account,prepared.principalCents(),prepared.channel(),a.customerId());marketing.attach(s,topup.id(),prepared);
   r.jdbc().update("update wallet_topups set origin='customer' where tenant_id=? and id=?",s.tenantId().value(),topup.id());commands.audit(s,a.customerId(),"customer.topup.create",topup.id(),Map.of("quoteId",c.quoteId(),"origin","customer"));return topups.get(s,topup.id(),false);
  });
 }
 public List<WalletViews.Topup> list(StoreScope s,CustomerAuthPrincipal a,int offset,String status){access.customer(s,a,false);var p=accounts.requireProgram(s,false);if(offset<0||offset>100000||!Set.of("all","pending","completed","closed").contains(status))throw WalletException.invalid();String filter=switch(status){case "pending"->" and status in ('pending_collection','verified_pending_credit','exception')";case "completed"->" and status='credited'";case "closed"->" and status='cancelled'";default->"";};var ids=r.jdbc().queryForList("select id from wallet_topups where tenant_id=? and program_id=? and customer_id=?"+filter+" order by created_at desc,id desc limit 100 offset ?",s.tenantId().value(),p.id(),a.customerId(),offset);return ids.stream().map(x->own(s,a,(UUID)x.get("id"),false)).toList();}
 public WalletViews.Topup get(StoreScope s,CustomerAuthPrincipal a,UUID id){access.customer(s,a,false);return own(s,a,id,false);}
 WalletViews.Topup own(StoreScope s,CustomerAuthPrincipal a,UUID id,boolean lock){var p=accounts.requireProgram(s,false);var rows=r.jdbc().queryForList("select id,store_id,account_id from wallet_topups where tenant_id=? and program_id=? and customer_id=? and id=?"+(lock?" for update":""),s.tenantId().value(),p.id(),a.customerId(),id);if(rows.isEmpty())throw WalletException.missing();var row=rows.getFirst();return topups.get(new StoreScope(s.tenantId(),(UUID)row.get("store_id")),id,false);}
 public WalletViews.Topup declare(StoreScope s,CustomerAuthPrincipal a,UUID id,CustomerAction c){access.customer(s,a,true);validate(c);return commands.run(s,a.customerId(),"customer.topup.declare",c.idempotencyKey(),Map.of("id",id,"command",c),WalletViews.Topup.class,()->{var t=own(s,a,id,true);if(!"pending_collection".equals(t.status()))throw WalletException.conflict("仅待收款充值单可以声明付款");r.jdbc().update("update wallet_topups set customer_declared_paid_at=coalesce(customer_declared_paid_at,now()),customer_declared_note=case when customer_declared_paid_at is null then ? else customer_declared_note end,version=version+case when customer_declared_paid_at is null then 1 else 0 end,updated_at=now() where tenant_id=? and id=?",Objects.toString(c.note(),""),s.tenantId().value(),id);commands.audit(s,a.customerId(),"customer.topup.declare",id,Map.of("note",Objects.toString(c.note(),"")));return own(s,a,id,false);});}
 public WalletViews.Topup cancel(StoreScope s,CustomerAuthPrincipal a,UUID id,CustomerAction c){access.customer(s,a,true);validate(c);return commands.run(s,a.customerId(),"customer.topup.cancel",c.idempotencyKey(),Map.of("id",id,"command",c),WalletViews.Topup.class,()->{var t=own(s,a,id,true);if(!"pending_collection".equals(t.status()))throw WalletException.conflict("已核实收款的充值单不能取消");topups.status(new StoreScope(s.tenantId(),t.storeId()),id,"cancelled");commands.audit(s,a.customerId(),"customer.topup.cancel",id,Map.of("reason",Objects.toString(c.reason(),"")));return own(s,a,id,false);});}
 public Map<String,String> paynow(StoreScope s,CustomerAuthPrincipal a,UUID id){access.customer(s,a,false);var t=own(s,a,id,false);var original=new StoreScope(s.tenantId(),t.storeId());if(!"pending_collection".equals(t.status())||!"paynow".equals(t.channel()))throw WalletException.conflict("原单不能展示收款码");
  // An unreceived topup is not an existing partially-paid bill: an expired product cannot solicit new funds.
  if(!access.commercial(original).allowed())throw WalletException.forbidden();var account=accounts.account(original,t.accountId(),false);accounts.requireActive(account);accounts.verifyIntegrity(account);return Map.of("payload",collection.paynowQr(original,t.amountCents(),id),"notice","付款码仅用于转账，请等待商户核实本金实收，声明付款不代表到账");}
 private void validate(CustomerAction c){if(c==null||Objects.toString(c.note(),"").length()>500||Objects.toString(c.reason(),"").length()>500)throw WalletException.invalid();}
 public static class PendingTopupException extends WalletException {private final UUID pendingTopupId;public PendingTopupException(UUID id){super("WALLET_PENDING_TOPUP_EXISTS",409,"已有待处理充值申请，请恢复原单");pendingTopupId=id;}public UUID pendingTopupId(){return pendingTopupId;}}
}
