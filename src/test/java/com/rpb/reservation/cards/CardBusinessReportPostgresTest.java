package com.rpb.reservation.cards;

import com.rpb.reservation.wallet.cards.report.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.rpb.reservation.wallet.cards.report.CardBusinessReport.*;

class CardBusinessReportPostgresTest extends CardPostgresTest {
 private Page report(String section,String kind,String query,UUID plan,int page,int size){return new CardBusinessReportRepository(jdbc).query(scope,new Filter(LocalDate.parse("2026-10-25"),LocalDate.parse("2026-10-26"),section,query,plan,kind,page,size),"Asia/Singapore",clock.instant());}
 private UUID entry(com.rpb.reservation.cards.application.CardDtos.Holding h,String kind,int quantity,UUID original){
  UUID id=UUID.randomUUID();jdbc.update("insert into card_entries(id,tenant_id,store_id,card_id,customer_id,plan_id,kind,quantity,business_day,source,actor_id,original_id,created_at) values(?,?,?,?,?,?,?,?,?::date,?,?,?,?::timestamptz)",id,tenant,store,h.id(),h.customerId(),h.planId(),kind,quantity,"2026-10-25",id.toString(),actor.actorId(),original,"2026-10-26T01:00:00Z");return id;
 }
 @Test void reportPaginatesAllMatchingRowsAndSeparatesStampReversalAndUnits(){
  var h=paid(plan("count",1000,1000,0));
  tx.execute(s->{for(int i=0;i<503;i++)entry(h,"redeem",1,null);return null;});
  var original=entry(h,"stamp",2,null);entry(h,"stamp_reverse",1,original);
  var first=report("business","all","91234567",null,0,25);assertThat(first.total()).isEqualTo(506);assertThat(first.items()).hasSize(25);
  assertThat(first.summary().stream().mapToLong(Summary::redeemed).sum()).isEqualTo(503);assertThat(first.summary().stream().filter(r->r.unit().equals("card")).mapToLong(Summary::granted).sum()).isEqualTo(1);
  assertThat(first.summary().stream().mapToLong(Summary::stamps).sum()).isEqualTo(2);assertThat(first.summary().stream().mapToLong(Summary::stampReversed).sum()).isEqualTo(1);
  var second=report("business","all","91234567",null,1,25);assertThat(second.total()).isEqualTo(first.total());assertThat(second.items().stream().map(Row::id)).doesNotContainAnyElementsOf(first.items().stream().map(Row::id).toList());
  var stamps=report("business","stamp","",null,0,25);assertThat(stamps.items().getFirst().reversed()).isTrue();assertThat(stamps.items().getFirst().reversible()).isFalse();assertThat(stamps.items().getFirst().unit()).isEqualTo("stamp");
 }
 @Test void reportScopesCustomerPlanStoreAndTenantAndEscapesWildcards(){
  var h=paid(plan("count",30,1,0));entry(h,"redeem",1,null);
  jdbc.update("update card_payment_links set created_at='2026-10-25T01:00:00Z' where tenant_id=? and store_id=?",tenant,store);
  assertThat(report("business","all","not-found",null,0,25).total()).isZero();assertThat(report("business","all","%",null,0,25).total()).isZero();assertThat(report("business","all","",UUID.randomUUID(),0,25).total()).isZero();
  var f=new Filter(LocalDate.parse("2026-10-25"),LocalDate.parse("2026-10-26"),"business","",null,"all",0,25);
  var repo=new CardBusinessReportRepository(jdbc);assertThat(repo.query(new StoreScope(new TenantId(UUID.randomUUID()),store),f,"Asia/Singapore",clock.instant()).total()).isZero();assertThat(repo.query(new StoreScope(scope.tenantId(),UUID.randomUUID()),f,"Asia/Singapore",clock.instant()).total()).isZero();
  var result=report("business","all","顾客",h.planId(),0,25);assertThat(result.items()).hasSize(2);assertThat(result.money().receivedCents()).isEqualTo(20000);assertThat(result.items().getFirst().createdAt()).isEqualTo(Instant.parse("2026-10-26T01:00:00Z"));assertThat(result.items().getFirst().businessDay()).isEqualTo(LocalDate.parse("2026-10-25"));
  jdbc.update("update customer_cards set expires_at='2026-10-26T12:00:00Z' where id=?",h.id());clock.now=Instant.parse("2026-10-27T01:00:00Z");assertThat(report("business","redeem","",h.planId(),0,25).items().getFirst().reversible()).isTrue();
 }
 @Test void reportIncludesDerivedUnlimitedPeriodExpiryWithoutCreatingLedgerRows(){
  var h=paid(plan("period",null,3,0));clock.now=Instant.parse("2026-10-27T01:00:00Z");
  var expiry=report("expiry","all","",h.planId(),0,25);assertThat(expiry.total()).isEqualTo(2);assertThat(expiry.summary().stream().mapToLong(Summary::expired).sum()).isEqualTo(6);
  assertThat(expiry.items()).allSatisfy(r->{assertThat(r.id()).startsWith("expiry:");assertThat(r.createdAt()).isNull();assertThat(r.reversible()).isFalse();});
  assertThat(jdbc.queryForObject("select count(*) from card_entries where card_id=? and kind='daily_expire'",Integer.class,h.id())).isZero();
 }
 @Test void reportSeparatesRecordedExpiryFromConsumption(){
  var h=paid(plan("count",30,1,0));entry(h,"daily_expire",1,null);
  assertThat(report("expiry","all","",h.planId(),0,25).summary().stream().mapToLong(Summary::expired).sum()).isEqualTo(1);
  assertThat(report("business","all","",h.planId(),0,25).items()).noneMatch(r->r.kind().equals("daily_expire"));
 }
 @Test void reportGroupsByCustomerWithoutTruncatingDirectoryMatches(){
  var h=paid(plan("count",30,1,0));entry(h,"redeem",1,null);var other=UUID.randomUUID();
  jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,phone_e164,status) values(?,?,'report-other','regular','第二顾客','+6581234567','active')",other,tenant);
  var r=new com.rpb.reservation.cards.entitlement.CardHoldingRepository(jdbc,json,new com.rpb.reservation.cards.entitlement.CardDailyExpiryRepository(jdbc));
  var second=tx.execute(s->r.issue(scope,other,h.snapshot(),"count",30,1,"Asia/Singapore",clock.instant(),clock.instant().plusSeconds(86400*30)));entry(second,"redeem",1,null);
  var result=new CardBusinessReportRepository(jdbc).query(scope,new Filter(LocalDate.parse("2026-10-25"),LocalDate.parse("2026-10-26"),"business","",null,"redeem",0,25,"customer"),"Asia/Singapore",clock.instant());
  assertThat(result.summary()).hasSize(2);assertThat(result.summary().stream().map(Summary::groupId)).containsExactlyInAnyOrder(customer.toString(),other.toString());assertThat(result.summary().stream().mapToLong(Summary::redeemed).sum()).isEqualTo(2);
 }
 @Test void reportCountsReturnedCardsWithoutRewritingOriginalEntitlementQuantity(){
  var h=paid(plan("count",30,1,0));var order=jdbc.queryForObject("select id from card_purchase_orders where card_id=?",UUID.class,h.id());
  tx.execute(s->purchases.refund(scope,actor,order,new com.rpb.reservation.cards.application.CardDtos.Refund("未使用退卡","REPORT-REFUND",true,key())));
  jdbc.update("update card_payment_links set created_at='2026-10-25T01:00:00Z' where tenant_id=? and store_id=?",tenant,store);
  var result=report("business","all","",h.planId(),0,25);assertThat(result.money().receivedCents()).isEqualTo(20000);assertThat(result.money().refundedCents()).isEqualTo(20000);
  assertThat(result.summary().stream().mapToLong(Summary::granted).sum()).isEqualTo(1);assertThat(result.summary().stream().mapToLong(Summary::returned).sum()).isEqualTo(1);
  var refund=report("business","refund","",h.planId(),0,25).items().getFirst();assertThat(refund.unit()).isEqualTo("card");assertThat(refund.quantity()).isEqualTo(1);assertThat(refund.ledgerQuantity()).isEqualTo(30);
  assertThat(jdbc.queryForObject("select quantity from card_entries where card_id=? and kind='refund'",Integer.class,h.id())).isEqualTo(30);
 }
}
