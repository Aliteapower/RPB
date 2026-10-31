package com.rpb.reservation.cards.application;
import com.rpb.reservation.catalog.application.CatalogDtos.Product;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class CardDtos {
 private CardDtos(){}
 public record StoreInfo(UUID issuerId,String timezone){}
 public record Settings(boolean enabled){}
 public record PlanCreate(String nameZh,String nameEn,String kind,UUID productId,Integer total,Integer dailyLimit,int validDays,int stampTarget,boolean repeatable,int rewardDays,long priceCents,String taxMode,BigDecimal taxRate,String idempotencyKey){}
 public record Plan(UUID id,UUID storeId,String nameZh,String nameEn,String kind,UUID productId,Integer total,Integer dailyLimit,int validDays,int stampTarget,boolean repeatable,int rewardDays,long priceCents,String taxMode,BigDecimal taxRate,Product product,String status,int version){}
 public record PlanStatus(String status,int expectedVersion,String idempotencyKey){}
 public record Enroll(UUID planId,String idempotencyKey){}
 public record Holding(UUID id,UUID customerId,UUID planId,String kind,Integer remaining,Integer initialTotal,Integer dailyLimit,int progress,int round,String timezone,Instant startsAt,Instant expiresAt,String status,int version,Plan snapshot,int usedToday,int available){}
 public record StampCommand(UUID cardId,String source,String idempotencyKey){}
 public record RequestCreate(String kind,UUID planId,UUID cardId,int quantity,String idempotencyKey){}
 public record Request(UUID id,String kind,UUID planId,UUID cardId,UUID customerId,int quantity,UUID createdBy,String status,Instant expiresAt,UUID entryId,LocalDate authorizationDay){}
 public record Confirm(UUID cardId,String businessDay,String idempotencyKey){}
 public record Code(String code,Instant expiresAt){}
 public record PresentationCreate(int quantity,String businessDay,String idempotencyKey){}
 public record Presentation(UUID requestId,String code,Instant expiresAt){}
 public record Scan(String code){}
 public record Authorized(String authorization,String businessDay,String idempotencyKey){}
 public record Key(String idempotencyKey){}
 public record Reverse(String reason,String idempotencyKey){}
 public record PurchaseCreate(UUID planId,String idempotencyKey){}
 public record Purchase(UUID id,UUID customerId,UUID planId,UUID cardId,long amountCents,String status,Instant expiresAt,Plan snapshot){}
 public record PaymentConfirm(String channel,String account,String terminal,String receipt,long amountCents,boolean verified,String idempotencyKey){}
 public record PaymentReceipt(String channel,String account,String receipt,long amountCents){}
 public record ExceptionPayment(PaymentConfirm payment,String reason,String idempotencyKey){}
 public record Refund(String reason,String receipt,boolean verified,String idempotencyKey){}
 public record Entry(UUID id,UUID cardId,UUID customerId,String kind,int quantity,Integer beforeTotal,Integer afterTotal,LocalDate businessDay,int beforeDaily,int afterDaily,String source,UUID originalId,String reason,Instant createdAt,Integer rewardRound){}
}
