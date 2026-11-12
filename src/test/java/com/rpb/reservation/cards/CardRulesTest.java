package com.rpb.reservation.cards;
import com.rpb.reservation.cards.entitlement.CardRules;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CardRulesTest {
 @Test void quotaNeverCarriesOverAndTotalRemainsIndependent() {
  assertThat(CardRules.available(28,2,2)).isZero();assertThat(CardRules.available(28,2,0)).isEqualTo(2);
  assertThat(CardRules.available(null,1,0)).isEqualTo(1);assertThat(CardRules.available(1,2,0)).isEqualTo(1);
 }
 @Test void purchaseDayIsFirstCalendarDayWithExclusiveExpiry() {
  assertThat(CardRules.expiry(Instant.parse("2026-10-25T15:59:00Z"),"Asia/Singapore",30)).isEqualTo(Instant.parse("2026-11-23T16:00:00Z"));
  assertThat(CardRules.day(Instant.parse("2026-10-25T16:00:00Z"),"Asia/Singapore")).isEqualTo(LocalDate.parse("2026-10-26"));
 }
}
