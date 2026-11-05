package com.rpb.reservation.wallet.cards.report;

import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.history.CardReportService;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.rpb.reservation.wallet.cards.report.CardBusinessReport.*;

@Service
public class CardBusinessReportService {
 private final CardReportService cards;private final CardBusinessReportRepository reports;
 public CardBusinessReportService(CardReportService cards,CardBusinessReportRepository reports){this.cards=cards;this.reports=reports;}
 @Transactional(isolation=Isolation.REPEATABLE_READ)
 public Page query(StoreScope scope,CurrentActor actor,Filter f){
  validate(f);var context=cards.prepare(scope,actor);
  return reports.query(scope,new Filter(f.from(),f.to(),f.section(),f.query().trim(),f.planId(),f.kind(),f.page(),f.size(),f.groupBy()),context.timezone(),context.now());
 }
 static void validate(Filter f){
  if(f==null||f.from()==null||f.to()==null||f.to().isBefore(f.from())||f.to().isAfter(f.from().plusDays(365))||f.query()==null||f.query().length()>120||f.section()==null||f.kind()==null||f.page()<0||f.page()>100000||f.size()<1||f.size()>100
   ||f.groupBy()==null||!Set.of("plan","customer").contains(f.groupBy())||!Set.of("business","expiry").contains(f.section())||!Set.of("all","redeem","stamp","grant","refund","reversal").contains(f.kind())||f.section().equals("expiry")&&!f.kind().equals("all"))throw CardException.invalid();
 }
}
