package com.rpb.reservation.workforce.statutory.settlement;

import com.rpb.reservation.workforce.payroll.PayrollDtos;
import com.rpb.reservation.workforce.payroll.supplemental.SupplementalMoney;
import com.rpb.reservation.workforce.statutory.annual.AnnualWageDtos;
import java.time.*;
import java.util.*;

public final class SettlementDtos {
 private SettlementDtos(){}
 public record Amounts(String employeeCpf,String employerCpf,String sdl,String cdac){}
 public record Recovery(String cause,String legalBasis,Integer dueYear,LocalDate deadline,String maximum,String evidenceReference,UUID reconciliationId){public Recovery(String cause,String legalBasis,Integer dueYear,LocalDate deadline,String maximum,String evidenceReference){this(cause,legalBasis,dueYear,deadline,maximum,evidenceReference,null);}}
 public record Interest(String state,String assessedAmount,String officialReference){}
 /** Remitted is opening actual payments only; later remittance registrations are added separately. */
 public record Evidence(UUID commandId,Long expectedVersion,String evidenceReference,LocalDate discoveryDate,String employeeCpfDeducted,String cdacDeducted,Amounts remitted,String recognisedSdl,String recognisedCdac,PayrollDtos.CdacProfile cdacProfile,PayrollDtos.CdacReview cdacReview,String manualCdac,Recovery recovery,Interest interest){}
 public record EvidencePage(UUID companyId,UUID employeeId,String month,long version,Evidence evidence,MonthBalance balance,boolean writable,String fingerprint){}
 public record CompanyEvidence(UUID commandId,Long expectedVersion,String evidenceReference,List<UUID> employeeIds,String recognisedSdl,String remittedSdl){}
 public record CompanySdl(UUID companyId,String month,long version,CompanyEvidence evidence,String target,String recognised,String remitted,String liabilityDelta,String remittanceBalance,String individualTotal,String roundingAdjustment,List<String> issues,boolean writable){}
 public record MonthBalance(String month,SupplementalMoney.Balance employeeCpf,SupplementalMoney.Balance employerCpf,SupplementalMoney.Balance sdl,SupplementalMoney.Balance cdac,Recovery recovery,String employeeCpfRecoverable,Interest interest,boolean refundRequired,List<String> issues){}
 public record Allocation(UUID employeeId,String month,String component,String amount){}
 public record RemittanceWrite(UUID commandId,UUID companyId,LocalDate paymentDate,String reference,List<Allocation> allocations,UUID reversesId,String note){}
 public record Remittance(UUID id,UUID companyId,LocalDate paymentDate,String reference,List<Allocation> allocations,UUID reversesId,String note,UUID recordedBy,Instant recordedAt){}
 public record ReconciliationPage(AnnualWageDtos.Reconciliation reconciliation,List<MonthBalance> balances,boolean settled){}
 public record SettleWrite(UUID commandId,String expectedFingerprint,String note){}
 public record Settlement(UUID id,UUID companyId,UUID employeeId,String settlementMonth,UUID reconciliationId,List<MonthBalance> balances,Instant recordedAt,AnnualWageDtos.Context context){}
}
