package com.rpb.reservation.workforce.payroll;
import java.time.*;
import java.util.*;
import com.rpb.reservation.workforce.payroll.PayrollDtos.PayrollLine;
/** Read projection only; null money means unavailable, never a zero placeholder. */
public final class PayrollBoardDtos {
 private PayrollBoardDtos(){}
 public record Row(UUID employeeId,String employeeName,String employeeNo,String positionName,String basis,UUID profileId,String gross,String deductions,String net,List<String> issues,PayrollLine line,PayBreakdown.Values breakdown,List<ApprovalTarget> approvals,LocalDate suggestedEffectiveFrom){}
 public record ApprovalTarget(UUID sourceId,UUID storeId,LocalDate workDate,String state,String sourceType,long sourceVersion){}
 public record Board(UUID companyId,String month,UUID batchId,Long batchVersion,String status,boolean frozen,boolean sourceChanged,Instant asOf,List<String> issues,List<Row> rows,PayBreakdown.Values totals,@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY) List<SegmentSummary> segments){}
 public record SegmentSummary(UUID id,UUID employeeId,String employeeName,LocalDate from,LocalDate cutoff,String status,String gross,String net,boolean sourceChanged){}
}
