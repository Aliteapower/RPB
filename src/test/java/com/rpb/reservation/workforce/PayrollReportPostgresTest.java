package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.reporting.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import static com.rpb.reservation.workforce.reporting.PayrollReportDtos.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.*;
import java.time.*;import java.util.*;

class PayrollReportPostgresTest extends WorkforcePayrollPostgresTest {
 PayrollReportService reports; PayrollReconciliationService reconciliation;PayrollReportRepository reportRepository;
 @BeforeEach void reportingSetup(){
  reportRepository=spy(new PayrollReportRepository(jdbc));
  reports=proxy(new PayrollReportService(new PayrollAccess(access,directory,jdbc),reportRepository,payRepository,sources,new WorkforceJson(new ObjectMapper().findAndRegisterModules()),time));
  reconciliation=proxy(new PayrollReconciliationService(reports));
 }
 Report report(){return reports.read(admin,store,"2026-10",List.of(company));}
 @Test void reportSeparatesForecastFrozenAndMissingWithoutLeakingPrivateSnapshots()throws Exception{
  completed();var b=action(create(),"calculate");var preview=reports.read(admin,store,"2026-10",List.of(company,otherCompany));
  assertThat(preview.forecast().totals().gross()).isEqualTo("800.00");assertThat(preview.frozen().companyCount()).isZero();
  assertThat(preview.companies()).anySatisfy(c->{assertThat(c.companyId()).isEqualTo(otherCompany);assertThat(c.status()).isEqualTo("missing");assertThat(c.totals()).isNull();});
  time.at("2026-11-02T01:00:00Z");b=action(b,"confirm");var r=report();assertThat(r.frozen().employeeCount()).isEqualTo(1);assertThat(r.frozen().payrollRows()).isEqualTo(1);assertThat(r.forecast().companyCount()).isZero();assertThat(r.frozen().totals().sdl()).isEqualTo("2.00");
  var text=new ObjectMapper().findAndRegisterModules().writeValueAsString(r);assertThat(text).doesNotContain("birthDate","prDate","electionReference","sourceFingerprint","Checked","accountId");
  assertThat(r.employees().getFirst().totals().net()).isEqualTo("640.00");
 }
 @Test void paymentReversalChangesReportTokenAndKeepsFrozenAmounts(){
  var b=published();var initial=report();UUID p=UUID.randomUUID();payments.register(admin,store,b.batch().id(),p,new PaymentWrite(employee,"200",LocalDate.of(2026,11,2),"cash","","Paid privately",null));
  var paid=report();assertThat(paid.frozen().totals().paid()).isEqualTo("200.00");assertThat(paid.frozen().totals().remaining()).isEqualTo("440.00");assertThat(paid.fingerprint()).isNotEqualTo(initial.fingerprint());
  assertThatThrownBy(()->reports.export(admin,store,"2026-10",List.of(company),"companies",initial.fingerprint())).isInstanceOfSatisfying(WorkforceException.class,e->assertThat(e.code()).isEqualTo("WORKFORCE_REPORT_CHANGED"));
  payments.register(admin,store,b.batch().id(),UUID.randomUUID(),new PaymentWrite(employee,"200",LocalDate.of(2026,11,2),"cash","","Reverse privately",p));
  var reversed=report();assertThat(reversed.frozen().totals().paid()).isEqualTo("0.00");assertThat(reversed.payments()).extracting(PaymentRow::signedAmount).containsExactlyInAnyOrder("200.00","-200.00");
  assertThat(reports.export(admin,store,"2026-10",List.of(company),"payments",reversed.fingerprint())).doesNotContain("privately","actorId");assertThat(reversed.frozen().totals().gross()).isEqualTo("800.00");
 }
 @Test void selectedCompanyScopeIsExplicitAndFailsClosed(){
  published();assertThatThrownBy(()->reports.read(worker,store,"2026-10",List.of(company))).isInstanceOf(WorkforceException.class);
  UUID managerId=account("store_manager",Set.of(store,sibling),Set.of("workforce.view"));admins.save(admin,store,managerId,new com.rpb.reservation.workforce.application.WorkforceDtos.AdministratorWrite(0L,false,company));
  var manager=new CurrentActor(tenant,managerId,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store,sibling));
  assertThat(reports.read(manager,store,"2026-10",List.of(company)).frozen().companyCount()).isEqualTo(1);
  assertThatThrownBy(()->reports.read(manager,store,"2026-10",List.of(company,otherCompany))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->reports.read(admin,store,"2026-10",List.of(company,UUID.randomUUID()))).isInstanceOf(WorkforceException.class);
  for(var ids:List.of(List.<UUID>of(),List.of(company,company)))assertThatThrownBy(()->reports.read(admin,store,"2026-10",ids)).isInstanceOf(WorkforceException.class);
 }
 @Test void sourceChangeMarksFrozenReportAndVoidedBatchDoesNotDoubleCount(){
  completed();var b=action(create(),"calculate");b=action(b,"void");assertThat(report().companies().getFirst().status()).isEqualTo("missing");
  b=action(create(),"calculate");time.at("2026-11-02T01:00:00Z");b=action(b,"confirm");var before=report();jdbc.update("update stores set currency='USD' where tenant_id=? and id=?",tenant,store);
  var after=report();assertThat(after.companies().getFirst().sourceChanged()).isTrue();assertThat(after.frozen().totals().gross()).isEqualTo(before.frozen().totals().gross());assertThat(after.fingerprint()).isNotEqualTo(before.fingerprint());
 }
 @Test void reconciliationIsExactReadOnlyAndRequiresFrozenCurrentReport(){
  completed();var b=action(create(),"calculate");var preview=report();assertThatThrownBy(()->reconciliation.compare(admin,store,new CompareWrite(company,"2026-10",preview.fingerprint(),preview.forecast().totals()))).isInstanceOf(WorkforceException.class);
  time.at("2026-11-02T01:00:00Z");b=action(b,"confirm");var r=report();var expected=r.frozen().totals();clearInvocations(logs);
  assertThat(reconciliation.compare(admin,store,new CompareWrite(company,"2026-10",r.fingerprint(),expected)).matched()).isTrue();
  var different=new Totals("799.99",expected.net(),expected.employeeCpf(),expected.employerCpf(),expected.sdl(),expected.employerCost(),"0","640");
  var result=reconciliation.compare(admin,store,new CompareWrite(company,"2026-10",r.fingerprint(),different));assertThat(result.matched()).isFalse();assertThat(result.items().getFirst().difference()).isEqualTo("0.01");
  assertThat(report().fingerprint()).isEqualTo(r.fingerprint());verifyNoInteractions(logs);
  assertThatThrownBy(()->reconciliation.compare(admin,store,new CompareWrite(company,"2026-10","stale",expected))).isInstanceOfSatisfying(WorkforceException.class,e->assertThat(e.code()).isEqualTo("WORKFORCE_REPORT_CHANGED"));
  assertThatThrownBy(()->reconciliation.compare(admin,store,new CompareWrite(company,"2026-10",r.fingerprint(),new Totals("1e3","0","0","0","0","0","0","0")))).isInstanceOf(WorkforceException.class);
 }
 @Test void groupCountsUniquePeopleButKeepsSeparateCompanyPayrollRows(){
  var b=published();
  // Seed a second frozen company liability for the same person (in-month company move).
  jdbc.update("insert into workforce_pay_batches(id,tenant_id,company_id,company_name,period_month,status,version,calculation,created_by,confirmed_by,confirmed_at) select ?,tenant_id,?,'Second company',period_month,'confirmed',1,jsonb_set(calculation,'{companyName}',to_jsonb('Second company'::text)),created_by,confirmed_by,confirmed_at from workforce_pay_batches where tenant_id=? and id=?",UUID.randomUUID(),otherCompany,tenant,b.batch().id());
  var r=reports.read(admin,store,"2026-10",List.of(company,otherCompany));assertThat(r.frozen().companyCount()).isEqualTo(2);assertThat(r.frozen().employeeCount()).isEqualTo(1);assertThat(r.frozen().payrollRows()).isEqualTo(2);assertThat(r.frozen().totals().gross()).isEqualTo("1600.00");assertThat(r.frozen().totals().sdl()).isEqualTo("4.00");
  var csv=reports.export(admin,store,"2026-10",List.of(company,otherCompany),"employees",r.fingerprint());assertThat(csv).startsWith("\uFEFFmonth,currency").contains("employee_sdl_unrounded","employee_cost_estimate");assertThat(csv.lines()).hasSize(3);
 }
 @Test void exportRevalidatesFrozenSupportStoresTenantAndAppGate(){
  var b=published();var before=report();var limited=new CurrentActor(tenant,adminId,"staff",Set.of("tenant_admin"),Set.of("workforce.view","workforce.admin.manage","tenant.admin.manage"),Set.of(store,sibling));
  jdbc.update("update workforce_pay_batches set calculation=jsonb_set(calculation,'{lines,0,sources,0,storeId}',to_jsonb(?::text)) where tenant_id=? and id=?",otherStore.toString(),tenant,b.batch().id());
  assertThatThrownBy(()->reports.export(limited,store,"2026-10",List.of(company),"employees",before.fingerprint())).isInstanceOfSatisfying(WorkforceException.class,e->assertThat(e.status()).isEqualTo(403));
  var foreign=new CurrentActor(UUID.randomUUID(),adminId,"staff",admin.roles(),admin.permissions(),admin.storeIds());
  assertThatThrownBy(()->reports.read(foreign,store,"2026-10",List.of(company))).isInstanceOf(WorkforceException.class);
  doAnswer(call->{var req=(com.rpb.reservation.appgate.application.AppGateAccessRequest)call.getArgument(0);return com.rpb.reservation.appgate.domain.AppGateDecision.deny(req.appKey(),req.tenantId(),req.storeId(),req.requiredPermission(),com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED);}).when(gate).evaluate(any());
  assertThatThrownBy(()->report()).isInstanceOfSatisfying(WorkforceException.class,e->assertThat(e.status()).isEqualTo(403));
 }
 @Test void readsAreRepeatableReadOnlyAndCompanyLimitsNeverSilentlyTruncate(){
  doAnswer(call->{assertThat(jdbc.queryForObject("show transaction_isolation",String.class)).isEqualTo("repeatable read");assertThat(jdbc.queryForObject("show transaction_read_only",String.class)).isEqualTo("on");return call.callRealMethod();}).when(reportRepository).batch(any(),any(),any());
  assertThat(report().companies()).hasSize(1);
  assertThatThrownBy(()->reports.read(admin,store,"2026-10",java.util.stream.IntStream.range(0,51).mapToObj(i->UUID.randomUUID()).toList())).isInstanceOfSatisfying(WorkforceException.class,e->assertThat(e.status()).isEqualTo(413));
 }
 @Test void repeatedSourceChangesInvalidateTokenEvenWhenWarningIsAlreadyVisible(){
  published();jdbc.update("update operating_entities set display_name='Changed once' where tenant_id=? and id=?",tenant,company);var once=report();assertThat(once.companies().getFirst().sourceChanged()).isTrue();
  jdbc.update("update operating_entities set display_name='Changed twice' where tenant_id=? and id=?",tenant,company);var twice=report();assertThat(twice.fingerprint()).isNotEqualTo(once.fingerprint());
  assertThatThrownBy(()->reports.export(admin,store,"2026-10",List.of(company),"companies",once.fingerprint())).isInstanceOfSatisfying(WorkforceException.class,e->assertThat(e.code()).isEqualTo("WORKFORCE_REPORT_CHANGED"));
 }
 @Test void draftInputChangeInvalidatesExportEvenBeforeAnyCalculation(){
  var b=create();var before=report();batches.input(admin,store,b.batch().id(),employee,new InputWrite(0L,null,null,true,false,List.of(),"Draft review only"));
  var after=report();assertThat(after.companies().getFirst().status()).isEqualTo("draft");assertThat(after.fingerprint()).isNotEqualTo(before.fingerprint());
  assertThatThrownBy(()->reports.export(admin,store,"2026-10",List.of(company),"companies",before.fingerprint())).isInstanceOfSatisfying(WorkforceException.class,e->assertThat(e.code()).isEqualTo("WORKFORCE_REPORT_CHANGED"));
 }
}
