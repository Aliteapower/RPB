package com.rpb.reservation.cards.history;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional
public class CardReportService {
 private final CardAccess access;private final CardReportRepository r;private final com.rpb.reservation.cards.entitlement.CardDailyExpiryRepository expiry;private final Clock clock;
 public CardReportService(CardAccess access,CardReportRepository r,com.rpb.reservation.cards.entitlement.CardDailyExpiryRepository expiry,Clock clock){this.access=access;this.r=r;this.expiry=expiry;this.clock=clock;}
 public CardReportRepository.Report query(StoreScope s,CurrentActor a,LocalDate from,LocalDate to){access.staff(s,a,"wallet.cards.view",false);if(from==null||to==null||to.isBefore(from)||to.isAfter(from.plusYears(5)))throw CardException.invalid();expiry.settleScope(s,null,clock.instant());return r.query(s,from,to,access.store(s).timezone());}
}
