package com.rpb.reservation.cards.history;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class CardReportService {
 private final CardAccess access;private final CardReportRepository r;
 public CardReportService(CardAccess access,CardReportRepository r){this.access=access;this.r=r;}
 public CardReportRepository.Report query(StoreScope s,CurrentActor a,LocalDate from,LocalDate to){access.staff(s,a,"wallet.cards.view",false);if(from==null||to==null||to.isBefore(from)||to.isAfter(from.plusYears(5)))throw CardException.invalid();return r.query(s,from,to,access.store(s).timezone());}
}
