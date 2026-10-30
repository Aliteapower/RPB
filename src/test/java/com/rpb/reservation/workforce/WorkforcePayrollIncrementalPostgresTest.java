package com.rpb.reservation.workforce;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.payroll.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.any;

class WorkforcePayrollIncrementalPostgresTest extends WorkforcePayrollPostgresTest {
 @Test void unchangedRowsAreReusedAndMonthlyInputKeepsTheLastCalculationBaseline(){
  completed();var first=action(create(),"calculate");
  var initial=payRepository.computation(tenant,first.batch().id()).orElseThrow();assertThat(initial.recalculated()).isEqualTo(1);
  var same=action(first,"calculate");assertThat(payRepository.computation(tenant,same.batch().id()).orElseThrow().reused()).isEqualTo(1);
  assertThat(same.calculation()).isEqualTo(first.calculation());
  var edited=batches.input(admin,store,same.batch().id(),employee,new InputWrite(0L,null,null,false,false,List.of(new Item("Bonus","ow_additional","75")),"Bonus"));
  assertThat(edited.calculation()).isNull();assertThat(payRepository.computation(tenant,edited.batch().id()).orElseThrow().cache().snapshot()).isEqualTo(same.calculation());
  var changed=action(edited,"calculate");var cache=payRepository.computation(tenant,changed.batch().id()).orElseThrow();
  assertThat(cache.recalculated()).isEqualTo(1);assertThat(cache.lastCalculation().totalGrossDelta()).isEqualTo("75.00");assertThat(changed.calculation().totalGross()).isEqualTo("875.00");
  var full=action(changed,"calculate_full");assertThat(full.calculation()).isEqualTo(changed.calculation());
  time.at("2026-11-02T01:00:00Z");var confirmed=action(full,"confirm");assertThat(confirmed.calculation()).isEqualTo(full.calculation());
  assertThatThrownBy(()->action(confirmed,"calculate")).hasMessage("WORKFORCE_CONFLICT");
 }
 @Test void cacheCanNeverReplaceFullConfirmationValidation(){
  completed();time.at("2026-11-02T01:00:00Z");var first=action(create(),"calculate");
  jdbc.update("update workforce_pay_computations set snapshot=jsonb_set(snapshot,'{cache,snapshot,lines,0,grossPay}','\"999.00\"'::jsonb) where tenant_id=? and batch_id=?",tenant,first.batch().id());
  var reused=action(first,"calculate");assertThat(reused.calculation().lines().getFirst().grossPay()).isEqualTo("999.00");
  assertThatThrownBy(()->action(reused,"confirm")).hasMessage("WORKFORCE_PAYROLL_SOURCE_CHANGED");
  var corrected=action(reused,"calculate_full");assertThat(corrected.calculation().totalGross()).isEqualTo("800.00");assertThat(action(corrected,"confirm").batch().status()).isEqualTo("confirmed");
 }
 @Test void incompatibleCacheFallsBackAndTenantForeignKeysProtectOptimizationEvidence(){
  completed();var first=action(create(),"calculate");
  jdbc.update("update workforce_pay_computations set snapshot='{}'::jsonb where tenant_id=? and batch_id=?",tenant,first.batch().id());
  var repaired=action(first,"calculate");assertThat(repaired.calculation()).isEqualTo(first.calculation());assertThat(payRepository.computation(tenant,first.batch().id()).orElseThrow().recalculated()).isEqualTo(1);
  assertThatThrownBy(()->jdbc.update("insert into workforce_pay_computations(tenant_id,batch_id,company_id,snapshot) values(?,?,?,'{}')",UUID.randomUUID(),first.batch().id(),company)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("update workforce_pay_computations set company_id=? where tenant_id=? and batch_id=?",otherCompany,tenant,first.batch().id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void failedAuditRollsBackTheWageCacheAndReceiptTogether(){
  completed();var first=action(create(),"calculate");var oldCache=payRepository.computation(tenant,first.batch().id()).orElseThrow();
  var edited=batches.input(admin,store,first.batch().id(),employee,new InputWrite(0L,null,null,false,false,List.of(new Item("Bonus","ow_additional","75")),"Bonus"));
  doThrow(new IllegalStateException("audit failure")).when(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
  var command=UUID.randomUUID();assertThatThrownBy(()->batches.action(admin,store,edited.batch().id(),command,new ActionWrite(edited.batch().version(),"calculate","Audit"))).hasMessage("audit failure");
  assertThat(payRepository.computation(tenant,first.batch().id())).contains(oldCache);var state=payRepository.find(tenant,first.batch().id()).orElseThrow();assertThat(state.version()).isEqualTo(edited.batch().version());assertThat(state.calculation()).isNull();
  assertThat(jdbc.queryForObject("select count(*) from workforce_pay_commands where tenant_id=? and id=?",Integer.class,tenant,command)).isZero();
 }
 @Test void adjacentConfirmedMonthIsComparedWithoutRewritingItsSnapshot(){
  jdbc.update("update workforce_employees set hire_date='2026-09-01',company_effective_date='2026-09-01' where tenant_id=? and id=?",tenant,employee);
  jdbc.update("update workforce_company_history set effective_date='2026-09-01' where tenant_id=? and employee_id=?",tenant,employee);
  jdbc.update("update workforce_work_stores set started_on='2026-09-01' where tenant_id=? and employee_id=?",tenant,employee);
  jdbc.update("update workforce_pay_profiles set effective_from='2026-09-01',snapshot=jsonb_set(snapshot,'{effectiveFrom}','\"2026-09-01\"'::jsonb) where tenant_id=? and id=?",tenant,profile);
  time.at("2026-09-28T01:00:00Z");var shift=shifts.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ShiftWrite(0L,"September","09:00","17:00",false,"none",0,0,0,0,false,true));
  var schedule=roster.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ScheduleWrite(0L,employee,shift.id(),java.time.LocalDate.of(2026,9,28),"scheduled"));
  punches.punch(worker,store,UUID.randomUUID(),new com.rpb.reservation.workforce.attendance.AttendanceDtos.ClockWrite(schedule.id(),"IN"));time.at("2026-09-28T09:00:00Z");punches.punch(worker,store,UUID.randomUUID(),new com.rpb.reservation.workforce.attendance.AttendanceDtos.ClockWrite(schedule.id(),"OUT"));
  var approval=approvals.detail(admin,store,schedule.id()).row();approvals.approve(admin,store,schedule.id(),new com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalWrite(0L,approval.sourceFingerprint(),"Checked"));
  time.at("2026-10-02T01:00:00Z");var previous=action(batches.create(admin,store,UUID.randomUUID(),new BatchWrite(company,"2026-09")),"calculate");previous=action(previous,"confirm");var previousId=previous.batch().id();
  var frozen=jdbc.queryForObject("select calculation::text from workforce_pay_batches where tenant_id=? and id=?",String.class,tenant,previousId);
  time.at("2026-10-28T01:00:00Z");completed();var current=action(create(),"calculate");
  current=batches.input(admin,store,current.batch().id(),employee,new InputWrite(0L,null,null,false,false,List.of(new Item("Bonus","ow_additional","75")),"Bonus"));current=action(current,"calculate");
  var service=proxy(new PayrollProgressService(access,new PayrollAccess(access,directory,jdbc),payRepository,sources,new PayrollCalculationService(time)));var changes=service.changes(admin,store,current.batch().id());
  assertThat(changes.previousMonth().available()).isTrue();assertThat(changes.previousMonth().baselineMonth()).isEqualTo("2026-09");assertThat(changes.previousMonth().totalGrossDelta()).isEqualTo("75.00");assertThat(changes.previousMonth().totalNetDelta()).isEqualTo("60.00");
  assertThat(jdbc.queryForObject("select calculation::text from workforce_pay_batches where tenant_id=? and id=?",String.class,tenant,previousId)).isEqualTo(frozen);
 }
 @Test void allCacheHitsStillReleaseTransactionsAndRejectConcurrentSourceChanges()throws Exception{
  var schedule=completed();var first=action(create(),"calculate");var old=payRepository.computation(tenant,first.batch().id()).orElseThrow();
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new java.util.concurrent.atomic.AtomicInteger();
  var calculator=new PayrollCalculationService(time){
   @Override public PayrollSnapshot calculate(PayrollSources.Capture c,java.time.YearMonth m){calls.incrementAndGet();return super.calculate(c,m);}
   @Override public PayrollSnapshot aggregate(PayrollSources.Capture c,java.time.YearMonth m,List<PayrollLine> rows){assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();entered.countDown();PayrollBatchConcurrencyPostgresTest.await(release);return super.aggregate(c,m,rows);}
  };
  var service=new PayrollBatchService(access,new PayrollAccess(access,directory,jdbc),payRepository,sources,calculator,repository,new com.rpb.reservation.workforce.application.WorkforceAudit(logs,new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()),time,tx);
  var pool=Executors.newFixedThreadPool(2);
  try{
   var calculation=pool.submit(()->service.action(admin,store,first.batch().id(),UUID.randomUUID(),new ActionWrite(first.batch().version(),"calculate","Cache race")));
   assertThat(entered.await(20,TimeUnit.SECONDS)).isTrue();assertThat(calls).hasValue(0);
   pool.submit(()->corrections.submit(worker,store,UUID.randomUUID(),new com.rpb.reservation.workforce.attendance.AttendanceDtos.CorrectionWrite(schedule.id(),2L,schedule.startAt(),schedule.endAt().minusSeconds(3600),List.of(),false,"Cache source race"))).get(10,TimeUnit.SECONDS);
   release.countDown();assertThatThrownBy(()->calculation.get(20,TimeUnit.SECONDS)).rootCause().hasMessage("WORKFORCE_PAYROLL_SOURCE_CHANGED");assertThat(payRepository.computation(tenant,first.batch().id())).contains(old);
  }finally{release.countDown();pool.shutdownNow();}
 }
 @Test void newReadAndBulkPathsHonorAllAppGateDenialsBeforeSalaryAccess(){
  var progress=new PayrollProgressService(access,new PayrollAccess(access,directory,jdbc),payRepository,sources,new PayrollCalculationService(time));
  var bulk=new PayrollBulkService(batches,new PayrollProgressRepository(jdbc,new com.rpb.reservation.workforce.application.WorkforceJson(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules())),access,tx);
  for(var reason:List.of(com.rpb.reservation.appgate.domain.AppGateDenyReason.APP_DISABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.TENANT_APP_NOT_ENABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED)){
   doAnswer(call->{com.rpb.reservation.appgate.application.AppGateAccessRequest r=call.getArgument(0);return com.rpb.reservation.appgate.domain.AppGateDecision.deny(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission(),reason);}).when(gate).evaluate(any());
   assertThatThrownBy(()->progress.changes(admin,store,UUID.randomUUID())).hasMessage(reason.name());
   assertThatThrownBy(()->bulk.execute(admin,store,UUID.randomUUID(),new PayrollProgressDtos.BulkWrite("calculate","Checked",List.of(new PayrollProgressDtos.BulkEntry(UUID.randomUUID(),1L))))).hasMessage(reason.name());
  }
  assertThat(jdbc.queryForObject("select count(*) from workforce_pay_bulk_operations where tenant_id=?",Integer.class,tenant)).isZero();
 }
}
