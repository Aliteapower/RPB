package com.rpb.reservation.workforce.leave.account;
import java.math.BigDecimal;import java.time.*;import java.util.*;
public final class LeaveAccountDtos {
 private LeaveAccountDtos(){}
 public record Account(UUID id,UUID employeeId,UUID companyId,int year,String kind,String unit,UUID contractId,LocalDate openingOn,BigDecimal baselineAccrual,long version){}
 public record Lot(UUID id,UUID accountId,String origin,String sourceKey,LocalDate expiresOn,BigDecimal quantity,BigDecimal remaining,BigDecimal reserved,UUID contractId,long accountVersion,int entitlementYear){}
 public record Entry(UUID id,UUID accountId,UUID lotId,String sourceKey,String operation,BigDecimal quantity,String reason,UUID actorId,Instant createdAt,UUID contractId,long accountVersion){}
 public record Hold(UUID sourceId,String sourceKind,UUID accountId,UUID lotId,BigDecimal quantity,String status){}
 public record Balance(Account account,BigDecimal available,BigDecimal reserved,BigDecimal used,BigDecimal encashed,BigDecimal dueAccrual,List<Lot> lots,List<Entry> history,List<String> issues){}
 public record Page(UUID employeeId,String employeeName,int year,boolean canManage,List<Balance> balances){}
 public record Opening(String kind,BigDecimal remaining){}
 public record Enable(UUID employeeId,int year,UUID contractId,LocalDate openingOn,List<Opening> openings,String note){}
 public record Adjust(UUID employeeId,UUID accountId,Long expectedVersion,BigDecimal quantity,LocalDate expiresOn,String note){}
 public record YearEnd(UUID employeeId,int year,String note){}
 public record Review(UUID employeeId,UUID accountId,Long expectedVersion,UUID contractId,LocalDate reviewOn,BigDecimal available,String note){}
 public record Refresh(UUID employeeId,int year,String note){}
 public record Binding(UUID typeId,String kind,long version){}
 public record BindWrite(Long expectedVersion,String kind){}
}
