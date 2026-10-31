package com.rpb.reservation.workforce.statutory.annual;

import com.rpb.reservation.workforce.payroll.WageEvents;
import com.rpb.reservation.workforce.statutory.*;
import java.time.*;
import java.util.*;

/** Decimal strings, explicit completeness and immutable source revisions. No inferred remittance. */
public final class AnnualWageDtos {
 private AnnualWageDtos(){}
 public record WageEvent(WageEvents.Evidence evidence,String amount,String source){}
 public record OpeningMonth(String month,String state,String rawOW,StatutoryDtos.CpfProfile cpf,UUID policyId,Long policyVersion,String recognisedEmployeeCpf,String recognisedEmployerCpf,String evidenceReference,List<WageEvent> events){}
 public record Write(UUID commandId,Long expectedVersion,List<OpeningMonth> months,String reason){}
 public record MonthFact(String month,String state,String rawOW,String cpfOW,StatutoryDtos.CpfProfile cpf,StatutoryPolicies.Policy policy,String recognisedEmployeeCpf,String recognisedEmployerCpf,String source,List<WageEvent> events,List<String> issues){}
 public record Context(UUID companyId,UUID employeeId,int year,long version,String fingerprint,List<MonthFact> months,List<String> issues,boolean writable,List<OpeningMonth> openingMonths){public Context(UUID companyId,UUID employeeId,int year,long version,String fingerprint,List<MonthFact> months,List<String> issues,boolean writable){this(companyId,employeeId,year,version,fingerprint,months,issues,writable,List.of());}}
 public record Allocation(UUID eventId,String month,String amount,String eligibleAW,String remaining,String source){}
 public record Assessment(String status,String fingerprint,String annualOW,String awCeiling,String usedAW,String remainingAW,List<Allocation> allocations,List<MonthLiability> months,String employeeDelta,String employerDelta,String totalDelta,boolean reviewRequired,List<String> issues){}
 public record MonthLiability(String month,String rawOW,String rawAW,String bandWages,String cpfOW,String cpfAW,String targetEmployeeCpf,String targetEmployerCpf,String employeeDelta,String employerDelta,String rateCode,List<String> issues){}
 public record ReconcileWrite(UUID commandId,String expectedFingerprint,String settlementMonth){}
 public record Reconciliation(UUID id,UUID companyId,UUID employeeId,int year,String settlementMonth,Assessment assessment,UUID recordedBy,Instant recordedAt){}
 public record FrozenAssessment(UUID batchId,UUID employeeId,String sourceFingerprint,Context context,Assessment assessment){}
 public record Page(Context context,Assessment assessment,List<Reconciliation> reconciliations){public Page(Context context,Assessment assessment){this(context,assessment,List.of());}}
}
