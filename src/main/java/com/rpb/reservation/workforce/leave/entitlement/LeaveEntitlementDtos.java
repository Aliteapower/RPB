package com.rpb.reservation.workforce.leave.entitlement;
import java.math.BigDecimal;import java.time.*;import java.util.*;
public final class LeaveEntitlementDtos {
 private LeaveEntitlementDtos(){}
 public record Write(Long expectedVersion,UUID companyId,UUID typeId,Integer year,BigDecimal grant,BigDecimal carryIn,BigDecimal adjustment,String unit,Integer dayMinutes,String reason){}
 public record Policy(UUID id,UUID companyId,UUID employeeId,UUID typeId,int year,BigDecimal grant,BigDecimal carryIn,BigDecimal adjustment,String unit,Integer dayMinutes,String reason,long version){}
 public record Balance(UUID typeId,String typeName,UUID sourceStoreId,UUID companyId,Policy policy,String state,Long usedSeconds,Long reservedSeconds,BigDecimal used,BigDecimal reserved,BigDecimal remaining,List<String> unknownReasons,String accountUnit){
 public Balance(UUID typeId,String typeName,UUID sourceStoreId,UUID companyId,Policy policy,String state,Long usedSeconds,Long reservedSeconds,BigDecimal used,BigDecimal reserved,BigDecimal remaining,List<String> unknownReasons){this(typeId,typeName,sourceStoreId,companyId,policy,state,usedSeconds,reservedSeconds,used,reserved,remaining,unknownReasons,null);}
 }
 public record Page(UUID employeeId,int year,boolean canManage,List<Balance> balances){}
 public record Type(UUID id,String name,UUID storeId){}
 public record Event(UUID id,UUID typeId,LocalDate workDate,Instant start,Instant end,String status){}
}
