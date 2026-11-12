package com.rpb.reservation.wallet.cards.report;

import java.time.*;
import java.util.*;

public final class CardBusinessReport {
 private CardBusinessReport(){}
 public record Filter(LocalDate from,LocalDate to,String section,String query,UUID planId,String kind,int page,int size,String groupBy){
  public Filter(LocalDate from,LocalDate to,String section,String query,UUID planId,String kind,int page,int size){this(from,to,section,query,planId,kind,page,size,"plan");}
 }
 public record Row(String id,UUID cardId,UUID customerId,String customerName,String customerPhone,UUID planId,String nameZh,String nameEn,String productZh,String productEn,String kind,String unit,int quantity,int ledgerQuantity,Integer beforeTotal,Integer afterTotal,int beforeDaily,int afterDaily,LocalDate businessDay,Instant createdAt,String source,UUID originalId,String reason,boolean reversed,boolean reversible){}
 public record Summary(UUID planId,String nameZh,String nameEn,String unit,long count,long redeemed,long redeemReversed,long stamps,long stampReversed,long granted,long revoked,long expired,long returned,String groupId,String groupPhone){}
 public record Money(long receivedCents,long refundedCents){}
 public record Page(String timezone,int page,int size,long total,List<Row> items,List<Summary> summary,Money money){}
}
