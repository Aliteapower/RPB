package com.rpb.reservation.winestorage.application;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.rpb.reservation.common.serialization.StrictIntegerDeserializer;

public final class StorageDtos {
 private StorageDtos(){}
 public record Settings(String operationMode,int version){}
 public record SettingsWrite(String operationMode,@JsonDeserialize(using=StrictIntegerDeserializer.class) int version,String idempotencyKey){}
 public record Rule(UUID id,UUID categoryId,String name,int months,int version){}
 public record RuleWrite(UUID categoryId,String name,@JsonDeserialize(using=StrictIntegerDeserializer.class) int months,String idempotencyKey){}
 public record Line(UUID id,UUID productId,String nameZh,String nameEn,@JsonDeserialize(using=StrictIntegerDeserializer.class) int quantity,boolean opened,String source,List<UUID> photos,String note){}
 public record Apply(List<Line> lines,boolean rulesAccepted,UUID withdrawalId,UUID withdrawalItemId,String idempotencyKey){}
 public record Application(UUID id,UUID customerId,String status,Apply original,List<Line> lines,Receive confirmation,String reason,Instant createdAt){}
 public record ReceiptLine(UUID lineId,UUID productId,UUID categoryId,String nameZh,String nameEn,@JsonDeserialize(using=StrictIntegerDeserializer.class) int quantity,boolean opened,String source,UUID ruleId,List<UUID> photos,String note,String location,String label,@JsonInclude(JsonInclude.Include.NON_NULL) UUID storageLocationId,@JsonInclude(JsonInclude.Include.NON_NULL) LocationRef storageLocation){
  public ReceiptLine(UUID lineId,UUID productId,UUID categoryId,String nameZh,String nameEn,int quantity,boolean opened,String source,UUID ruleId,List<UUID> photos,String note,String location,String label){this(lineId,productId,categoryId,nameZh,nameEn,quantity,opened,source,ruleId,photos,note,location,label,null,null);}
  public ReceiptLine(UUID lineId,UUID productId,UUID categoryId,String nameZh,String nameEn,int quantity,boolean opened,String source,UUID ruleId,List<UUID> photos,String note,String location,String label,UUID storageLocationId){this(lineId,productId,categoryId,nameZh,nameEn,quantity,opened,source,ruleId,photos,note,location,label,storageLocationId,null);}
 }
 public record Receive(List<ReceiptLine> lines,String reason,String idempotencyKey,@JsonInclude(JsonInclude.Include.NON_NULL) UUID custodianId){public Receive(List<ReceiptLine> lines,String reason,String idempotencyKey){this(lines,reason,idempotencyKey,null);}}
 public record Registration(UUID customerId,List<ReceiptLine> lines,String reason,boolean rulesConfirmed,String idempotencyKey,@JsonInclude(JsonInclude.Include.NON_NULL) UUID custodianId){public Registration(UUID customerId,List<ReceiptLine> lines,String reason,boolean rulesConfirmed,String idempotencyKey){this(customerId,lines,reason,rulesConfirmed,idempotencyKey,null);}}
 public record Key(String idempotencyKey,String reason){}
 public record Snapshot(UUID productId,String code,String nameZh,String nameEn,UUID categoryId,Rule rule,String timezone,String note,List<UUID> applicationPhotos,List<UUID> receiptPhotos,CustodianRef custodian,@JsonInclude(JsonInclude.Include.NON_NULL) LocationRef storageLocation){
  public Snapshot(UUID productId,String code,String nameZh,String nameEn,UUID categoryId,Rule rule,String timezone,String note,List<UUID> applicationPhotos,List<UUID> receiptPhotos,CustodianRef custodian){this(productId,code,nameZh,nameEn,categoryId,rule,timezone,note,applicationPhotos,receiptPhotos,custodian,null);}
 }
 public record Item(UUID id,UUID applicationId,UUID customerId,Snapshot snapshot,int quantity,int remaining,boolean opened,String source,String location,String label,Instant receivedAt,Instant expiresAt,String status){}
 public record Selection(UUID itemId,@JsonDeserialize(using=StrictIntegerDeserializer.class) int quantity){}
 public record IntentWrite(UUID customerId,List<Selection> selections,String idempotencyKey){}
 public record Intent(UUID id,UUID customerId,String origin,String status,Instant expiresAt,Instant completedAt,List<Selection> selections,String code){}
 public record Scan(String code){}
 public record ScanDelivery(String code,String idempotencyKey){}
 public record Authorize(String code,String idempotencyKey){}
 public record Correction(String reason,String note,String idempotencyKey){}
 public record PhotoAdd(List<UUID> photos,String note,String idempotencyKey){}
 public record Event(UUID id,UUID sourceId,String action,UUID actorId,Map<String,Object> details,Instant createdAt,@JsonInclude(JsonInclude.Include.NON_NULL) String actorName){
  public Event(UUID id,UUID sourceId,String action,UUID actorId,Map<String,Object> details,Instant createdAt){this(id,sourceId,action,actorId,details,createdAt,null);}
 }
 public record Product(UUID id,UUID categoryId,String code,String nameZh,String nameEn){}
 public record Category(UUID id,String nameZh,String nameEn){}
 public record StoreOption(UUID id,String name){}
 public record Home(String operationMode,int settingsVersion,String timezone,String storeName,List<StoreOption> stores,boolean newBusinessEnabled,Set<String> permissions,List<Rule> rules,List<Category> categories,List<Application> applications,List<Item> items,List<Intent> intents,List<Event> events){}
 public record StorageLocation(UUID id,String name,boolean defaultLocation,int version){}
 public record LocationWrite(String name,Integer version,String idempotencyKey){}
 public record LocationRef(UUID id,String name){}
 public record Custodian(UUID id,String name,String kind,boolean enabled,int version){}
 public record CustodianRef(UUID id,String name,String kind){}
 public record CustodianWrite(String name,String kind,Boolean enabled,Integer version,String idempotencyKey){}
 public record PendingApplication(Application application,StorageCustomerQuery.Customer customer){}
 public record Photo(UUID id,String phase){}
 public record Image(byte[] bytes,String contentType){}
 public record Problem(UUID itemId,String code){}
}
