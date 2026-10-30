package com.rpb.reservation.workforce.payroll.supplemental;

import com.rpb.reservation.workforce.payroll.PayrollDtos;
import com.rpb.reservation.workforce.payroll.governance.PayChangeDtos;
import com.rpb.reservation.workforce.statutory.annual.AnnualWageDtos;
import com.rpb.reservation.workforce.statutory.settlement.SettlementDtos;
import java.time.*;
import java.util.*;

public final class SupplementalDtos {
 private SupplementalDtos(){}
 /** One employee and original contribution month per independently approved aggregate. */
 public record Source(String kind,UUID batchId,Long openingVersion,String snapshotHash){}
 public record SourcePage(UUID companyId,UUID employeeId,String month,Source source,String grossPay,String originalCashRemaining,List<String> issues){}
 public record Proposal(UUID id,String originMonth,String settlementMonth,Source source,List<PayrollDtos.Item> items,String evidenceReference){}
 public record Write(UUID commandId,Long expectedVersion,UUID companyId,UUID employeeId,UUID requestId,Long revision,String hash,UUID proposalId){}
 public record Action(UUID commandId,Long expectedVersion,String action,String expectedFingerprint,String note){}
 public record Snapshot(String sourceFingerprint,String grossDelta,String employeeCashPayable,String employeeCpfDeduction,String cdacDeduction,String employeeCpfLiabilityDelta,String employerCpfDelta,String sdlDelta,String sdlRoundingDelta,String cdacDelta,String originalCashRemaining,List<SettlementDtos.MonthBalance> balances,AnnualWageDtos.Context context,AnnualWageDtos.Assessment assessment,List<String> issues,Instant calculatedAt){}
 public record Batch(UUID id,UUID companyId,UUID employeeId,String employeeName,String originMonth,String settlementMonth,String status,long version,Proposal proposal,PayChangeDtos.Proof approval,Snapshot calculation,Instant createdAt){}
 public record Detail(Batch batch,boolean sourceChanged,List<PayrollDtos.Payment> payments,String paidAmount,String remainingAmount){}
 public record PayslipItem(String label,String amount,String businessType,String classification,String originMonth,String settlementMonth){}
 public record Payslip(UUID batchId,String companyName,String employeeName,String originMonth,String settlementMonth,String status,String grossDelta,String employeeCpfDeduction,String cdacDeduction,String employeeCashPayable,String paidAmount,String remainingAmount,UUID originalBatchId,List<PayslipItem> items){}
}
