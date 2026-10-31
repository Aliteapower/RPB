package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
class PayrollWorkbenchProjectionTest {
 @Test void uncalculatedBatchDoesNotPretendItsWagesAreZero(){
  var b=new PayrollRepository.State(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"C",YearMonth.of(2026,10),"draft",1,null,UUID.randomUUID(),null,Instant.EPOCH);
  assertThat(PayrollViews.summary(b,1).totalNet()).isNull();assertThat(PayrollViews.summary(b,1).totalGross()).isNull();
 }
 @Test void ledgerUsesExactDecimalsReversalsAndZeroIsNotBankCompletion(){
  UUID employee=UUID.randomUUID(),zero=UUID.randomUUID(),payment=UUID.randomUUID();var line=mock(PayrollLine.class);when(line.employeeId()).thenReturn(employee);when(line.employeeName()).thenReturn("E");when(line.netPay()).thenReturn("9007199254740993.01");
  var z=mock(PayrollLine.class);when(z.employeeId()).thenReturn(zero);when(z.netPay()).thenReturn("0.00");var snap=mock(PayrollSnapshot.class);when(snap.lines()).thenReturn(List.of(line,z));
  var one=new Payment(payment,employee,"10.01",LocalDate.of(2026,10,30),"cash","","",null,UUID.randomUUID(),Instant.EPOCH);
  var partial=PayrollPaymentProjection.summarize(snap,List.of(one));assertThat(partial.remaining()).isEqualTo("9007199254740983.00");assertThat(partial.partial()).isEqualTo(1);assertThat(partial.zero()).isEqualTo(1);
  var reverse=new Payment(UUID.randomUUID(),employee,"10.01",one.paymentDate(),"cash","","",payment,one.actorId(),Instant.EPOCH);
  var restored=PayrollPaymentProjection.summarize(snap,List.of(one,reverse));assertThat(restored.paid()).isEqualTo("0.00");assertThat(restored.unpaid()).isEqualTo(1);
  assertThat(PayrollPaymentProjection.summarize(null,List.of()).due()).isNull();
 }

 java.util.List<PayrollWorkbenchDtos.Reason> reasons(SourceRef before,SourceRef after){
  UUID employee=UUID.randomUUID();var e=mock(com.rpb.reservation.workforce.application.WorkforceDtos.Employee.class);when(e.id()).thenReturn(employee);when(e.name()).thenReturn("E");
  var line=mock(PayrollLine.class);when(line.employeeId()).thenReturn(employee);when(line.employeeName()).thenReturn("E");when(line.sources()).thenReturn(List.of(before));
  var baseline=mock(PayrollSnapshot.class);when(baseline.lines()).thenReturn(List.of(line));
  var capture=new PayrollSources.Capture("new",null,List.of(new PayrollSources.CapturedLine(e,null,null,List.of(after),List.of(),false)),Set.of(after.storeId()),null);
  return PayrollSourceReasons.compare(baseline,capture,null,true);
 }
 @Test void addingEvidenceToOldSnapshotDoesNotInventANewApproval(){
  UUID schedule=UUID.randomUUID(),store=UUID.randomUUID();var day=LocalDate.of(2026,10,28);
  var before=new SourceRef(schedule,store,day,1,"old",3600,0,0,true);
  var after=new SourceRef(schedule,store,day,1,"new",3600,0,0,true,new SourceEvidence(3600,"current",3600L,Instant.EPOCH,Instant.EPOCH.plusSeconds(3600),List.of()));
  assertThat(reasons(before,after)).extracting(PayrollWorkbenchDtos.Reason::kind).contains("source").doesNotContain("approval","attendance","leave");
 }
 @Test void staleApprovalZeroPlaceholdersDoNotProveLeaveChanged(){
  UUID schedule=UUID.randomUUID(),store=UUID.randomUUID();var day=LocalDate.of(2026,10,28);
  var before=new SourceRef(schedule,store,day,1,"old",3600,3600,0,true,new SourceEvidence(3600,"current",3600L,Instant.EPOCH,Instant.EPOCH.plusSeconds(3600),List.of()));
  var after=new SourceRef(schedule,store,day,1,"new",0,0,0,true,new SourceEvidence(3600,"stale",null,Instant.EPOCH,Instant.EPOCH.plusSeconds(3600),List.of()));
  assertThat(reasons(before,after)).extracting(PayrollWorkbenchDtos.Reason::kind).contains("approval").doesNotContain("leave","attendance");
 }

 @Test void generatedUnitChangesWithSameInputVersionAreNotReportedAsManualInputChanges(){
  UUID employee=UUID.randomUUID();var e=mock(com.rpb.reservation.workforce.application.WorkforceDtos.Employee.class);when(e.id()).thenReturn(employee);when(e.name()).thenReturn("E");
  var before=new LineInput(employee,0,"22","20",true,false,List.of(),"",null,"automatic");var after=new LineInput(employee,0,"22","21",true,false,List.of(),"",null,"automatic");
  var line=mock(PayrollLine.class);when(line.employeeId()).thenReturn(employee);when(line.employeeName()).thenReturn("E");when(line.sources()).thenReturn(List.of());when(line.input()).thenReturn(before);
  var baseline=mock(PayrollSnapshot.class);when(baseline.lines()).thenReturn(List.of(line));var capture=new PayrollSources.Capture("new",null,List.of(new PayrollSources.CapturedLine(e,null,after,List.of(),List.of(),false)),Set.of(),null);
  assertThat(PayrollSourceReasons.compare(baseline,capture,null,true)).extracting(PayrollWorkbenchDtos.Reason::kind).contains("source").doesNotContain("monthly_input");
 }
 @Test void legacyPolicyWithoutVerifiableIdentityIsNotClaimedAsPolicyMutation(){
  var baseline=mock(PayrollSnapshot.class);when(baseline.lines()).thenReturn(List.of());when(baseline.policyVersion()).thenReturn("LEGACY-2026");
  var policy=mock(com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy.class);when(policy.id()).thenReturn(UUID.randomUUID());when(policy.version()).thenReturn(1L);
  var capture=new PayrollSources.Capture("new",null,List.of(),Set.of(),policy);
  assertThat(PayrollSourceReasons.compare(baseline,capture,null,true)).extracting(PayrollWorkbenchDtos.Reason::kind).contains("source").doesNotContain("statutory_policy");
 }

 @Test void blockedPreviewZeroPlaceholderIsNotNoPaymentNeeded(){
  UUID id=UUID.randomUUID();var line=mock(PayrollLine.class);when(line.employeeId()).thenReturn(id);when(line.netPay()).thenReturn("0.00");
  var snapshot=mock(PayrollSnapshot.class);when(snapshot.lines()).thenReturn(List.of(line));when(snapshot.issues()).thenReturn(List.of("APPROVAL_STALE"));
  var result=PayrollPaymentProjection.summarize(snapshot,List.of());assertThat(result.available()).isFalse();assertThat(result.due()).isNull();assertThat(result.zero()).isZero();assertThat(result.lines()).isEmpty();
 }

 @Test void frozenAmountsRemainReadableDespiteCurrentBlockers(){
  UUID id=UUID.randomUUID();var line=mock(PayrollLine.class);when(line.employeeId()).thenReturn(id);when(line.netPay()).thenReturn("100.00");
  var snapshot=mock(PayrollSnapshot.class);when(snapshot.lines()).thenReturn(List.of(line));when(snapshot.issues()).thenReturn(List.of("LEGACY_REVIEW"));
  var payment=new Payment(UUID.randomUUID(),id,"20.00",LocalDate.of(2026,10,30),"cash","","",null,UUID.randomUUID(),Instant.EPOCH);
  var result=PayrollPaymentProjection.summarize(snapshot,List.of(payment),true);assertThat(result.available()).isTrue();assertThat(result.due()).isEqualTo("100.00");assertThat(result.paid()).isEqualTo("20.00");assertThat(result.remaining()).isEqualTo("80.00");
 }

 @Test void changedEngineRemainsVisibleEvenWhenFixedEmployeeCaptureFingerprintIsUnchanged(){
  var baseline=mock(PayrollSnapshot.class);when(baseline.lines()).thenReturn(List.of());when(baseline.sourceFingerprint()).thenReturn("same");
  var capture=new PayrollSources.Capture("same",null,List.of(),Set.of(),null);var cache=new PayrollIncremental.Cache("workforce-payroll-incremental-v1","global",Map.of(),baseline);
  assertThat(PayrollSourceReasons.compare(baseline,capture,cache,false)).extracting(PayrollWorkbenchDtos.Reason::kind).containsExactly("engine");
 }
}
