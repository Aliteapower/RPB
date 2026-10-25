package com.rpb.reservation.wallet.transactions;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.domain.WalletException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class WalletBusinessReportService {
    private final WalletAccess access;
    private final WalletDirectory directory;
    private final WalletBusinessReportRepository repository;
    public WalletBusinessReportService(WalletAccess access,WalletDirectory directory,WalletBusinessReportRepository repository){this.access=access;this.directory=directory;this.repository=repository;}
    public WalletBusinessReport query(StoreScope scope,CurrentActor actor,Map<String,String> input){
        access.staff(scope,actor,"wallet.reconcile.view",false);
        if("platform_admin".equals(actor.actorType()))throw WalletException.forbidden();
        String period=input.get("period"),date=input.get("date");
        if(!Set.of("period","date").containsAll(input.keySet())||period==null||!Set.of("day","month","year").contains(period)||date==null||!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw WalletException.invalid();
        LocalDate selected;
        try{selected=LocalDate.parse(date);}catch(DateTimeException e){throw WalletException.invalid();}
        if(selected.getYear()<1900||selected.getYear()>9999)throw WalletException.invalid();
        LocalDate start=switch(period){case "month"->selected.withDayOfMonth(1);case "year"->selected.withDayOfYear(1);default->selected;};
        LocalDate end=switch(period){case "month"->start.plusMonths(1).minusDays(1);case "year"->start.plusYears(1).minusDays(1);default->start;};
        String timezone=directory.store(scope).timezone();var zone=ZoneId.of(timezone);
        var rows=repository.query(scope,start.atStartOfDay(zone).toInstant(),end.plusDays(1).atStartOfDay(zone).toInstant(),timezone,period.equals("year")?"month":"day",actor.hasPermission("wallet.points.view"));
        return new WalletBusinessReport(period,start,end,timezone,"SGD",List.copyOf(rows));
    }
}
