package com.rpb.reservation.workforce.scheduling.restswap;
import java.time.LocalDate;import java.util.*;
public final class RestSwapDtos {
 private RestSwapDtos(){}
 public record Request(List<UUID> employeeIds,LocalDate restDate,LocalDate makeupDate,UUID shiftId){}
 public record Commit(Request request,String fingerprint){}
 public record Item(UUID employeeId,String employeeNo,String employeeName,String status,String reason,List<UUID> cancelSchedules){}
 public record Preview(String fingerprint,int ready,int conflicts,int cancellations,List<Item> items,String timezone){}
 public record Result(UUID commandId,int changed,int skipped,List<UUID> scheduleIds){}
 public record Link(UUID id,UUID employeeId,String employeeNo,String employeeName,LocalDate restDate,LocalDate makeupDate,UUID makeupScheduleId,String makeupStatus){}
}
