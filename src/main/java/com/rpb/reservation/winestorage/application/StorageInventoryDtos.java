package com.rpb.reservation.winestorage.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StorageInventoryDtos {
 private StorageInventoryDtos(){}
 public record Row(UUID id,UUID customerId,String customerName,String customerPhone,String nameZh,String nameEn,int quantity,int remaining,boolean opened,Instant receivedAt,Instant expiresAt,String status,String locationName,String slot,String label,String custodianName){}
 public record Summary(long customerCount,long bottleCount,long openedBottleCount,long expiredBottleCount,long itemCount){}
 public record Page(List<Row> items,Summary summary,int page,int size,boolean hasMore){}
}
