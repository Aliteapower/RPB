package com.rpb.reservation.workforce.approval;
import com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
import java.time.*;import java.util.*;
public final class ApprovalBoardDtos {
 private ApprovalBoardDtos(){}
 public record Filter(LocalDate from,LocalDate to,UUID positionId,String search,String category,UUID employeeId){public Filter(LocalDate from,LocalDate to,UUID positionId,String search,String category){this(from,to,positionId,search,category,null);}}
 public record Entry(ApprovalRow row,Instant inAt,Instant outAt,String category,boolean canReview,String reviewBlock,boolean managerEmployee){}
 public record Day(LocalDate date,int total,int current,int normal,int waiting,int exceptions,int stale,int managers){}
 public record Board(List<Entry> items,int total,int limit,int offset,Map<String,Integer> counts,List<Day> days,List<Option> positions,String timezone,int eligible,List<EmployeeProgress> employees){
 public Board(List<Entry> items,int total,int limit,int offset,Map<String,Integer> counts,List<Day> days,List<Option> positions,String timezone,int eligible){this(items,total,limit,offset,counts,days,positions,timezone,eligible,List.of());}}
 public record EmployeeProgress(UUID employeeId,String employeeName,String employeeNo,int records,int pending,long scheduledSeconds,Long actualSeconds,Long approvedSeconds){}
 public record Selection(Filter filter,Boolean all,List<UUID> ids,List<UUID> excluded){}
 public record Candidate(UUID scheduleId,String employeeName,String employeeNo,LocalDate date,Long expectedVersion,String sourceFingerprint,long workSeconds,long paidLeaveSeconds,long unpaidLeaveSeconds){}
 public record Preview(List<Candidate> items,long workSeconds,long paidLeaveSeconds,long unpaidLeaveSeconds){}
 public record Commit(String note,List<Candidate> items){}
 public record Outcome(UUID scheduleId,boolean success,String code){}
 public record Result(List<Outcome> items,int succeeded,int failed){}
 public record StoreProgress(UUID storeId,String storeName,int total,int current,int pending,long workSeconds,long paidLeaveSeconds,long unpaidLeaveSeconds){}
 public record Readiness(UUID companyId,String month,boolean ready,int total,int current,int pending,List<StoreProgress> stores){}
}
