package com.rpb.reservation.wallet.transactions;

import com.rpb.reservation.wallet.domain.WalletException;
import java.time.*;
import java.util.*;

public record TransactionQuery(String type,String status,String reference,String customerQuery,String targetStoreId,
    int limit,String cursor,TransactionViews.Period period) {
    public static TransactionQuery parse(Map<String,String> q,Clock clock,String zone,boolean customer) {
        Set<String> allowed=Set.of("period","year","month","startDate","endDate","type","status","reference","customerQuery","targetStoreId","limit","cursor");
        if(!allowed.containsAll(q.keySet()) || customer&&q.containsKey("customerQuery"))throw WalletException.invalid();
        try {
            String mode=q.getOrDefault("period","month"); LocalDate today=LocalDate.now(clock.withZone(ZoneId.of(zone))),start,end;
            int year=Integer.parseInt(q.getOrDefault("year",Integer.toString(today.getYear())));
            switch(mode) {
                case "today" -> {start=today;end=today;}
                case "month" -> {start=LocalDate.of(year,Integer.parseInt(q.getOrDefault("month",Integer.toString(today.getMonthValue()))),1);end=start.withDayOfMonth(start.lengthOfMonth());}
                case "year" -> {start=LocalDate.of(year,1,1);end=LocalDate.of(year,12,31);}
                case "range" -> {start=LocalDate.parse(q.get("startDate"));end=LocalDate.parse(q.get("endDate"));}
                default -> throw WalletException.invalid();
            }
            if(start.isAfter(end)||start.getYear()<1900||end.getYear()>9998)throw WalletException.invalid();
            String type=q.getOrDefault("type","all"),status=q.getOrDefault("status","all");
            if(!Set.of("all","payment","topup","points_grant","points_deduct","points_redemption","refund","reversal").contains(type)||!Set.of("all","pending","completed","cancelled").contains(status))throw WalletException.invalid();
            int limit=Integer.parseInt(q.getOrDefault("limit","25"));if(limit<1||limit>100)throw WalletException.invalid();
            String ref=q.getOrDefault("reference","").trim(),search=q.getOrDefault("customerQuery","").trim();
            if(ref.length()>200||search.length()>200||q.getOrDefault("cursor","").length()>2000)throw WalletException.invalid();
            return new TransactionQuery(type,status,ref,search,q.getOrDefault("targetStoreId",""),limit,q.get("cursor"),new TransactionViews.Period(mode,start,end,zone));
        } catch(DateTimeException|NumberFormatException|NullPointerException e){throw WalletException.invalid();}
    }
}
