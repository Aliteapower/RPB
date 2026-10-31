package com.rpb.reservation.workforce.payroll.segment;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
import java.time.*;import java.util.*;
public final class SegmentDtos {
 private SegmentDtos(){}
 public record Write(Long expectedVersion,UUID companyId,UUID employeeId,String month,LocalDate cutoff,String workingDays,String payableDays,boolean unitsReviewed,boolean specialDaysReviewed,String note){}
 public record Action(Long expectedVersion,String action,String note){}
 public record Calculation(String fingerprint,UUID previousId,PayrollDtos.PayrollSnapshot cumulative,PayBreakdown.Values previouslySettled,PayBreakdown.Values delta,String companySdlDelta,String targetGross,String targetNet,List<String> issues,List<String> notices){}
 public record Detail(UUID id,UUID companyId,UUID employeeId,String employeeName,String companyName,String month,LocalDate from,LocalDate cutoff,String status,long version,Write request,Calculation calculation,List<PayrollDtos.Payment> payments,String paid,String remaining,boolean sourceChanged){}
 public record EmployeeOption(UUID id,String name,String basis,String generation){}
 public record Page(UUID companyId,String month,List<EmployeeOption> employees,List<Detail> records,PayBreakdown.Values formalTotals,int people,int settlementCount){}
 public record Slip(UUID id,String companyName,String employeeName,String month,LocalDate from,LocalDate cutoff,String status,PayBreakdown.Values breakdown,String net,String paid,String remaining,List<PublicPayment> payments,String basis,String rate,long workSeconds,long overtimeSeconds,String workingDays,String payableDays){}
 public record PublicPayment(UUID id,String amount,LocalDate paymentDate,String method,String reference,UUID reversesId,String bank){}
}
