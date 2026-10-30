package com.rpb.reservation.workforce.scheduling.copy;
import java.time.*;
import java.util.*;
public final class WeekCopyDtos {
 private WeekCopyDtos(){}
 public record Request(LocalDate sourceWeek,LocalDate targetWeek){}
 public record Commit(Request request,String fingerprint){}
 public record Item(UUID sourceId,UUID employeeId,String employeeNo,String employeeName,LocalDate sourceDate,LocalDate date,UUID shiftId,String shiftName,Instant startAt,Instant endAt,String status,String reason){}
 public record Preview(String fingerprint,LocalDate sourceWeek,LocalDate targetWeek,int employeeCount,int total,int ready,int existing,int conflicts,String timezone,List<Item> items){}
}
