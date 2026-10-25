package com.rpb.reservation.cards;
import com.rpb.reservation.catalog.CatalogPostgresTest;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.cards.command.CardCommands;
import com.rpb.reservation.cards.plan.*;
import com.rpb.reservation.cards.entitlement.*;
import com.rpb.reservation.cards.stamp.*;
import com.rpb.reservation.cards.redemption.*;
import com.rpb.reservation.cards.purchase.*;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CardPostgresTest extends CatalogPostgresTest {
 CardPlanService plans;CardHoldingService holdings;StampService stamps;RedemptionService redeem;PurchaseService purchases;CardCommands commands;
 MutableClock clock;CustomerAuthPrincipal principal;CardContextPort context;
 static class MutableClock extends Clock {volatile Instant now=Instant.parse("2026-10-25T01:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}}
 @BeforeEach void cards(){
  clock=new MutableClock();principal=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),customer,"test@example.com","顾客");context=mock(CardContextPort.class);
  when(context.store(scope)).thenReturn(new StoreInfo(issuer,"Asia/Singapore"));
  var access=new CardAccess(context,new CardSettingsRepository(jdbc));tx.execute(s->access.settings(scope,actor,true));
  commands=new CardCommands(jdbc,json);var planRepo=new CardPlanRepository(jdbc,json);var holdingRepo=new CardHoldingRepository(jdbc,json);
  plans=new CardPlanService(access,planRepo,products,commands);holdings=new CardHoldingService(access,planRepo,holdingRepo,commands,clock,products);
  stamps=new StampService(access,planRepo,holdingRepo,commands,clock);
  redeem=new RedemptionService(access,new RedemptionRepository(jdbc),planRepo,holdingRepo,commands,stamps,clock);
  var pay=mock(CardPaymentPort.class);when(pay.confirm(any(),any())).thenAnswer(i->{PaymentConfirm c=i.getArgument(1);return new PaymentReceipt(c.channel(),"cash-account",c.receipt(),c.amountCents());});when(pay.recovery(any(),any())).thenAnswer(i->{PaymentConfirm c=i.getArgument(1);return new PaymentReceipt(c.channel(),"cash-account",c.receipt(),c.amountCents());});
  purchases=new PurchaseService(access,planRepo,holdingRepo,new PurchaseRepository(jdbc,json),commands,products,pay,clock);
 }
 Plan plan(String kind,Integer total,Integer daily,int target){return tx.execute(s->plans.create(scope,actor,new PlanCreate("卡","Card",kind,product.id(),total,daily,30,target,true,7,kind.equals("stamp")?0:20000,"inclusive",java.math.BigDecimal.ZERO,key())));}
 Holding enroll(Plan p){return tx.execute(s->holdings.enroll(scope,principal,new Enroll(p.id(),key())));}
 @Test void tenStampsIssueOneRewardAndSourceCannotBeReused(){var p=plan("stamp",null,20,10);var h=enroll(p);for(int n=0;n<10;n++){int number=n;tx.execute(s->stamps.grant(scope,actor,h.id(),"receipt-"+number,key()));}
  assertThat(holdings.own(scope,principal).stream().filter(c->c.kind().equals("reward"))).hasSize(1);
  assertThatThrownBy(()->tx.execute(s->stamps.grant(scope,actor,h.id(),"receipt-9",key()))).isInstanceOf(RuntimeException.class);
  assertThat(jdbc.queryForObject("select count(*) from card_reward_grants where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
 }
 @Test void periodQuotaExpiresInsteadOfCarryingAndCrossDayReversalDoesNotRefillToday(){var p=plan("period",null,1,0);var h=paid(p);var request=request(h);
  tx.execute(s->redeem.confirm(scope,principal,request.id(),new Confirm(h.id(),"2026-10-25",key())));
  assertThat(holdings.own(scope,principal).getFirst().available()).isZero();clock.now=clock.now.plusSeconds(86400);
  assertThat(holdings.own(scope,principal).getFirst().available()).isEqualTo(1);
  var entry=jdbc.queryForObject("select id from card_entries where tenant_id=? and kind='redeem'",UUID.class,tenant);
  tx.execute(s->redeem.reverse(scope,actor,entry,new Reverse("纠错",key())));
  assertThat(holdings.own(scope,principal).getFirst().available()).isEqualTo(1);
 }
 Holding paid(Plan p){var order=tx.execute(s->purchases.create(scope,principal,new PurchaseCreate(p.id(),key())));tx.execute(s->purchases.confirm(scope,actor,order.id(),new PaymentConfirm("cash","","T1",key(),20000,true,key())));return holdings.own(scope,principal).stream().filter(h->h.kind().equals(p.kind())).findFirst().orElseThrow();}
 Request request(Holding h){return tx.execute(s->redeem.create(scope,actor,new RequestCreate("redeem",h.planId(),h.id(),1,key())));}
 @Test void pendingReadDoesNotConsumeAndTwoDifferentKeysCannotSpendTwice(){var h=paid(plan("count",1,null,0));var r=request(h);var code=tx.execute(s->redeem.issue(scope,actor,r.id()));
  assertThat(redeem.scan(scope,principal,code.code()).status()).isEqualTo("pending");assertThat(holdings.own(scope,principal).getFirst().available()).isEqualTo(1);
  var first=tx.execute(s->redeem.confirm(scope,principal,r.id(),new Confirm(h.id(),"2026-10-25",key())));
  assertThat(tx.execute(s->redeem.confirm(scope,principal,r.id(),new Confirm(h.id(),"2026-10-25",key()))).id()).isEqualTo(first.id());
  assertThat(jdbc.queryForObject("select count(*) from card_entries where tenant_id=? and kind='redeem'",Integer.class,tenant)).isEqualTo(1);
 }
 @Test void lastUseConcurrentRequestsHaveOnlyOneWinner()throws Exception{var h=paid(plan("count",1,null,0));var a=request(h);var b=request(h);var pool=Executors.newFixedThreadPool(2);try{
  var tasks=List.of(a,b).stream().map(r->pool.submit(()->{try{tx.execute(s->redeem.confirm(scope,principal,r.id(),new Confirm(h.id(),"2026-10-25",key())));return true;}catch(RuntimeException e){return false;}})).toList();
  assertThat(tasks.get(0).get()?1:0).isEqualTo(tasks.get(1).get()?0:1);
 }finally{pool.shutdownNow();}assertThat(holdings.own(scope,principal).getFirst().remaining()).isZero();}
 @Test void unconsumedPurchaseRefundIsLinkedAndUsedCardCannotRefund(){var p=plan("count",30,2,0);var order=tx.execute(s->purchases.create(scope,principal,new PurchaseCreate(p.id(),key())));var c=new PaymentConfirm("cash","","T1",key(),20000,true,key());
  tx.execute(s->purchases.confirm(scope,actor,order.id(),c));tx.execute(s->purchases.confirm(scope,actor,order.id(),c));assertThat(holdings.own(scope,principal)).hasSize(1);
  tx.execute(s->purchases.refund(scope,actor,order.id(),new Refund("退卡","refund-1",true,key())));assertThat(holdings.own(scope,principal).getFirst().status()).isEqualTo("refunded");
 }
 @Test void recoveryClosesUnsubmittedKeyAndLateCommandCannotExecute(){String key=key();tx.execute(s->commands.reconcile(scope,customer,"enroll",key));assertThatThrownBy(()->tx.execute(s->holdings.enroll(scope,principal,new Enroll(plan("stamp",null,1,10).id(),key)))).hasMessageContaining("关闭");}
 @Test void productWithdrawalBlocksNewEnrollmentButExistingRewardsStillRedeem(){
  var p=plan("stamp",null,20,1);var h=enroll(p);tx.execute(s->stamps.grant(scope,actor,h.id(),"sale-1",key()));
  tx.execute(s->catalog.status(scope,actor,product.id(),new com.rpb.reservation.catalog.application.CatalogDtos.Status("inactive",product.version(),key())));
  var other=UUID.randomUUID();jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,status) values(?,?,'other','regular','Other','active')",other,tenant);
  var stranger=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),other,"other@test.invalid","Other");
  assertThatThrownBy(()->tx.execute(s->holdings.enroll(scope,stranger,new Enroll(p.id(),key())))).hasMessageContaining("上架");
  var reward=holdings.own(scope,principal).stream().filter(c->c.kind().equals("reward")).findFirst().orElseThrow();var r=request(reward);
  assertThat(tx.execute(s->redeem.confirm(scope,principal,r.id(),new Confirm(reward.id(),"2026-10-25",key()))).status()).isEqualTo("completed");
 }
 @Test void authorizationIsForOneIntentAndOwnerAndNoMutationOccursAtPreview(){
  var h=paid(plan("count",2,1,0));var r=request(h);var stranger=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),UUID.randomUUID(),"other@test.invalid","Other");
  assertThatThrownBy(()->tx.execute(s->redeem.confirm(scope,stranger,r.id(),new Confirm(h.id(),"2026-10-25",key())))).isInstanceOf(com.rpb.reservation.cards.application.CardException.class);
  assertThatThrownBy(()->tx.execute(s->redeem.confirm(scope,principal,r.id(),new Confirm(h.id(),"2026-10-24",key())))).hasMessageContaining("日期");
  var code=tx.execute(s->redeem.authorize(scope,principal,r.id(),new Confirm(h.id(),"2026-10-25",key())));
  assertThat(holdings.own(scope,principal).getFirst().remaining()).isEqualTo(2);
  tx.execute(s->redeem.authorized(scope,actor,new Authorized(code.code(),"2026-10-25",key())));
  assertThatThrownBy(()->tx.execute(s->redeem.authorized(scope,actor,new Authorized(code.code(),"2026-10-25",key())))).isInstanceOf(RuntimeException.class);
  assertThat(holdings.own(scope,principal).getFirst().remaining()).isEqualTo(1);
 }
 @Test void expiredRequestAndDisabledCardFeatureLeaveNoEntries(){var h=paid(plan("count",1,1,0));var r=request(h);clock.now=clock.now.plusSeconds(121);assertThatThrownBy(()->tx.execute(s->redeem.confirm(scope,principal,r.id(),new Confirm(h.id(),"2026-10-25",key())))).hasMessageContaining("过期");
  tx.execute(s->jdbc.update("update card_settings set enabled=false where tenant_id=?",tenant));assertThatThrownBy(()->tx.execute(s->redeem.create(scope,actor,new RequestCreate("redeem",h.planId(),h.id(),1,key())))).hasMessageContaining("启用");assertThat(holdings.own(scope,principal).getFirst().remaining()).isEqualTo(1);}
 @Test void externalReceiptCannotFundBothWalletAndCards(){var registry=new com.rpb.reservation.payment.application.OfflineReceiptRegistry(new com.rpb.reservation.payment.persistence.OfflineReceiptClaimRepository(jdbc));tx.execute(s->{registry.claim(scope,UUID.randomUUID(),"wallet","paynow","paynow:uen:TEST","BANK-1");return null;});assertThatThrownBy(()->tx.execute(s->{registry.claim(scope,UUID.randomUUID(),"cards","paynow","paynow:uen:TEST","BANK-1");return null;})).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
 @Test void verifiedMoneyAfterQuoteExpiryIsRetainedAndRefundableWithoutIssuingCard(){var p=plan("count",30,2,0);var order=tx.execute(s->purchases.create(scope,principal,new PurchaseCreate(p.id(),key())));clock.now=clock.now.plusSeconds(301);var payment=new PaymentConfirm("cash","","T1","ACTUAL-RECEIPT",20000,true,key());assertThatThrownBy(()->tx.execute(s->purchases.confirm(scope,actor,order.id(),payment))).hasMessageContaining("过期");
  var recorded=tx.execute(s->purchases.exception(scope,actor,order.id(),new ExceptionPayment(payment,"顾客在报价过期后已付款",key())));assertThat(recorded.status()).isEqualTo("exception");assertThat(holdings.own(scope,principal)).isEmpty();
  var refunded=tx.execute(s->purchases.refund(scope,actor,order.id(),new Refund("异常退款","REFUND-EX",true,key())));assertThat(refunded.status()).isEqualTo("exception_refunded");
 }
 @Test void midnightCannotMoveCustomerAuthorizationIntoNextDay(){var h=paid(plan("period",null,1,0));clock.now=Instant.parse("2026-10-25T15:59:30Z");var r=request(h);var code=tx.execute(s->redeem.authorize(scope,principal,r.id(),new Confirm(h.id(),"2026-10-25",key())));clock.now=Instant.parse("2026-10-25T16:00:20Z");assertThatThrownBy(()->tx.execute(s->redeem.authorized(scope,actor,new Authorized(code.code(),"2026-10-26",key())))).hasMessageContaining("日期");assertThat(holdings.own(scope,principal).getFirst().usedToday()).isZero();}
 @Test void oldStampCannotRevokeAnotherRoundsUnusedReward(){var h=enroll(plan("stamp",null,10,1));tx.execute(s->stamps.grant(scope,actor,h.id(),"round-A",key()));var original=jdbc.queryForObject("select id from card_entries where tenant_id=? and kind='stamp' and source='round-A'",UUID.class,tenant);var reward=holdings.own(scope,principal).stream().filter(c->c.kind().equals("reward")).findFirst().orElseThrow();var r=request(reward);tx.execute(s->redeem.confirm(scope,principal,r.id(),new Confirm(reward.id(),"2026-10-25",key())));tx.execute(s->stamps.grant(scope,actor,h.id(),"round-B",key()));assertThatThrownBy(()->tx.execute(s->stamps.reverse(scope,actor,original,new Reverse("撤回旧轮次",key())))).hasMessageContaining("倒序");assertThat(holdings.own(scope,principal).stream().filter(c->c.kind().equals("reward")&&c.status().equals("active")&&c.remaining()==1)).hasSize(1);}
 @Test void singleStampNonRepeatingCardCanRetryAfterUnusedRewardReversal(){var p=tx.execute(s->plans.create(scope,actor,new PlanCreate("单次卡","Single","stamp",product.id(),null,10,30,1,false,7,0,"inclusive",java.math.BigDecimal.ZERO,key())));var h=enroll(p);tx.execute(s->stamps.grant(scope,actor,h.id(),"original",key()));var entry=jdbc.queryForObject("select id from card_entries where tenant_id=? and kind='stamp'",UUID.class,tenant);tx.execute(s->stamps.reverse(scope,actor,entry,new Reverse("登记错误",key())));tx.execute(s->stamps.grant(scope,actor,h.id(),"correct",key()));assertThat(holdings.own(scope,principal).stream().filter(c->c.kind().equals("reward")&&c.status().equals("active"))).hasSize(1);}
 @Test void cursorReachesOlderHoldingsBeyondFiveHundredWithoutDuplicates(){var h=paid(plan("count",1,null,0));jdbc.update("insert into customer_cards(id,tenant_id,store_id,customer_id,plan_id,kind,remaining,initial_total,daily_limit,timezone,starts_at,expires_at,status,snapshot) select gen_random_uuid(),tenant_id,store_id,customer_id,plan_id,kind,remaining,initial_total,daily_limit,timezone,starts_at,expires_at,status,snapshot from customer_cards cross join generate_series(1,505) where customer_cards.id=?",h.id());var first=holdings.own(scope,principal);assertThat(first).hasSize(500);var second=holdings.own(scope,principal,first.getLast().id());assertThat(second).hasSize(6);assertThat(first.stream().map(Holding::id).toList()).doesNotContainAnyElementsOf(second.stream().map(Holding::id).toList());}
}
