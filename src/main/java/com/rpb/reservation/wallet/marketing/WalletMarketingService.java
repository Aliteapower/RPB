package com.rpb.reservation.wallet.marketing;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.domain.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.marketing.MarketingDtos.*;
import static com.rpb.reservation.wallet.marketing.MarketingRepository.*;

/** Quote authority only. This module never credits money or confirms receipt. */
@Service
@Transactional
public class WalletMarketingService {
 private final MarketingRepository r;private final WalletAccountRepository accounts;private final WalletAccess access;private final WalletCollectionPort collection;
 @Value("${rpb.wallet.marketing.quote-per-minute:30}") private int quoteLimit=30;
 public WalletMarketingService(MarketingRepository r,WalletAccountRepository accounts,WalletAccess access,WalletCollectionPort collection){this.r=r;this.accounts=accounts;this.access=access;this.collection=collection;}
 public record Prepared(long principalCents,long bonusCents,String channel,String deductionPolicy,UUID quoteId,Map<String,Object> offerSnapshot,Map<String,Object> policySnapshot) {}
 WalletViews.Program context(StoreScope s,UUID actor,boolean active){var p=accounts.requireProgram(s,active);r.lock(s.tenantId().value(),p.id());r.initialize(s.tenantId().value(),p.id(),actor);return p;}
 /** Feature inspection must remain read-only for points-only entry and expired entitlement history. */
 @Transactional(readOnly=true)
 public boolean pointsEnabled(StoreScope s){return Boolean.TRUE.equals(r.jdbc().queryForObject("select exists(select 1 from wallet_programs p join wallet_program_stores ps on ps.program_id=p.id and ps.tenant_id=p.tenant_id join wallet_marketing_settings m on m.program_id=p.id and m.tenant_id=p.tenant_id where p.tenant_id=? and ps.store_id=? and p.status='active' and ps.enabled=true and m.points_enabled=true)",Boolean.class,s.tenantId().value(),s.storeId().value()));}
 public Offers offers(StoreScope s,CurrentActor a){access.staff(s,a,"wallet.topup.create",true);return live(s,context(s,a.actorId(),true));}
 public Offers customerOffers(StoreScope s,CustomerAuthPrincipal p){access.customer(s,p,true);var program=context(s,p.customerId(),true);requireSelf(s,program.id());return live(s,program);}
 void requireSelf(StoreScope s,UUID p){if(!(Boolean)r.settings(s.tenantId().value(),p).get("self_topup_enabled"))throw new WalletException("WALLET_SELF_TOPUP_DISABLED",403,"顾客自助充值尚未开启");}
 Offers live(StoreScope s,WalletViews.Program p){UUID t=s.tenantId().value();var settings=r.settings(t,p.id());var campaign=(Boolean)settings.get("campaigns_enabled")?r.active(t,p.id()):null;UUID id=campaign==null?null:(UUID)campaign.get("campaign_id");int revision=campaign==null?r.baseRevision(t,p.id()):integer(campaign,"revision");List<String> channels=p.channels();
  return new Offers(id,campaign==null?null:revision,campaign==null?null:(String)campaign.get("name"),(String)settings.get("default_deduction_policy"),integer(settings,"policy_version"),channels,r.offers(t,p.id(),id,revision),(Boolean)settings.get("self_topup_enabled"));
 }
 public Quote quote(StoreScope s,CurrentActor a,QuoteRequest c){access.staff(s,a,"wallet.topup.create",true);if(c==null||c.accountId()==null)throw WalletException.invalid();var p=context(s,a.actorId(),true);var account=accounts.account(s,c.accountId(),false);accounts.requireActive(account);return createQuote(s,p,"staff",a.actorId(),account.id(),c.offerId(),c.offerVersion(),c.channel());}
 public Quote customerQuote(StoreScope s,CustomerAuthPrincipal a,CustomerQuote c){access.customer(s,a,true);if(c==null)throw WalletException.invalid();var p=context(s,a.customerId(),true);requireSelf(s,p.id());return createQuote(s,p,"customer",a.customerId(),null,c.offerId(),c.offerVersion(),c.channel());}
 private Quote createQuote(StoreScope s,WalletViews.Program p,String kind,UUID owner,UUID account,UUID offerId,int version,String channel){
  r.rate(s.tenantId().value(),p.id(),kind+":"+owner,"quote",quoteLimit);var live=live(s,p);var offer=live.offers().stream().filter(o->o.id().equals(offerId)&&o.version()==version).findFirst().orElseThrow(MarketingDtos::changed);channel=WalletMoney.channel(channel);if(!live.channels().contains(channel)||("paynow".equals(channel)&&!collection.paynowConfigured(s)))throw WalletException.invalid();
  UUID id=UUID.randomUUID();Instant expiry=Instant.now().plusSeconds(300);Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("offerId",offer.id());snapshot.put("offerVersion",offer.version());snapshot.put("campaignId",live.campaignId());snapshot.put("campaignVersion",live.campaignVersion());snapshot.put("campaignName",live.campaignName());snapshot.put("principalCents",offer.principalCents());snapshot.put("bonusCents",offer.bonusCents());snapshot.put("ratioNumerator",offer.principalCents());snapshot.put("ratioDenominator",offer.creditTotalCents());
  if(live.campaignId()!=null){var campaign=r.campaign(s.tenantId().value(),p.id(),live.campaignId());snapshot.put("terms",campaign.revisions().stream().filter(x->x.revision()==live.campaignVersion()).findFirst().orElseThrow().terms());}
  Map<String,Object> policy=Map.of("deductionPolicy",live.deductionPolicy(),"policyVersion",live.policyVersion());
  r.jdbc().update("insert into wallet_topup_quotes(id,tenant_id,program_id,store_id,principal_type,principal_id,account_id,offer_id,offer_version,campaign_id,campaign_version,principal_cents,bonus_cents,channel,deduction_policy,policy_version,config_version,offer_snapshot,policy_snapshot,expires_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?)",id,s.tenantId().value(),p.id(),s.storeId().value(),kind,owner,account,offer.id(),version,live.campaignId(),live.campaignVersion(),offer.principalCents(),offer.bonusCents(),channel,live.deductionPolicy(),live.policyVersion(),integer(r.settings(s.tenantId().value(),p.id()),"config_version"),r.encode(snapshot),r.encode(policy),Timestamp.from(expiry));
  return new Quote(id,offer.id(),version,live.campaignId(),live.campaignVersion(),offer.principalCents(),offer.bonusCents(),offer.creditTotalCents(),channel,live.deductionPolicy(),live.policyVersion(),expiry);
 }
 public Prepared prepare(StoreScope s,CurrentActor a,WalletCommands.TopupCreate c){
  access.staff(s,a,"wallet.topup.create",true);if(c==null)throw WalletException.invalid();var p=context(s,a.actorId(),true);
  if(c.quoteId()!=null){if(c.amount()!=null||c.channel()!=null)throw WalletException.invalid();return quoted(s,p,"staff",a.actorId(),c.accountId(),c.quoteId());}
  if(live(s,p).campaignId()!=null)throw new WalletException("WALLET_TOPUP_OFFER_REQUIRED",409,"活动期间请先选择充值档位并报价");String channel=WalletMoney.channel(c.channel());if(!p.channels().contains(channel)||("paynow".equals(channel)&&!collection.paynowConfigured(s)))throw WalletException.invalid();var settings=r.settings(s.tenantId().value(),p.id());String policy=(String)settings.get("default_deduction_policy");return new Prepared(WalletMoney.cents(c.amount()),0,channel,policy,null,Map.of("kind","ordinary_no_campaign"),Map.of("deductionPolicy",policy,"policyVersion",integer(settings,"policy_version")));
 }
 Prepared customerPrepare(StoreScope s,WalletViews.Program p,CustomerAuthPrincipal a,UUID quote){requireSelf(s,p.id());return quoted(s,p,"customer",a.customerId(),null,quote);}
 private Prepared quoted(StoreScope s,WalletViews.Program p,String kind,UUID owner,UUID account,UUID id){
  if(id==null)throw WalletException.invalid();var rows=r.jdbc().queryForList("select * from wallet_topup_quotes where tenant_id=? and program_id=? and store_id=? and principal_type=? and principal_id=? and id=? for update",s.tenantId().value(),p.id(),s.storeId().value(),kind,owner,id);if(rows.isEmpty())throw WalletException.missing();var q=rows.getFirst();
  if(!Objects.equals(q.get("account_id"),account))throw WalletException.missing();if(q.get("consumed_topup_id")!=null)throw new WalletException("WALLET_TOPUP_QUOTE_CONSUMED",409,"报价已用于充值单 "+q.get("consumed_topup_id"));
  if(!instant(q.get("expires_at")).isAfter(Instant.now()))throw new WalletException("WALLET_TOPUP_QUOTE_EXPIRED",409,"充值报价已过期，请重新选择");
  var live=live(s,p);var settings=r.settings(s.tenantId().value(),p.id());if(!Objects.equals(live.campaignId(),q.get("campaign_id"))||!Objects.equals(live.campaignVersion(),q.get("campaign_version"))||integer(settings,"config_version")!=integer(q,"config_version")||("paynow".equals(q.get("channel"))&&!collection.paynowConfigured(s))||!live.channels().contains(q.get("channel"))||live.offers().stream().noneMatch(o->o.id().equals(q.get("offer_id"))&&o.version()==integer(q,"offer_version")))throw changed();
  return new Prepared(number(q,"principal_cents"),number(q,"bonus_cents"),(String)q.get("channel"),(String)q.get("deduction_policy"),id,decode(q.get("offer_snapshot")),decode(q.get("policy_snapshot")));
 }
 @SuppressWarnings("unchecked") private Map<String,Object> decode(Object o){try{return r.json().readValue(o.toString(),Map.class);}catch(Exception e){throw new IllegalStateException(e);}}
 public void attach(StoreScope s,UUID topup,Prepared p){
  if(p.quoteId()!=null){int n=r.jdbc().update("update wallet_topup_quotes set consumed_topup_id=? where tenant_id=? and store_id=? and id=? and consumed_topup_id is null",topup,s.tenantId().value(),s.storeId().value(),p.quoteId());if(n!=1)throw changed();}
  r.jdbc().update("update wallet_topups t set bonus_cents=?,deduction_policy=?,quote_id=?,offer_id=?,offer_version=?,campaign_id=?,campaign_version=?,offer_snapshot=?::jsonb,policy_snapshot=?::jsonb,customer_id=a.customer_id from wallet_accounts a where t.tenant_id=? and t.store_id=? and t.id=? and a.id=t.account_id and a.tenant_id=t.tenant_id",p.bonusCents(),p.deductionPolicy(),p.quoteId(),uuid(p.offerSnapshot().get("offerId")),p.offerSnapshot().get("offerVersion"),uuid(p.offerSnapshot().get("campaignId")),p.offerSnapshot().get("campaignVersion"),r.encode(p.offerSnapshot()),r.encode(p.policySnapshot()),s.tenantId().value(),s.storeId().value(),topup);
 }
 private UUID uuid(Object value){return value==null?null:UUID.fromString(value.toString());}
}
