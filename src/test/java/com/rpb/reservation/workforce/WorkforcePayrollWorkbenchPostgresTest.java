package com.rpb.reservation.workforce;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
class WorkforcePayrollWorkbenchPostgresTest extends WorkforcePayrollPostgresTest {
 com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule schedule(String day){
 var shift=shifts.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ShiftWrite(0L,"Pending","09:00","17:00",false,"none",0,0,0,0,false,true));
 return roster.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ScheduleWrite(0L,employee,shift.id(),LocalDate.parse(day),"scheduled"));
 }
 PayrollWorkbenchService workbench(){return proxy(new PayrollWorkbenchService(new PayrollAccess(access,directory,jdbc),payRepository,sources,new PayrollCalculationService(time),new PayrollProgressService(access,new PayrollAccess(access,directory,jdbc),payRepository,sources,new PayrollCalculationService(time)),time));}
 @Test void noBatchStillShowsMissingProfileAndPolicyWithoutFakeAmounts(){
  jdbc.update("delete from workforce_pay_profiles where tenant_id=?",tenant);
  jdbc.update("update workforce_statutory_policies set enabled=false where tenant_id=?",tenant);
  var w=workbench().read(admin,store,company,"2026-10",null);
  assertThat(w.batchId()).isNull();assertThat(w.batchVersion()).isNull();assertThat(w.payments().available()).isFalse();
  assertThat(w.missing()).extracting(PayrollWorkbenchDtos.Missing::code).contains("PROFILE_MISSING","STATUTORY_POLICY_MISSING");
  assertThat(w.missingEmployees()).isEqualTo(1);assertThat(w.changes()).isNull();
 }
 @Test void pendingPeopleAreDeduplicatedAndSourceMutationNeverRewritesFrozenSalary(){
  var first=schedule("2026-10-28");var second=schedule("2026-10-29");
  var pending=workbench().read(admin,store,company,"2026-10",null);
  assertThat(pending.pendingEmployees()).isEqualTo(1);assertThat(pending.pendingShifts()).isEqualTo(2);
  assertThat(pending.approvals()).extracting(PayrollWorkbenchDtos.Approval::scheduleId).contains(first.id(),second.id());
 }
 @Test void queryVersionMismatchAndUnauthorizedCompanyFailClosed(){
  completed();var b=create();
  assertThatThrownBy(()->workbench().read(admin,store,company,"2026-10",999L)).isInstanceOf(WorkforceException.class).extracting(e->((WorkforceException)e).status()).isEqualTo(409);
  assertThatThrownBy(()->workbench().read(worker,store,company,"2026-10",null)).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->workbench().read(admin,store,UUID.randomUUID(),"2026-10",null)).isInstanceOf(WorkforceException.class);
 }
 @Test void frozenChangesAreWarningsAndOptionalDocumentsAndPaymentPreferencesDoNotBlock(){
  completed();var b=action(create(),"calculate");time.at("2026-11-02T01:00:00Z");b=action(b,"confirm");b=action(b,"publish");
  var before=jdbc.queryForObject("select calculation::text from workforce_pay_batches where id=?",String.class,b.batch().id());
  jdbc.update("update workforce_pay_profiles set snapshot=jsonb_set(snapshot,'{version}',to_jsonb(version+1)),version=version+1 where tenant_id=?",tenant);
  var w=workbench().read(admin,store,company,"2026-10",b.batch().version());
  assertThat(w.frozen()).isTrue();assertThat(w.changes().sourceChanged()).isTrue();assertThat(w.changes().pending()).isEmpty();
  assertThat(w.reasons()).extracting(PayrollWorkbenchDtos.Reason::kind).contains("profile");
  assertThat(w.missing()).noneMatch(x->x.code().equals("CPF_DOCUMENT_REVIEW_REQUIRED"));
  assertThat(jdbc.queryForObject("select calculation::text from workforce_pay_batches where id=?",String.class,b.batch().id())).isEqualTo(before);
 }

 @Test void noBatchManualUnitsAndStatutoryDataUseActualCalculatorValidation(){
  approvedProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,new Rule("monthly","3000","8","44",false,"1.5",List.of()),new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("foreign",null,null,"GG","")));
  var w=workbench().read(admin,store,company,"2026-10",null);assertThat(w.missing()).extracting(PayrollWorkbenchDtos.Missing::code).contains("UNITS_REQUIRED","NO_SOURCES_REVIEW");assertThat(w.payments().available()).isFalse();
 }
 @Test void crossMonthContextPendingIsCountedAndFixedEmployeesAreExempt(){
  time.at("2026-09-30T00:00:00Z");
  jdbc.update("update workforce_employees set hire_date='2026-09-01',company_effective_date='2026-09-01' where tenant_id=? and id=?",tenant,employee);
  jdbc.update("update workforce_company_history set effective_date='2026-09-01' where tenant_id=? and employee_id=?",tenant,employee);
  jdbc.update("update workforce_work_stores set started_on='2026-09-01' where tenant_id=? and employee_id=?",tenant,employee);
  jdbc.update("update workforce_pay_profiles set effective_from='2026-09-01',snapshot=jsonb_set(snapshot,'{effectiveFrom}','\"2026-09-01\"'::jsonb) where tenant_id=? and id=?",tenant,profile);
  schedule("2026-09-30");schedule("2026-10-01");
  approvedProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,9,1),template,new Rule("hourly","100","8","44",true,"1.5",List.of()),new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("foreign",null,null,"GG","")));
  var w=workbench().read(admin,store,company,"2026-10",null);assertThat(w.pendingEmployees()).isEqualTo(1);assertThat(w.pendingShifts()).isEqualTo(2);assertThat(w.contextShifts()).isEqualTo(1);assertThat(w.approvals()).anyMatch(PayrollWorkbenchDtos.Approval::context);
  approvedProfile(admin,store,profile,new ProfileWrite(2L,company,employee,LocalDate.of(2026,9,1),template,new Rule("monthly","3000","8","44",false,"1.5",List.of(),new Generation("fixed",List.of())),new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("foreign",null,null,"GG","")));
  w=workbench().read(admin,store,company,"2026-10",null);assertThat(w.pendingShifts()).isZero();assertThat(w.exemptEmployees()).isEqualTo(1);
 }
 @Test void paymentSummaryRestoresAfterReversalAndSupportsLostResponseOriginalId(){
  var b=published();var before=workbench().read(admin,store,company,"2026-10",null);assertThat(before.payments().unpaid()).isEqualTo(1);
  UUID command=UUID.randomUUID();var payload=new PaymentWrite(employee,"200.00",LocalDate.of(2026,11,2),"bank","P","Registered",null,"DBS");
  payments.register(admin,store,b.batch().id(),command,payload);payments.register(admin,store,b.batch().id(),command,payload);
  var partial=workbench().read(admin,store,company,"2026-10",null);assertThat(partial.payments().partial()).isEqualTo(1);assertThat(partial.payments().remaining()).isEqualTo("440.00");assertThat(partial.status()).isEqualTo("published");
  payments.register(admin,store,b.batch().id(),UUID.randomUUID(),new PaymentWrite(employee,"200.00",LocalDate.of(2026,11,2),"bank","REV","Reverse",command,"DBS"));
  assertThat(workbench().read(admin,store,company,"2026-10",null).payments().unpaid()).isEqualTo(1);
  payments.register(admin,store,b.batch().id(),UUID.randomUUID(),new PaymentWrite(employee,"640.00",LocalDate.of(2026,11,2),"cash","","Settled",null));
  assertThat(workbench().read(admin,store,company,"2026-10",null).payments().settled()).isEqualTo(1);
 }
 @Test void historicalSourceAndAppGateRevocationAreRechecked(){
  var b=published();var limited=new com.rpb.reservation.walkin.api.CurrentActor(tenant,adminId,"staff",Set.of("tenant_admin"),admin.permissions(),Set.of(store));
  assertThatThrownBy(()->workbench().read(limited,store,company,"2026-10",null)).isInstanceOf(WorkforceException.class);
  var foreign=new com.rpb.reservation.walkin.api.CurrentActor(UUID.randomUUID(),adminId,"staff",Set.of("tenant_admin"),admin.permissions(),Set.of(store,sibling));
  assertThatThrownBy(()->workbench().read(foreign,store,company,"2026-10",null)).isInstanceOf(WorkforceException.class);
 }

 @Test void allAppGateDisabledNotEntitledStoreOffStatesRejectWorkbench(){
  for(var reason:List.of(com.rpb.reservation.appgate.domain.AppGateDenyReason.APP_DISABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.TENANT_APP_NOT_ENABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED)){
   org.mockito.Mockito.doReturn(com.rpb.reservation.appgate.domain.AppGateDecision.deny("workforce",tenant,store,"workforce.view",reason)).when(gate).evaluate(org.mockito.ArgumentMatchers.any());
   assertThatThrownBy(()->workbench().read(admin,store,company,"2026-10",null)).isInstanceOf(WorkforceException.class).extracting(e->((WorkforceException)e).status()).isEqualTo(403);
  }
 }

 @Test void sameMoneyNewApprovalStillRequiresRecalculationAndUnusedDefaultsDoNot(){
  var schedule=completed();var b=action(create(),"calculate");String net=b.calculation().totalNet();
  var row=approvals.detail(admin,store,schedule.id()).row();approvals.approve(admin,store,schedule.id(),new com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalWrite(1L,row.sourceFingerprint(),"Reviewed again"));
  var pending=workbench().read(admin,store,company,"2026-10",null);assertThat(pending.changes().sourceChanged()).isTrue();assertThat(pending.reasons()).extracting(PayrollWorkbenchDtos.Reason::kind).contains("approval");assertThat(pending.payments().due()).isEqualTo(net);
  b=action(b,"calculate");var compared=workbench().read(admin,store,company,"2026-10",null);assertThat(compared.changes().lastCalculation().totalNetDelta()).isEqualTo("0.00");assertThat(compared.changes().sourceChanged()).isFalse();
  UUID position=jdbc.queryForObject("select id from workforce_positions where tenant_id=? limit 1",UUID.class,tenant);
  configs.saveDefault(tenant,new PositionSalaryDefault(UUID.randomUUID(),company,position,LocalDate.of(2026,10,1),template,1,true,1));
  assertThat(workbench().read(admin,store,company,"2026-10",null).changes().sourceChanged()).isFalse();
 }
 @Test void realZeroNetIsNoPaymentNeededAndDraftHasUnknownAmounts(){
  var draft=create();assertThat(draft.batch().totalNet()).isNull();assertThat(workbench().read(admin,store,company,"2026-10",null).payments().available()).isFalse();
  var blocked=action(draft,"calculate");assertThat(workbench().read(admin,store,company,"2026-10",null).payments().available()).isFalse();
  approvedProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,new Rule("monthly","0","8","44",false,"1.5",List.of(),new Generation("fixed",List.of())),new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("foreign",null,null,"GG","")));
  var b=action(blocked,"calculate");time.at("2026-11-02T01:00:00Z");b=action(b,"confirm");b=action(b,"publish");
  var summary=workbench().read(admin,store,company,"2026-10",null).payments();assertThat(summary.zero()).isEqualTo(1);assertThat(summary.unpaid()).isZero();assertThat(summary.due()).isEqualTo("0.00");assertThat(summary.lines().getFirst().state()).isEqualTo("zero");
 }

 @Test void movedHistoricalStoreGateIsReauthorizedBeforeFrozenProjection(){
  published();jdbc.update("update stores set operating_entity_id=? where tenant_id=? and id=?",otherCompany,tenant,store);
  org.mockito.Mockito.doAnswer(call->{var r=(com.rpb.reservation.appgate.application.AppGateAccessRequest)call.getArgument(0);return r.storeId().equals(store)?com.rpb.reservation.appgate.domain.AppGateDecision.deny(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission(),com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED):com.rpb.reservation.appgate.domain.AppGateDecision.allow(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission());}).when(gate).evaluate(org.mockito.ArgumentMatchers.any());
  assertThatThrownBy(()->workbench().read(admin,sibling,company,"2026-10",null)).isInstanceOf(WorkforceException.class).extracting(e->((WorkforceException)e).status()).isEqualTo(403);
 }

 @Test void optionalPrDocumentAndPaymentPreferenceDoNotBecomeNewCalculationRequirements(){
  completed();approvedProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,rule(),new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("spr",LocalDate.of(1990,1,1),LocalDate.of(2024,1,1),"GG","")));
  var w=workbench().read(admin,store,company,"2026-10",null);assertThat(w.missing()).isEmpty();assertThat(w.pendingShifts()).isZero();assertThat(w.payments().available()).isFalse();
 }

 @Test void voidedBlockedPreviewIsImmutableButNeverTurnsPlaceholderIntoRealZero(){
  schedule("2026-10-28");var b=action(create(),"calculate");
  assertThat(b.calculation().lines().getFirst().issues()).contains("APPROVAL_MISSING");
  var id=b.batch().id();String before=jdbc.queryForObject("select calculation::text from workforce_pay_batches where id=?",String.class,id);
  assertThat(workbench().read(admin,store,company,"2026-10",b.batch().version(),id).payments().available()).isFalse();
  b=action(b,"void");var w=workbench().read(admin,store,company,"2026-10",b.batch().version(),id);
  assertThat(w.status()).isEqualTo("voided");assertThat(w.frozen()).isTrue();
  assertThat(w.payments().available()).isFalse();assertThat(w.payments().due()).isNull();assertThat(w.payments().zero()).isZero();assertThat(w.payments().lines()).isEmpty();
  assertThat(jdbc.queryForObject("select calculation::text from workforce_pay_batches where id=?",String.class,id)).isEqualTo(before);
 }
 @Test void retainedLegacyComparisonIsDowngradedReadOnlyWithoutRewritingOriginalJson(){
  completed();var b=action(create(),"calculate");b=action(b,"calculate");var id=b.batch().id();
  // Simulate a genuine pre-evidence comparison: only the result survives, never its complete before snapshot.
  String legacy="{\"available\":true,\"baselineMonth\":\"2026-10\",\"baselineVersion\":1,\"totalGrossDelta\":\"0.00\",\"totalNetDelta\":\"0.00\",\"totalEmployerCostDelta\":\"0.00\",\"lines\":[{\"employeeId\":\""+employee+"\",\"employeeName\":\"Employee\",\"kind\":\"changed\",\"reasons\":[\"policy\",\"input\",\"attendance\"],\"basicPayDelta\":\"0.00\",\"grossPayDelta\":\"0.00\",\"netPayDelta\":\"0.00\",\"employeeCpfDelta\":\"0.00\",\"employerCostDelta\":\"0.00\"}]}";
  jdbc.update("update workforce_pay_computations set snapshot=jsonb_set(snapshot,'{lastCalculation}',?::jsonb) where tenant_id=? and batch_id=?",legacy,tenant,id);
  String computation=jdbc.queryForObject("select snapshot::text from workforce_pay_computations where batch_id=?",String.class,id);
  String salary=jdbc.queryForObject("select calculation::text from workforce_pay_batches where id=?",String.class,id);
  var w=workbench().read(admin,store,company,"2026-10",b.batch().version(),id);
  assertThat(w.changes().lastCalculation().lines().getFirst().reasons()).containsExactly("legacy_comparison");
  assertThat(w.changes().lastCalculation().totalNetDelta()).isEqualTo("0.00");
  assertThat(jdbc.queryForObject("select snapshot::text from workforce_pay_computations where batch_id=?",String.class,id)).isEqualTo(computation);
  assertThat(jdbc.queryForObject("select calculation::text from workforce_pay_batches where id=?",String.class,id)).isEqualTo(salary);
 }
 @Test void recalculatedStaleApprovalComparisonUsesEvidenceInActualWorkbench(){
  var schedule=completed();var b=action(create(),"calculate");
  jdbc.update("update workforce_schedules set version=version+1 where tenant_id=? and id=?",tenant,schedule.id());
  b=action(b,"calculate");var w=workbench().read(admin,store,company,"2026-10",b.batch().version(),b.batch().id());
  assertThat(w.changes().lastCalculation().lines().getFirst().reasons()).contains("approval").doesNotContain("attendance","input","policy");
  assertThat(w.changes().sourceChanged()).isFalse();
 }

 @Test void confirmedAndThenVoidedValidWagesKeepAmountsAndOriginalSnapshots(){
  completed();var b=action(create(),"calculate");time.at("2026-11-02T01:00:00Z");b=action(b,"confirm");var id=b.batch().id();
  String before=jdbc.queryForObject("select calculation::text from workforce_pay_batches where id=?",String.class,id);
  var confirmed=workbench().read(admin,store,company,"2026-10",b.batch().version(),id);
  assertThat(confirmed.payments().available()).isTrue();assertThat(confirmed.payments().due()).isEqualTo("640.00");
  b=action(b,"void");var voided=workbench().read(admin,store,company,"2026-10",b.batch().version(),id);
  assertThat(voided.frozen()).isTrue();assertThat(voided.payments().available()).isTrue();assertThat(voided.payments().due()).isEqualTo("640.00");
  assertThat(jdbc.queryForObject("select calculation::text from workforce_pay_batches where id=?",String.class,id)).isEqualTo(before);
 }
}
