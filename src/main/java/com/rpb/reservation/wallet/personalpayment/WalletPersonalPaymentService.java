package com.rpb.reservation.wallet.personalpayment;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.domain.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.funding.application.WalletFundingService;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.*;

@Service
@Transactional(readOnly=true)
public class WalletPersonalPaymentService {
 private static final String CHARGE="bill.personal_scan_charge";
 private final WalletAccountRepository accounts;private final WalletBillRepository bills;private final WalletCommandRepository commands;
 private final WalletPersonalPaymentRepository personal;private final WalletCustomerIdentityRepository identities;
 private final WalletAccess access;private final WalletDirectory directory;private final WalletFundingService funding;private final Clock clock;
 public WalletPersonalPaymentService(WalletAccountRepository accounts,WalletBillRepository bills,WalletCommandRepository commands,WalletPersonalPaymentRepository personal,WalletCustomerIdentityRepository identities,WalletAccess access,WalletDirectory directory,WalletFundingService funding,Clock clock){this.accounts=accounts;this.bills=bills;this.commands=commands;this.personal=personal;this.identities=identities;this.access=access;this.directory=directory;this.funding=funding;this.clock=clock;}
 public Consent consent(StoreScope s,CustomerAuthPrincipal customer){access.customer(s,customer,false);return personal.consent(s,accounts.requireProgram(s,false).id(),customer.customerId());}
 @Transactional public Consent consent(StoreScope s,CustomerAuthPrincipal customer,ConsentUpdate c){
  access.customer(s,customer,true);if(c==null||c.accepted()==null||c.policyVersion()==null||c.policyVersion()<0||Boolean.TRUE.equals(c.accepted())&&c.limitCents()==null)throw WalletException.invalid();limit(c.limitCents());
  return commands.run(s,customer.customerId(),"personal.consent",c.idempotencyKey(),c,Consent.class,()->{
   var program=accounts.requireProgram(s,true);personal.lockProgram(s,program.id());personal.lockIdentity(s,program.id(),customer.customerId());
   var previous=personal.consent(s,program.id(),customer.customerId());
   if(c.policyVersion()!=previous.policyVersion()||previous.policyVersion()==Integer.MAX_VALUE)throw new WalletException("WALLET_POLICY_STALE",409,"您的付款授权已更新，请刷新后重新确认");
   var saved=new ConsentUpdate(previous.policyVersion()+1,c.limitCents(),c.accepted(),c.idempotencyKey());
   personal.consent(s,program.id(),customer.customerId(),saved);
   // Any customer edit invalidates old payment snapshots; identity remains usable.
   identities.revokePayments(s,program.id(),customer.customerId());
   commands.audit(s,customer.customerId(),"personal.consent",customer.customerId(),details("accepted",c.accepted(),"policyVersion",saved.policyVersion(),"ruleVersion",1,"previousConsentedLimitCents",previous.consentedLimitCents(),"consentedLimitCents",c.limitCents()));
   return personal.consent(s,program.id(),customer.customerId());
  });
 }
 @Transactional public PersonalCode issue(StoreScope s,CustomerAuthPrincipal customer){
  access.customer(s,customer,true);var program=accounts.requireProgram(s,true);personal.lockProgram(s,program.id());personal.lockIdentity(s,program.id(),customer.customerId());
  var consent=personal.consent(s,program.id(),customer.customerId());String token=WalletTokens.issue("WI");var now=clock.instant();var expires=now.plusSeconds(WalletTokens.PERSONAL_CODE_LIFETIME_SECONDS);
  identities.issue(s,program.id(),customer.customerId(),WalletTokens.hash(token),now,expires);personal.snapshot(s,WalletTokens.hash(token),consent);
  return new PersonalCode("customer_personal",token,expires,consent.paymentEnabled(),consent.limitCents(),consent.consentedLimitCents(),consent.policyVersion());
 }
 public CodeStatus status(StoreScope s,CustomerAuthPrincipal customer,String code){
  access.customer(s,customer,false);var program=accounts.requireProgram(s,false);var ref=personal.code(s,program.id(),WalletTokens.required(code,"WI"));
  if(!ref.customerId().equals(customer.customerId()))throw WalletException.missing();
  if(ref.billId()!=null){var b=bills.get(new StoreScope(s.tenantId(),ref.storeId()),ref.billId(),false);return new CodeStatus(b.walletApplied()||b.remainingCents()==0||b.closed()?"used":"confirmation_required",b);}
  return new CodeStatus(ref.expiresAt().isAfter(clock.instant())?"active":"expired",null);
 }
 @Transactional public ChargeResult charge(StoreScope s,CurrentActor actor,ScanCharge c){
  staff(s,actor,true);if(c==null)throw WalletException.invalid();long amount=WalletMoney.cents(c.amount());String hash=WalletTokens.required(c.customerCode(),"WI");
  if(c.description()!=null&&c.description().length()>200)throw WalletException.invalid();
  // Only the irreversible token hash participates in the stored request fingerprint.
  var fingerprint=new ScanCharge(hash,c.amount(),c.description(),c.idempotencyKey());
  return commands.run(s,actor.actorId(),CHARGE,c.idempotencyKey(),fingerprint,ChargeResult.class,()->{
   var program=accounts.requireProgram(s,true);personal.lockProgram(s,program.id());var ref=personal.code(s,program.id(),hash);
   personal.lockIdentity(s,program.id(),ref.customerId());ref=personal.code(s,program.id(),hash);
   if(ref.billId()!=null)throw new WalletException("WALLET_PERSONAL_CODE_USED",409,"个人码已用于付款，请顾客刷新后重新出示");
   if(!ref.expiresAt().isAfter(clock.instant()))throw new WalletException("WALLET_PERSONAL_CODE_EXPIRED",409,"个人码已过期，请顾客刷新后重新出示");
   var consent=personal.consent(s,program.id(),ref.customerId());if(!ref.enabled()||!consent.paymentEnabled())throw disabled();
   directory.customer(s.tenantScope(),ref.customerId());
   var account=accounts.customerAccounts(s,ref.customerId()).stream().filter(a->a.programId().equals(program.id())).findFirst().orElseThrow(()->new WalletException("WALLET_ACCOUNT_REQUIRED",409,"请顾客先开通钱包资金账户"));
   account=accounts.account(s,account.id(),true);accounts.requireActive(account);
   // Account locking may wait; authorization time must be checked after the final blocking lock.
   if(!ref.expiresAt().isAfter(clock.instant()))throw new WalletException("WALLET_PERSONAL_CODE_EXPIRED",409,"个人码已过期，请顾客刷新后重新出示");
   long cap=Math.min(ref.limit(),Math.min(consent.limitCents(),consent.consentedLimitCents()));boolean over=amount>cap;
   if(!over&&account.availableCents()<amount)throw new WalletException("WALLET_INSUFFICIENT_FUNDS",409,"可用余额不足，无法全额支付本次消费");
   var bill=bills.create(s,account,amount,actor.actorId());personal.bind(s,hash,bill.id(),c.description());
   if(!over){var split=funding.debit(s,account,amount,bill.id(),actor.actorId());bills.walletApplied(s,bill,amount);funding.billComponents(s,bill.id(),split);}
   commands.audit(s,actor.actorId(),CHARGE,bill.id(),Map.of("accountId",account.id(),"amountCents",amount,"confirmationRequired",over,"effectiveCapCents",cap,"authorizationLimitCents",ref.limit(),"ruleVersion",1));
   return new ChargeResult(over?"confirmation_required":"paid",bills.get(s,bill.id(),false),accounts.account(s,account.id(),false));
  });
 }
 public ChargeResult result(StoreScope s,CurrentActor actor,String key){staff(s,actor,false);var r=commands.result(s,actor.actorId(),CHARGE,key,ChargeResult.class);if(!s.storeId().value().equals(r.storeId()))throw WalletException.missing();return r;}
 @Transactional public ChargeResult reconcile(StoreScope s,CurrentActor actor,String key){
  staff(s,actor,false);var result=commands.reconcileOrClose(s,actor.actorId(),CHARGE,key,ChargeResult.class,()->new ChargeResult("not_submitted",null,null,s.storeId().value()));
  if(!s.storeId().value().equals(result.storeId()))throw WalletException.missing();return result;
 }
 private void staff(StoreScope s,CurrentActor actor,boolean mutate){access.staff(s,actor,"wallet.charge.create",mutate);access.staff(s,actor,"wallet.collection.confirm",mutate);}
 private static Map<String,Object> details(Object... values){var details=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)details.put((String)values[i],values[i+1]);return details;}
 private static WalletException disabled(){return new WalletException("WALLET_PERSONAL_PAYMENT_DISABLED",409,"请顾客设置并确认个人付款上限后，重新出示个人码");}
 private static void limit(Long limit){if(limit!=null&&(limit<=0||limit>WalletMoney.MAX_CENTS))throw WalletException.invalid();}
}
