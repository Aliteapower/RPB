package com.rpb.reservation.wallet.cards.report;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.entitlement.CardDailyExpiryRepository;
import com.rpb.reservation.cards.history.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.rpb.reservation.wallet.cards.report.CardBusinessReport.*;
class CardBusinessReportServiceTest {
 @Test void authorizationRunsBeforeSettlementAndBadFiltersNeverRunQueries(){
  var cards=mock(CardReportService.class);var repo=mock(CardBusinessReportRepository.class);var service=new CardBusinessReportService(cards,repo);var scope=new StoreScope(new TenantId(UUID.randomUUID()),UUID.randomUUID());var actor=mock(CurrentActor.class);
  var invalid=new Filter(LocalDate.parse("2026-01-01"),LocalDate.parse("2028-01-01"),"business","",null,"all",0,25);
  assertThatThrownBy(()->service.query(scope,actor,invalid)).isInstanceOf(CardException.class);verifyNoInteractions(cards,repo);
  doThrow(CardException.forbidden()).when(cards).prepare(scope,actor);var valid=new Filter(LocalDate.now(),LocalDate.now(),"business","",null,"all",0,25);assertThatThrownBy(()->service.query(scope,actor,valid)).isInstanceOf(CardException.class);verifyNoInteractions(repo);
 }
 @Test void cardFacadeOwnsAuthorizationAndSettlementWithOneClockSnapshot(){
  var access=mock(CardAccess.class);var expiry=mock(CardDailyExpiryRepository.class);var repo=mock(CardReportRepository.class);var clock=Clock.fixed(Instant.parse("2026-11-03T01:00:00Z"),ZoneOffset.UTC);var cards=new CardReportService(access,repo,expiry,clock);var scope=new StoreScope(new TenantId(UUID.randomUUID()),UUID.randomUUID());var actor=mock(CurrentActor.class);
  doThrow(CardException.forbidden()).when(access).staff(scope,actor,"wallet.cards.view",false);assertThatThrownBy(()->cards.prepare(scope,actor)).isInstanceOf(CardException.class);verifyNoInteractions(expiry,repo);
  doNothing().when(access).staff(scope,actor,"wallet.cards.view",false);when(access.store(scope)).thenReturn(new CardDtos.StoreInfo(UUID.randomUUID(),"Asia/Singapore"));var result=cards.prepare(scope,actor);assertThat(result.now()).isEqualTo(clock.instant());verify(expiry).settleScope(scope,null,clock.instant());assertThat(result.timezone()).isEqualTo("Asia/Singapore");
 }
 @Test void invalidKindPaginationAndExpiryFiltersAreRejected(){
  for(var f:List.of(new Filter(LocalDate.now(),LocalDate.now(),"business","",null,"unknown",0,25),new Filter(LocalDate.now(),LocalDate.now(),"business","",null,"all",-1,25),new Filter(LocalDate.now(),LocalDate.now(),"business","",null,"all",0,101),new Filter(LocalDate.now(),LocalDate.now(),"expiry","",null,"stamp",0,25)))assertThatThrownBy(()->CardBusinessReportService.validate(f)).isInstanceOf(CardException.class);
 }
}
