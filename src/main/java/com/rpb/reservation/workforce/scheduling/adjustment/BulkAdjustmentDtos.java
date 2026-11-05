package com.rpb.reservation.workforce.scheduling.adjustment;
import java.time.*;import java.util.*;
public final class BulkAdjustmentDtos {
 private BulkAdjustmentDtos(){}
 public record Target(UUID scheduleId,Long expectedVersion){}
 public record Request(LocalDate date,UUID sourceShiftId,UUID targetShiftId,List<Target> targets){}
 public record Commit(Request request,String fingerprint){}
 public record Item(UUID scheduleId,UUID employeeId,String employeeNo,String employeeName,LocalDate date,long version,String sourceShiftName,Instant sourceStartAt,Instant sourceEndAt,String sourceTimezone,Instant startAt,Instant endAt,String status,String reason){}
 public record Preview(String fingerprint,LocalDate date,UUID sourceShiftId,UUID targetShiftId,String targetShiftName,String timezone,int total,int ready,int unchanged,int conflicts,List<Item> items){}
 public record Result(UUID commandId,int updated,int unchanged,int conflicts,List<UUID> scheduleIds){}
}
