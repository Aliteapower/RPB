package com.rpb.reservation.winestorage.application;

import java.time.Instant;
import java.util.*;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;

public final class StorageReportDtos {
 private StorageReportDtos(){}
 public record Row(UUID id,String kind,UUID customerId,String customerName,String customerPhone,String status,Instant occurredAt,long quantity,String winesZh,String winesEn,String locations){}
 public record Summary(long recordCount,long completedCount,long bottleCount){}
 public record Page(List<Row> items,Summary summary,int page,int size,boolean hasMore){}
 public record Detail(Application application,Intent intent,List<Item> items,List<Event> events){}
}
