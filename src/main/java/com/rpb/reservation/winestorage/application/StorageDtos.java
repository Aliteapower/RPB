package com.rpb.reservation.winestorage.application;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rpb.reservation.common.serialization.StrictIntegerDeserializer;

public final class StorageDtos {
 private StorageDtos(){}
 public record Rule(UUID id,UUID categoryId,String name,int months,int version){}
 public record RuleWrite(UUID categoryId,String name,@JsonDeserialize(using=StrictIntegerDeserializer.class) int months,String idempotencyKey){}
 public record Line(UUID id,UUID productId,String nameZh,String nameEn,@JsonDeserialize(using=StrictIntegerDeserializer.class) int quantity,boolean opened,String source,List<UUID> photos,String note){}
 public record Apply(List<Line> lines,boolean rulesAccepted,UUID withdrawalId,UUID withdrawalItemId,String idempotencyKey){}
 public record Application(UUID id,UUID customerId,String status,Apply original,List<Line> lines,Receive confirmation,String reason,Instant createdAt){}
 public record ReceiptLine(UUID lineId,UUID productId,UUID categoryId,String nameZh,String nameEn,@JsonDeserialize(using=StrictIntegerDeserializer.class) int quantity,boolean opened,String source,UUID ruleId,List<UUID> photos,String note,String location,String label){}
 public record Receive(List<ReceiptLine> lines,String reason,String idempotencyKey){}
 public record Registration(UUID customerId,List<ReceiptLine> lines,String reason,boolean rulesConfirmed,String idempotencyKey){}
 public record Key(String idempotencyKey,String reason){}
 public record Snapshot(UUID productId,String code,String nameZh,String nameEn,UUID categoryId,Rule rule,String timezone,String note,List<UUID> applicationPhotos,List<UUID> receiptPhotos){}
 public record Item(UUID id,UUID applicationId,UUID customerId,Snapshot snapshot,int quantity,int remaining,boolean opened,String source,String location,String label,Instant receivedAt,Instant expiresAt,String status){}
 public record Selection(UUID itemId,@JsonDeserialize(using=StrictIntegerDeserializer.class) int quantity){}
 public record IntentWrite(UUID customerId,List<Selection> selections,String idempotencyKey){}
 public record Intent(UUID id,UUID customerId,String origin,String status,Instant expiresAt,Instant completedAt,List<Selection> selections,String code){}
 public record Scan(String code){}
 public record Authorize(String code,String idempotencyKey){}
 public record Correction(String reason,String note,String idempotencyKey){}
 public record PhotoAdd(List<UUID> photos,String note,String idempotencyKey){}
 public record Event(UUID id,UUID sourceId,String action,UUID actorId,Map<String,Object> details,Instant createdAt){}
 public record Product(UUID id,UUID categoryId,String code,String nameZh,String nameEn){}
 public record Category(UUID id,String nameZh,String nameEn){}
 public record StoreOption(UUID id,String name){}
 public record Home(String timezone,String storeName,List<StoreOption> stores,boolean newBusinessEnabled,Set<String> permissions,List<Rule> rules,List<Category> categories,List<Application> applications,List<Item> items,List<Intent> intents,List<Event> events){}
 public record Photo(UUID id,String phase){}
 public record Image(byte[] bytes,String contentType){}
 public record Problem(UUID itemId,String code){}
}
