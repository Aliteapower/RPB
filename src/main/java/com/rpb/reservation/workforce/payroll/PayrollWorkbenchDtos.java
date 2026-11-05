package com.rpb.reservation.workforce.payroll;
import java.time.*;import java.util.*;
public final class PayrollWorkbenchDtos {
 private PayrollWorkbenchDtos(){}
 public record Missing(UUID employeeId,String employeeName,String category,String code,UUID profileId,String action){}
 public record Approval(UUID employeeId,String employeeName,UUID scheduleId,UUID storeId,LocalDate workDate,String state,boolean context){}
 public record Reason(UUID employeeId,String kind,String sourceId,String oldVersion,String newVersion,String scope,String action,UUID storeId,LocalDate workDate){}
 public record PaymentLine(UUID employeeId,String employeeName,String netPay,String paidAmount,String remainingAmount,String state){}
 public record PaymentSummary(boolean available,String due,String paid,String remaining,int unpaid,int partial,int settled,int zero,List<PaymentLine> lines){}
 public record Workbench(UUID companyId,String month,UUID batchId,Long batchVersion,String status,Instant asOf,Instant calculatedAt,boolean frozen,int missingEmployees,List<Missing> missing,int pendingEmployees,int pendingShifts,int contextShifts,int exemptEmployees,List<Approval> approvals,List<Reason> reasons,PayrollProgressDtos.ChangeReport changes,PaymentSummary payments){}
}
