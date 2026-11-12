package com.rpb.reservation.cards.entitlement;
import java.time.*;
public final class CardRules {
 private CardRules() {}
 public static LocalDate day(Instant now,String zone){return now.atZone(ZoneId.of(zone)).toLocalDate();}
 public static Instant expiry(Instant now,String zone,int days){if(days<1||days>3660)throw new IllegalArgumentException("Invalid days");return day(now,zone).plusDays(days).atStartOfDay(ZoneId.of(zone)).toInstant();}
 public static int expiredDaily(int remaining,int dailyLimit,int used){return Math.min(Math.max(remaining,0),Math.max(0,dailyLimit-used));}
 public static int available(Integer remaining,Integer dailyLimit,int used){return Math.max(0,Math.min(remaining==null?Integer.MAX_VALUE:remaining,dailyLimit==null?Integer.MAX_VALUE:dailyLimit-used));}
}
