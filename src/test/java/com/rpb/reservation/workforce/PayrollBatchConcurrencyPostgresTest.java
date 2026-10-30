package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class PayrollBatchConcurrencyPostgresTest extends WorkforcePayrollPostgresTest {
    PayrollBatchService instrument(PayrollCalculationService calculator) {
        return proxy(new PayrollBatchService(access,new PayrollAccess(access,directory,jdbc),payRepository,sources,
            calculator,repository,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules()),time,tx));
    }
    static void await(CountDownLatch latch) {
        try { if(!latch.await(20,TimeUnit.SECONDS))throw new IllegalStateException("latch timeout"); }
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
    }
    @Test void calculationReleasesTransactionsAndSourceLocksThenRejectsChangedSources() throws Exception {
        var schedule=completed();var initial=create();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var service=instrument(new PayrollCalculationService(time){@Override public PayrollSnapshot calculate(PayrollSources.Capture c,YearMonth m){
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();entered.countDown();await(release);return super.calculate(c,m);
        }});
        var pool=Executors.newFixedThreadPool(2);var command=UUID.randomUUID();
        try {
            var calculation=pool.submit(()->service.action(admin,store,initial.batch().id(),command,new ActionWrite(initial.batch().version(),"calculate","Concurrent")));
            assertThat(entered.await(20,TimeUnit.SECONDS)).isTrue();
            pool.submit(()->corrections.submit(worker,store,UUID.randomUUID(),new CorrectionWrite(schedule.id(),2L,schedule.startAt(),schedule.endAt().minusSeconds(3600),List.of(),false,"Concurrent source"))).get(10,TimeUnit.SECONDS);
            release.countDown();assertThatThrownBy(()->calculation.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(WorkforceException.class).rootCause().hasMessage("WORKFORCE_PAYROLL_SOURCE_CHANGED");
            assertThat(payRepository.find(tenant,initial.batch().id()).orElseThrow().version()).isEqualTo(initial.batch().version());
            assertThat(jdbc.queryForObject("select count(*) from workforce_pay_commands where tenant_id=? and id=?",Integer.class,tenant,command)).isZero();
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void calculationDoesNotHoldBatchLockAndCannotOverwriteNewInput() throws Exception {
        completed();var initial=create();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var service=instrument(new PayrollCalculationService(time){@Override public PayrollSnapshot calculate(PayrollSources.Capture c,YearMonth m){entered.countDown();await(release);return super.calculate(c,m);}});
        var pool=Executors.newFixedThreadPool(2);
        try {
            var calculation=pool.submit(()->service.action(admin,store,initial.batch().id(),UUID.randomUUID(),new ActionWrite(initial.batch().version(),"calculate","Concurrent")));
            assertThat(entered.await(20,TimeUnit.SECONDS)).isTrue();
            var edited=pool.submit(()->approvedInput(admin,store,initial.batch().id(),employee,new InputWrite(0L,null,null,false,false,List.of(),"New note"))).get(10,TimeUnit.SECONDS);
            release.countDown();assertThatThrownBy(()->calculation.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(WorkforceException.class).rootCause().hasMessage("WORKFORCE_CONFLICT");
            assertThat(payRepository.find(tenant,initial.batch().id()).orElseThrow().version()).isEqualTo(edited.batch().version());
            assertThat(payRepository.inputs(tenant,initial.batch().id()).getFirst().note()).isEqualTo("New note");
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void permissionsAreRecheckedAfterComputationBeforeAnyWrite() {
        completed();var initial=create();var command=UUID.randomUUID();
        var service=instrument(new PayrollCalculationService(time){@Override public PayrollSnapshot calculate(PayrollSources.Capture c,YearMonth m){
            var result=super.calculate(c,m);doThrow(WorkforceException.forbidden()).when(gate).evaluate(any());return result;
        }});
        assertThatThrownBy(()->service.action(admin,store,initial.batch().id(),command,new ActionWrite(initial.batch().version(),"calculate","Check"))).hasMessage("WORKFORCE_FORBIDDEN");
        var stored=payRepository.find(tenant,initial.batch().id()).orElseThrow();assertThat(stored.version()).isEqualTo(initial.batch().version());assertThat(stored.calculation()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from workforce_pay_commands where tenant_id=? and id=?",Integer.class,tenant,command)).isZero();
    }
    @Test void failedAutomaticCreateDoesNotLeaveDraftAndRunsWithoutTransaction() {
        approvedProfile(admin,store,profile,new ProfileWrite(1L,company,employee,java.time.LocalDate.of(2026,10,1),template,
            new Rule("monthly","3000","8","44",false,"1.5",List.of(),new Generation("fixed",List.of())),
            new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("foreign",null,null,"GG","")));
        var service=instrument(new PayrollCalculationService(time){@Override public PayrollSnapshot calculate(PayrollSources.Capture c,YearMonth m){
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();throw new IllegalStateException("compute failure");
        }});var id=UUID.randomUUID();
        assertThatThrownBy(()->service.create(admin,store,id,new BatchWrite(company,"2026-10"))).hasMessage("compute failure");
        assertThat(payRepository.find(tenant,id)).isEmpty();
    }
}
