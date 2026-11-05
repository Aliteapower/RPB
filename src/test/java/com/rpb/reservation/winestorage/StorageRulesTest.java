package com.rpb.reservation.winestorage;

import com.rpb.reservation.winestorage.domain.StorageRules;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageRulesTest {
 @Test void unlimitedStorageHasNoExpiryButStillEnforcesQuantities() {
  var now=Instant.parse("2099-01-01T00:00:00Z");
  assertThat(StorageRules.expires(now,"Asia/Singapore",0)).isNull();
  assertThat(StorageRules.available(4,false,null,now,2)).isTrue();
  assertThat(StorageRules.available(4,false,null,now,5)).isFalse();
  assertThat(StorageRules.available(4,false,null,now,0)).isFalse();
  assertThat(StorageRules.available(1,true,null,now,2)).isFalse();
  assertThatThrownBy(()->StorageRules.expires(now,"Asia/Singapore",-1)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->StorageRules.expires(now,"Asia/Singapore",121)).isInstanceOf(RuntimeException.class);
 }
 @Test void calendarMonthsClampAtMonthEndInStoreZone() {
  assertThat(StorageRules.expires(Instant.parse("2026-01-31T13:30:00Z"),"Asia/Singapore",1))
   .isEqualTo(Instant.parse("2026-02-28T13:30:00Z"));
  assertThat(StorageRules.expires(Instant.parse("2024-01-31T16:30:00Z"),"America/New_York",1))
   .isEqualTo(Instant.parse("2024-02-29T16:30:00Z"));
  assertThat(StorageRules.expires(Instant.parse("2026-01-31T13:30:00Z"),"America/New_York",3))
   .isEqualTo(Instant.parse("2026-04-30T12:30:00Z"));
 }
 @Test void expiryIsExclusiveAndAnIntentNeverExtendsIt() {
  var end=Instant.parse("2026-10-25T00:00:00Z");
  assertThat(StorageRules.available(4,false,end,end.minusNanos(1),2)).isTrue();
  assertThat(StorageRules.available(4,false,end,end,2)).isFalse();
  assertThat(StorageRules.available(4,false,end,end.plusSeconds(1),2)).isFalse();
  assertThat(StorageRules.available(1,true,end,end.minusSeconds(1),2)).isFalse();
 }
}
