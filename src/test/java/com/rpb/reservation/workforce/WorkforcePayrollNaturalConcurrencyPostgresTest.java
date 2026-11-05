package com.rpb.reservation.workforce;

import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.attendance.*;
import com.rpb.reservation.workforce.payroll.*;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import static org.assertj.core.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Run the named method only: inherited benchmark methods are deliberately not repeated. */
class WorkforcePayrollNaturalConcurrencyPostgresTest extends WorkforcePayrollPerformancePostgresTest {
    static final Path NATURAL_OUTPUT=Path.of("target/workforce-payroll-perf-20261030/natural-concurrency.json");
    final ThreadLocal<WriterTiming> writerTiming=new ThreadLocal<>();

    @Test void naturalClockConcurrencyOldAndNew() throws Exception {
        var originalJdbc=jdbc;var originalTx=tx;var observations=new ArrayList<Object>();
        try(var executor=new PayrollComputeExecutor(4)) {
            var counted=counting(originalJdbc.getDataSource());
            jdbc=new JdbcTemplate(counted);tx=new DataSourceTransactionManager(counted);initialize();
            for(int size:List.of(100,300,500)) {
                if(size!=100)newCompany();
                new TransactionTemplate(tx).executeWithoutResult(status->seed(size));
                var oracle=new TransactionTemplate(tx).execute(status->new LegacyPayrollCalculationService(FIXED)
                    .calculate(legacySources.capture(tenant,company,MONTH,List.of()),MONTH));
                assertSnapshot(oracle,oracle,size);
                observations.add(naturalProbe(true,executor,oracle,size));
                observations.add(naturalProbe(false,executor,oracle,size));
                writeNatural(observations,"running");
            }
            writeNatural(observations,"complete");
        }finally{active.set(null);writerTiming.remove();jdbc=originalJdbc;tx=originalTx;}
    }

    Map<String,Object> naturalProbe(boolean legacy,PayrollComputeExecutor executor,PayrollSnapshot oracle,int size) throws Exception {
        // An employee hired in March with only a March schedule is outside February's employee and source sets.
        UUID workerAccount=account("store_staff",Set.of(store),Set.of("workforce.view"));
        UUID employee=UUID.randomUUID(),schedule=UUID.randomUUID();
        String employeeNo="NAT-"+employee.toString().replace("-","").substring(0,20);
        jdbc.update("insert into workforce_employees(id,tenant_id,employee_no,name,hire_date,status,company_id,company_effective_date,main_store_id,account_id,version) values(?,?,?,'Natural March probe','2026-03-01','active',?,'2026-03-01',?,?,1)",employee,tenant,employeeNo,company,store,workerAccount);
        jdbc.update("insert into workforce_company_history(employee_id,tenant_id,company_id,effective_date,version) values(?,?,?,'2026-03-01',1)",employee,tenant,company);
        jdbc.update("insert into workforce_work_stores(employee_id,tenant_id,store_id,started_on) values(?,?,?,'2026-03-01')",employee,tenant,store);
        var shift=scheduling.shifts(tenant,store).getFirst();
        scheduling.save(tenant,store,new Schedule(schedule,employee,"Natural March probe",employeeNo,shift.id(),LocalDate.of(2026,3,2),FIXED.instant(),FIXED.instant().plusSeconds(28800),"Asia/Singapore","scheduled",shift,company,1));
        var worker=new CurrentActor(tenant,workerAccount,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));
        var punches=proxy(new ClockService(new AttendanceAccess(access,repository,scheduling),repository,scheduling,attendance,audit,FIXED,workModeClock));
        var entered=new CountDownLatch(1);var computeStarted=new AtomicLong();var computeFinished=new AtomicLong();
        PayrollCalculationService calculator=legacy?new LegacyPayrollCalculationService(FIXED){
            @Override public PayrollSnapshot calculate(PayrollSources.Capture capture,YearMonth month){
                computeStarted.set(System.nanoTime());entered.countDown();
                try{return super.calculate(capture,month);}finally{computeFinished.set(System.nanoTime());}
            }
        }:new PayrollCalculationService(FIXED,executor){
            @Override public PayrollSnapshot calculate(PayrollSources.Capture capture,YearMonth month){
                computeStarted.set(System.nanoTime());entered.countDown();
                try{return super.calculate(capture,month);}finally{computeFinished.set(System.nanoTime());}
            }
        };
        Callable<BatchDetail> calculate=legacy
            ?()->runOld(proxy(new LegacyPayrollBatchService(access,payAccess,payroll,legacySources,calculator,repository,audit,FIXED)))
            :()->runNew(new PayrollBatchService(access,payAccess,payroll,sources,calculator,repository,audit,FIXED,tx));
        var pool=Executors.newFixedThreadPool(2);var timing=new WriterTiming();var sample=new Sample(legacy?"legacy":"optimized");
        active.set(sample);long started=System.nanoTime();
        try {
            var calculation=pool.submit(calculate);
            // Notification only: the calculator immediately proceeds to the real implementation, without waiting.
            // These bounded awaits are deadlock guards, not performance assertions.
            assertThat(entered.await(180,TimeUnit.SECONDS)).isTrue();
            var writer=pool.submit(()->{
                writerTiming.set(timing);timing.started=System.nanoTime();
                try{return punches.punch(worker,store,UUID.randomUUID(),new ClockWrite(schedule,"IN"));}
                finally{timing.finished=System.nanoTime();writerTiming.remove();}
            });
            var saved=writer.get(180,TimeUnit.SECONDS);
            var detail=calculation.get(180,TimeUnit.SECONDS);
            active.set(null);
            assertThat(saved.scheduleId()).isEqualTo(schedule);assertThat(saved.employeeId()).isEqualTo(employee);
            assertThat(attendance.bySchedule(tenant,schedule)).contains(saved);
            assertSnapshot(detail.calculation(),oracle,size);
            assertThat(timing.sharedAcquisitions).hasSize(1);
            var result=new LinkedHashMap<String,Object>();
            result.put("employees",size);result.put("schedules",size*20);result.put("mode",sample.mode);
            result.put("writerLatencyMs",millis(timing.finished-timing.started));
            result.put("writerSourceSharedLockAcquisitionMs",timing.sharedAcquisitions.stream().map(WorkforcePayrollPerformancePostgresTest::millis).toList());
            result.put("writerSourceSharedLockAcquisitionTotalMs",millis(timing.sharedAcquisitions.stream().mapToLong(Long::longValue).sum()));
            result.put("writerStartedFromProbeStartMs",millis(timing.started-started));
            result.put("writerFinishedFromProbeStartMs",millis(timing.finished-started));
            result.put("calculationStartedFromProbeStartMs",millis(computeStarted.get()-started));
            result.put("calculationFinishedFromProbeStartMs",millis(computeFinished.get()-started));
            result.put("writerStartedBeforePureCalculationFinished",timing.started<computeFinished.get());
            result.put("sourceExclusiveLockHoldMs",sample.lockHoldNanos.stream().map(WorkforcePayrollPerformancePostgresTest::millis).toList());
            result.put("sourceExclusiveLockAcquisitionMs",sample.lockWaitNanos.stream().map(WorkforcePayrollPerformancePostgresTest::millis).toList());
            result.put("concurrentSqlExecutions",sample.sql.get());result.put("writerSucceeded",true);
            result.put("februarySnapshotStrictlyEqual",true);result.put("sourceFingerprint",oracle.sourceFingerprint());
            result.put("probeMonth","2026-03");result.put("artificialPause",false);return result;
        }finally{active.set(null);pool.shutdownNow();}
    }

    @Override Object statement(Statement delegate,String preparedSql,Class<?> type) {
        Object counted=super.statement(delegate,preparedSql,type);
        var parameters=new HashMap<Integer,Object>();
        return Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(proxy,method,args)->{
            if(method.getName().startsWith("set")&&args!=null&&args.length>1&&args[0] instanceof Integer index)parameters.put(index,args[1]);
            String sql=args!=null&&args.length>0&&args[0] instanceof String text?text:preparedSql;
            var timing=writerTiming.get();
            boolean sourceShared=timing!=null&&Set.of("execute","executeQuery","executeUpdate","executeLargeUpdate").contains(method.getName())
                &&sql.contains("pg_advisory_xact_lock_shared(")
                &&parameters.values().stream().anyMatch(p->p instanceof String text&&text.equals("workforce.payroll.source:"+tenant));
            if(!sourceShared)return invoke(counted,method,args);
            long began=System.nanoTime();Object result=invoke(counted,method,args);
            timing.sharedAcquisitions.add(System.nanoTime()-began);return result;
        });
    }

    void writeNatural(List<Object> observations,String status)throws Exception {
        var result=new LinkedHashMap<String,Object>();result.put("status",status);result.put("clock",FIXED.instant().toString());
        result.put("month",MONTH.toString());result.put("parallelism",4);result.put("workModeSetup","Production payroll resolver and seven-argument ClockService are enabled; February employees have dated field/personal assignments.");result.put("observationsPerModeAndSize",1);
        result.put("scheduling","Writer is submitted after calculator-entry notification; the calculator immediately continues without any pause, barrier, sleep, or writer-completion dependency.");
        result.put("writerLatencyDefinition","Measured inside the writer task around proxied ClockService.punch, including transaction commit and excluding task-queue time.");
        result.put("sharedLockMetricDefinition","JDBC shared source-lock SQL execution duration, including round trip; not an isolated PostgreSQL server wait measurement.");
        result.put("limitations","One scheduling observation per mode and size. No latency threshold or lock winner is asserted; these observations are not stable latency guarantees. Artificial pauses are absent.");
        result.put("observations",observations);Files.createDirectories(NATURAL_OUTPUT.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(NATURAL_OUTPUT.toFile(),result);
    }

    static class WriterTiming {
        long started,finished;
        final List<Long> sharedAcquisitions=new ArrayList<>();
    }
}
