package com.rpb.reservation.wallet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.wallet.application.WalletViews.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WalletCachedResponseTest {
 final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 @Test void topupDerivedResponseFieldsAreSerializedAndIgnoredOnIdempotencyReplay()throws Exception{
  var topup=new Topup(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),10000,"cash","credited",0,1,Instant.now(),1000,"proportional","staff",null,Instant.now(),Map.of("name","活动"),Map.of("policyVersion",1));
  String encoded=json.writeValueAsString(topup);assertThat(encoded).contains("\"principalCents\":10000","\"creditTotalCents\":11000");assertThat(json.readValue(encoded,Topup.class)).isEqualTo(topup);
 }
 @Test void oldTopupCachedJsonGetsStableDefaultsWithoutCurrentAccountLookup()throws Exception{
  String cached="{\"id\":\""+UUID.randomUUID()+"\",\"amountCents\":10000,\"channel\":\"cash\",\"status\":\"credited\"}";
  var topup=json.readValue(cached,Topup.class);assertThat(topup.deductionPolicy()).isEqualTo("proportional");assertThat(topup.origin()).isEqualTo("staff");assertThat(topup.offerSnapshot()).isEmpty();assertThat(topup.policySnapshot()).isEmpty();assertThat(topup.bonusCents()).isZero();assertThat(json.readValue(json.writeValueAsString(topup),Topup.class)).isEqualTo(topup);
 }
 @Test void refundDerivedComponentsAndFundingAllocationsRoundTrip()throws Exception{
  var refund=new Refund(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,5000,5500,"completed","退出",1,Instant.now(),null,5000L,500L);
  String encoded=json.writeValueAsString(refund);assertThat(encoded).contains("\"cashPrincipalRefundCents\":5000","\"bonusRevokeCents\":500");assertThat(json.readValue(encoded,Refund.class)).isEqualTo(refund);
  var entry=new Entry(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"bill_debit",UUID.randomUUID(),5500,11000,5500,2,Instant.now(),5000L,500L,List.of(new FundingPart(UUID.randomUUID(),5000,500)));
  assertThat(json.readValue(json.writeValueAsString(entry),Entry.class)).isEqualTo(entry);
 }
}
