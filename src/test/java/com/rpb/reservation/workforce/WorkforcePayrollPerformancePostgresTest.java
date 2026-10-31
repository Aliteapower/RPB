package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.approval.*;
import com.rpb.reservation.workforce.attendance.*;
import com.rpb.reservation.workforce.calendar.CalendarRepository;
import com.rpb.reservation.workforce.leave.LeaveRepository;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.persistence.*;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.statutory.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import static org.assertj.core.api.Assertions.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.*;

/** Opt-in PostgreSQL benchmark. All writes use the inherited UUID schema and are removed by its cleanup. */
@EnabledIfSystemProperty(named="rpb.payroll.performance", matches="true")
@Tag("payroll-performance")
class WorkforcePayrollPerformancePostgresTest extends WorkforcePostgresTest {
    static final YearMonth MONTH=YearMonth.of(2026,2);
    static final Clock FIXED=Clock.fixed(Instant.parse("2026-03-02T01:00:00Z"),ZoneOffset.UTC);
    static final Path OUTPUT=Path.of("target/workforce-payroll-perf-20261030/metrics.json");
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final List<Object> results=new ArrayList<>();
    final AtomicReference<Sample> active=new AtomicReference<>();
    WorkforceJson json;
    PayrollRepository payroll;
    PayrollConfigurationRepository configs;
    SchedulingRepository scheduling;
    AttendanceRepository attendance;
    ApprovalSources approvalSources;
    PayrollSources sources;
    LegacyPayrollSources legacySources;
    PayrollAccess payAccess;
    WorkforceAudit audit;
    com.rpb.reservation.workforce.workmode.WorkModeClock workModeClock;
    UUID batch;

    @Test void benchmarkOldAndNewWithIdenticalApprovedSources() throws Exception {
        var originalJdbc=jdbc;var originalTx=tx;
        try(var executor=new PayrollComputeExecutor(4)) {
            DataSource counted=counting(originalJdbc.getDataSource());
            jdbc=new JdbcTemplate(counted);tx=new DataSourceTransactionManager(counted);
            initialize();
            for(int size:List.of(100,300,500)) {
                // Separate companies avoid source changes between samples while retaining the same tenant-wide lock.
                if(size!=100)newCompany();
                new TransactionTemplate(tx).executeWithoutResult(status->seed(size));
                var oldCalc=new TimedLegacyCalculator();var newCalc=new TimedCalculator(executor);
                var oldBatch=proxy(new LegacyPayrollBatchService(access,payAccess,payroll,legacySources,oldCalc,repository,audit,FIXED));
                var newBatch=new PayrollBatchService(access,payAccess,payroll,sources,newCalc,repository,audit,FIXED,tx);
                PayrollSnapshot oracle=runOld(oldBatch).calculation();
                assertSnapshot(runNew(newBatch).calculation(),oracle,size);
                var oldSamples=new ArrayList<Sample>();var newSamples=new ArrayList<Sample>();
                for(int repeat=0;repeat<3;repeat++) {
                    // Alternate order to limit a systematic cache/JIT advantage.
                    if(repeat%2==0){oldSamples.add(measure("legacy",()->runOld(oldBatch),oracle,size));newSamples.add(measure("optimized",()->runNew(newBatch),oracle,size));}
                    else {newSamples.add(measure("optimized",()->runNew(newBatch),oracle,size));oldSamples.add(measure("legacy",()->runOld(oldBatch),oracle,size));}
                }
                var row=new LinkedHashMap<String,Object>();row.put("employees",size);row.put("schedules",size*20);
                row.put("legacy",summary(oldSamples));row.put("optimized",summary(newSamples));
                row.put("financialResultsEqual",true);row.put("evidenceSemantics","Governance evidence intentionally changes source fingerprints and line metadata.");row.put("sourceFingerprint",oracle.sourceFingerprint());
                results.add(row);writeMetrics(Map.of("status","performance-measured"));
                // The probe is an unrelated March schedule. It must not change February source evidence.
                var probes=new ArrayList<Object>();
                probes.add(probe(true,executor,oracle,size));probes.add(probe(false,executor,oracle,size));
                row.put("pausedCalculationWriterProbes",probes);writeMetrics(Map.of("status","running"));
            }
            writeMetrics(Map.of("status","complete"));
        } finally {active.set(null);jdbc=originalJdbc;tx=originalTx;}
    }

    void initialize() {
        json=new WorkforceJson(mapper);audit=new WorkforceAudit(logs,mapper);
        directory=new WorkforceDirectory(jdbc);repository=new WorkforceEmployeeRepository(jdbc);access=new WorkforceAccess(gate,directory);
        payroll=new PayrollRepository(jdbc,json);configs=new PayrollConfigurationRepository(jdbc,json);
        scheduling=new SchedulingRepository(jdbc,json);attendance=new AttendanceRepository(jdbc,json);
        var approvals=new ApprovalRepository(jdbc,json);
        var workModeRepository=new com.rpb.reservation.workforce.workmode.WorkModeRepository(jdbc,json);
        var workModeResolver=new com.rpb.reservation.workforce.workmode.WorkModeResolver(workModeRepository);
        workModeClock=new com.rpb.reservation.workforce.workmode.WorkModeClock(workModeRepository,workModeResolver,FIXED);
        approvalSources=new ApprovalSources(attendance,new LeaveRepository(jdbc),new CalendarRepository(jdbc),json,FIXED,workModeClock);
        sources=new PayrollSources(payroll,configs,repository,scheduling,approvals,approvalSources,json,workModeResolver);
        legacySources=new LegacyPayrollSources(payroll,configs,repository,scheduling,approvals,approvalSources,json,workModeResolver);
        payAccess=new PayrollAccess(access,directory,jdbc);
        var seed=OfficialStatutoryTemplates.forYear(2026);
        new StatutoryPolicyRepository(jdbc,json).save(tenant,new StatutoryPolicies.Policy(UUID.randomUUID(),seed.name(),2026,"SGD",seed.effectiveFrom(),seed.effectiveTo(),true,1,seed.config()));
    }

    void newCompany() {
        company=UUID.randomUUID();store=UUID.randomUUID();
        jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,?,'Benchmark Company','active')",company,tenant,"perf-"+company);
        jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,?,'Benchmark Store','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,company,"perf-"+store);
        jdbc.update("insert into auth_account_store_access(account_id,tenant_id,store_id) values(?,?,?)",adminId,tenant,store);
        var stores=new HashSet<>(admin.storeIds());stores.add(store);
        admin=new CurrentActor(tenant,adminId,"staff",admin.roles(),admin.permissions(),Set.copyOf(stores));
    }

    void seed(int size) {
        // Batch/set-based seeding is deliberately outside every timed interval.
        var ids=new ArrayList<UUID>();for(int i=0;i<size;i++)ids.add(UUID.randomUUID());
        jdbc.batchUpdate("insert into workforce_employees(id,tenant_id,employee_no,name,hire_date,status,company_id,company_effective_date,main_store_id,version) values(?,?,?,?,'2026-01-01','active',?,'2026-01-01',?,1)",
            ids,500,(p,id)->{p.setObject(1,id);p.setObject(2,tenant);p.setString(3,"PERF-"+size+"-"+ids.indexOf(id));p.setString(4,"Employee "+ids.indexOf(id));p.setObject(5,company);p.setObject(6,store);});
        jdbc.update("insert into workforce_company_history(employee_id,tenant_id,company_id,effective_date,version) select id,tenant_id,company_id,'2026-01-01',1 from workforce_employees where tenant_id=? and company_id=?",tenant,company);
        jdbc.update("insert into workforce_work_stores(employee_id,tenant_id,store_id,started_on) select id,tenant_id,main_store_id,'2026-01-01' from workforce_employees where tenant_id=? and company_id=?",tenant,company);
        jdbc.update("""
            insert into workforce_work_modes(id,tenant_id,employee_id,effective_from,mode,origin,position_id,position_version,version)
            select gen_random_uuid(),tenant_id,id,date '2026-01-01','field','personal',null,0,1
            from workforce_employees where tenant_id=? and company_id=?
            """,tenant,company);
        var shift=new Shift(UUID.randomUUID(),"Day","09:00","17:00",false,"none",0,0,0,0,false,true,1);
        scheduling.saveShift(tenant,store,shift);
        var profiles=new ArrayList<PayProfile>();
        for(int i=0;i<ids.size();i++) {
            String basis=List.of("hourly","monthly","daily").get(i%3);
            var rule=new Rule(basis,basis.equals("monthly")?"3200.00":basis.equals("daily")?"160.00":"20.00","8","44",true,"1.5",List.of(new Item("Transport","ow_allowance","30.00")),new Generation("attendance",List.of(1,2,3,4,5)));
            var template=new SalaryTemplate(UUID.randomUUID(),company,"Rule "+i,rule,true,1);jdbc.update("insert into workforce_pay_templates(id,tenant_id,company_id,name,snapshot,enabled,version) values(?,?,?,?,?::jsonb,true,1)",template.id(),tenant,company,template.name(),json.write(template));
            profiles.add(new PayProfile(UUID.randomUUID(),company,ids.get(i),"Employee "+i,LocalDate.of(2026,1,1),template.id(),1,rule,new StatutoryDtos.CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG",""),1));
        }
        jdbc.batchUpdate("insert into workforce_pay_profiles(id,tenant_id,company_id,employee_id,effective_from,template_id,template_version,snapshot,version) values(?,?,?,?,'2026-01-01',?,1,?::jsonb,1)",profiles,500,(p,profile)->{p.setObject(1,profile.id());p.setObject(2,tenant);p.setObject(3,company);p.setObject(4,profile.employeeId());p.setObject(5,profile.templateId());p.setString(6,json.write(profile));});
        // Explicit installation compatibility seeds, outside timed intervals.
        jdbc.update("insert into workforce_pay_effective_evidence(id,tenant_id,company_id,kind,subject_kind,subject_id,version,result) select gen_random_uuid(),tenant_id,company_id,'legacy-valid','template',id,version,snapshot from workforce_pay_templates where tenant_id=? and company_id=?",tenant,company);
        jdbc.update("insert into workforce_pay_effective_evidence(id,tenant_id,company_id,kind,subject_kind,subject_id,version,result) select gen_random_uuid(),tenant_id,company_id,'legacy-valid','profile',id,version,snapshot from workforce_pay_profiles where tenant_id=? and company_id=?",tenant,company);
        jdbc.update("""
            insert into workforce_schedules(id,tenant_id,store_id,employee_id,company_id,shift_id,work_date,start_at,end_at,timezone,shift_snapshot,status,version)
            select gen_random_uuid(),e.tenant_id,e.main_store_id,e.id,e.company_id,?,d::date,
             (d::date+time '09:00') at time zone 'Asia/Singapore',(d::date+time '17:00') at time zone 'Asia/Singapore',
             'Asia/Singapore',?::jsonb,'scheduled',1
            from workforce_employees e cross join generate_series(date '2026-02-01',date '2026-02-28',interval '1 day') d
            where e.tenant_id=? and e.company_id=? and extract(isodow from d)<6
            """,shift.id(),json.write(shift),tenant,company);
        jdbc.update("insert into workforce_attendance_sessions(id,tenant_id,store_id,employee_id,schedule_id,in_at,out_at,version) select gen_random_uuid(),tenant_id,store_id,employee_id,id,start_at,end_at,2 from workforce_schedules where tenant_id=? and company_id=? and work_date between '2026-02-01' and '2026-02-28'",tenant,company);
        var plans=scheduling.scoped(tenant,payroll.scheduleIds(tenant,company,MONTH.atDay(1),MONTH.atEndOfMonth())).stream().map(SchedulingRepository.ScopedSchedule::schedule).toList();
        assertThat(plans).hasSize(size*20);
        var evidence=approvalSources.readAll(tenant,store,plans);
        var approvalArgs=new ArrayList<Object[]>();
        for(var plan:plans){var source=evidence.get(plan.id());var row=approvalSources.row(store,source,null);assertThat(row.issues()).isEmpty();assertThat(row.workSeconds()).isEqualTo(28800);approvalArgs.add(new Object[]{tenant,store,plan.id(),plan.employeeId(),row.sourceFingerprint(),row.workSeconds(),row.paidLeaveSeconds(),row.unpaidLeaveSeconds(),json.write(source),adminId,Timestamp.from(FIXED.instant())});}
        jdbc.batchUpdate("insert into workforce_approvals(tenant_id,store_id,schedule_id,employee_id,version,source_fingerprint,work_seconds,paid_leave_seconds,unpaid_leave_seconds,snapshot,reviewer_id,note,approved_at) values(?,?,?,?,1,?,?,?,?,?::jsonb,?,'Benchmark real source',?)",approvalArgs);
        batch=UUID.randomUUID();new TransactionTemplate(tx).executeWithoutResult(status->payroll.create(tenant,company,batch,"Company",MONTH,adminId,FIXED.instant()));
    }

    BatchDetail runOld(LegacyPayrollBatchService service){return service.action(admin,store,batch,UUID.randomUUID(),nextAction());}
    BatchDetail runNew(PayrollBatchService service){return service.action(admin,store,batch,UUID.randomUUID(),nextAction());}
    ActionWrite nextAction(){return new ActionWrite(payroll.find(tenant,batch).orElseThrow().version(),"calculate","Benchmark");}
    void assertSnapshot(PayrollSnapshot value,PayrollSnapshot oracle,int size){assertThat(List.of(value.totalGross(),value.totalNet(),value.totalEmployeeCpf(),value.totalEmployerCpf(),value.totalSdl(),value.totalEmployerCost())).isEqualTo(List.of(oracle.totalGross(),oracle.totalNet(),oracle.totalEmployeeCpf(),oracle.totalEmployerCpf(),oracle.totalSdl(),oracle.totalEmployerCost()));for(int n=0;n<value.lines().size();n++){var a=value.lines().get(n);var b=oracle.lines().get(n);assertThat(List.of(a.basicPay(),a.overtimePay(),a.allowances(),a.reimbursements(),a.deductions(),a.grossPay(),a.employeeCpf(),a.employerCpf(),a.sdl(),a.netPay())).isEqualTo(List.of(b.basicPay(),b.overtimePay(),b.allowances(),b.reimbursements(),b.deductions(),b.grossPay(),b.employeeCpf(),b.employerCpf(),b.sdl(),b.netPay()));}assertThat(value.lines()).hasSize(size);assertThat(value.issues()).isEmpty();assertThat(value.lines()).allSatisfy(line->{assertThat(line.sources()).hasSize(20);assertThat(line.workSeconds()).isEqualTo(576000);assertThat(line.issues()).isEmpty();});}

    Sample measure(String mode,Callable<BatchDetail> run,PayrollSnapshot oracle,int size) throws Exception {
        var sample=new Sample(mode);active.set(sample);long began=System.nanoTime();
        BatchDetail result;
        try{result=run.call();}finally{sample.totalNanos=System.nanoTime()-began;active.set(null);}
        assertSnapshot(result.calculation(),oracle,size);assertThat(sample.sql.get()).isPositive();assertThat(sample.computeNanos.get()).isPositive();assertThat(sample.lockHoldNanos).isNotEmpty();return sample;
    }

    Map<String,Object> summary(List<Sample> samples){var result=new LinkedHashMap<String,Object>();result.put("samples",samples.stream().map(Sample::values).toList());result.put("medianTotalMs",median(samples.stream().map(s->millis(s.totalNanos)).toList()));result.put("medianSqlExecutions",median(samples.stream().map(s->(double)s.sql.get()).toList()));result.put("medianPureCalculationMs",median(samples.stream().map(s->millis(s.computeNanos.get())).toList()));result.put("medianSourceExclusiveLockHoldMs",median(samples.stream().map(s->millis(s.lockHoldNanos.stream().mapToLong(Long::longValue).sum())).toList()));result.put("medianSourceExclusiveLockWaitMs",median(samples.stream().map(s->millis(s.lockWaitNanos.stream().mapToLong(Long::longValue).sum())).toList()));return result;}
    static double median(List<Double> xs){return xs.stream().sorted().toList().get(xs.size()/2);}
    static double millis(long ns){return ns/1_000_000.0;}
    void writeMetrics(Map<String,Object> state)throws Exception{var value=new LinkedHashMap<String,Object>();value.putAll(state);value.put("clock",FIXED.instant().toString());value.put("month",MONTH.toString());value.put("parallelism",4);value.put("workModeSetup","Production resolver and clock enabled; February employees have dated field/personal assignments; legacy session policies remain null.");value.put("warmupsPerImplementation",1);value.put("repetitionsPerImplementation",3);value.put("transactionFairness","Legacy is @Transactional proxied; optimized owns explicit transaction boundaries; both share one counted DataSource and PostgreSQL schema. No outer transaction around optimized calculation.");value.put("lockMetric","Exclusive source advisory SQL return to transaction afterCompletion; acquisition SQL duration reported separately as wait including round trip.");value.put("pausedProbeWarning","Artificial calculation pause is only a concurrency assertion; it is excluded from all natural performance samples.");value.put("results",results);Files.createDirectories(OUTPUT.getParent());mapper.writerWithDefaultPrettyPrinter().writeValue(OUTPUT.toFile(),value);}

    class TimedCalculator extends PayrollCalculationService {
        TimedCalculator(PayrollComputeExecutor executor){super(FIXED,executor);}
        @Override public PayrollSnapshot calculate(PayrollSources.Capture capture,YearMonth month){long start=System.nanoTime();try{return super.calculate(capture,month);}finally{var s=active.get();if(s!=null)s.computeNanos.addAndGet(System.nanoTime()-start);}}
    }
    class TimedLegacyCalculator extends LegacyPayrollCalculationService {
        TimedLegacyCalculator(){super(FIXED);}
        @Override public PayrollSnapshot calculate(PayrollSources.Capture capture,YearMonth month){long start=System.nanoTime();try{return super.calculate(capture,month);}finally{var s=active.get();if(s!=null)s.computeNanos.addAndGet(System.nanoTime()-start);}}
    }

    static class Sample {
        final String mode;final AtomicLong sql=new AtomicLong(),computeNanos=new AtomicLong();long totalNanos;
        final List<Long> lockHoldNanos=new CopyOnWriteArrayList<>(),lockWaitNanos=new CopyOnWriteArrayList<>();
        Sample(String mode){this.mode=mode;}
        Map<String,Object> values(){return Map.of("mode",mode,"totalMs",millis(totalNanos),"sqlExecutions",sql.get(),"pureCalculationMs",millis(computeNanos.get()),"sourceExclusiveLockHoldMs",lockHoldNanos.stream().map(WorkforcePayrollPerformancePostgresTest::millis).toList(),"sourceExclusiveLockWaitMs",lockWaitNanos.stream().map(WorkforcePayrollPerformancePostgresTest::millis).toList());}
    }

    DataSource counting(DataSource delegate){return new AbstractDataSource(){@Override public Connection getConnection()throws SQLException{return connection(delegate.getConnection());}@Override public Connection getConnection(String user,String password)throws SQLException{return connection(delegate.getConnection(user,password));}};}
    Connection connection(Connection delegate){return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
        Object value=invoke(delegate,method,args);
        if(value instanceof Statement statement){String sql=args!=null&&args.length>0&&args[0] instanceof String text?text:"";Class<?> type=value instanceof CallableStatement?CallableStatement.class:value instanceof PreparedStatement?PreparedStatement.class:Statement.class;return statement(statement,sql,type);}
        return value;
    });}
    Object statement(Statement delegate,String preparedSql,Class<?> type){var parameters=new HashMap<Integer,Object>();return Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(proxy,method,args)->{
        if(method.getName().startsWith("set")&&args!=null&&args.length>1&&args[0] instanceof Integer index)parameters.put(index,args[1]);
        boolean executes=Set.of("execute","executeQuery","executeUpdate","executeLargeUpdate","executeBatch","executeLargeBatch").contains(method.getName());
        if(!executes)return invoke(delegate,method,args);
        var sample=active.get();if(sample!=null)sample.sql.incrementAndGet();
        String sql=args!=null&&args.length>0&&args[0] instanceof String text?text:preparedSql;
        boolean sourceLock=sql.contains("pg_advisory_xact_lock(")&&parameters.values().stream().anyMatch(p->p instanceof String text&&text.startsWith("workforce.payroll.source:"));
        long began=System.nanoTime();Object value=invoke(delegate,method,args);
        if(sourceLock&&sample!=null){long acquired=System.nanoTime();sample.lockWaitNanos.add(acquired-began);assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){sample.lockHoldNanos.add(System.nanoTime()-acquired);}});}
        return value;
    });}
    static Object invoke(Object target,Method method,Object[] args)throws Throwable{try{return method.invoke(target,args);}catch(InvocationTargetException ex){throw ex.getCause();}}

    Map<String,Object> probe(boolean legacy,PayrollComputeExecutor executor,PayrollSnapshot oracle,int size)throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var workerAccount=account("store_staff",Set.of(store),Set.of("workforce.view"));
        // A distinct employee with only a March schedule cannot enter the February employee/source set.
        UUID probeEmployee=UUID.randomUUID();
        jdbc.update("insert into workforce_employees(id,tenant_id,employee_no,name,hire_date,status,company_id,company_effective_date,main_store_id,account_id,version) values(?,?,?,'March probe','2026-03-01','active',?,'2026-03-01',?,?,1)",probeEmployee,tenant,"PROBE-"+probeEmployee.toString().substring(0,20),company,store,workerAccount);
        jdbc.update("insert into workforce_company_history(employee_id,tenant_id,company_id,effective_date,version) values(?,?,?,'2026-03-01',1)",probeEmployee,tenant,company);
        jdbc.update("insert into workforce_work_stores(employee_id,tenant_id,store_id,started_on) values(?,?,?,'2026-03-01')",probeEmployee,tenant,store);
        var shift=scheduling.shifts(tenant,store).getFirst();UUID schedule=UUID.randomUUID();
        scheduling.save(tenant,store,new Schedule(schedule,probeEmployee,"March probe","PROBE",shift.id(),LocalDate.of(2026,3,2),FIXED.instant(),FIXED.instant().plusSeconds(28800),"Asia/Singapore","scheduled",shift,company,1));
        var worker=new CurrentActor(tenant,workerAccount,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));
        var punches=proxy(new ClockService(new AttendanceAccess(access,repository,scheduling),repository,scheduling,attendance,audit,FIXED,workModeClock));
        PayrollCalculationService calc=legacy?new LegacyPayrollCalculationService(FIXED){@Override public PayrollSnapshot calculate(PayrollSources.Capture c,YearMonth m){pause();return super.calculate(c,m);}void pause(){pauseCalculation(entered,release);}}:new PayrollCalculationService(FIXED,executor){@Override public PayrollSnapshot calculate(PayrollSources.Capture c,YearMonth m){pauseCalculation(entered,release);return super.calculate(c,m);}};
        var pool=Executors.newFixedThreadPool(2);var outcome=new LinkedHashMap<String,Object>();
        try{
            Callable<BatchDetail> work=legacy?()->runOld(proxy(new LegacyPayrollBatchService(access,payAccess,payroll,legacySources,calc,repository,audit,FIXED))):()->runNew(new PayrollBatchService(access,payAccess,payroll,sources,calc,repository,audit,FIXED,tx));
            var calculation=pool.submit(work);assertThat(entered.await(90,TimeUnit.SECONDS)).isTrue();
            long began=System.nanoTime();var writer=pool.submit(()->punches.punch(worker,store,UUID.randomUUID(),new ClockWrite(schedule,"IN")));
            boolean waiting=awaitSourceWriter(tenant,writer);
            if(legacy){assertThat(waiting).isTrue();assertThat(writer.isDone()).isFalse();}
            else {assertThat(writer.get(10,TimeUnit.SECONDS).scheduleId()).isEqualTo(schedule);assertThat(waiting).isFalse();}
            long observed=System.nanoTime()-began;release.countDown();writer.get(20,TimeUnit.SECONDS);
            assertSnapshot(calculation.get(90,TimeUnit.SECONDS).calculation(),oracle,size);
            outcome.put("mode",legacy?"legacy":"optimized");outcome.put("writerBlockedOnSourceLock",waiting);outcome.put("writerCompletedBeforeCalculationRelease",!legacy);outcome.put("observationMsArtificialPause",millis(observed));outcome.put("probeMonth","2026-03");outcome.put("februarySnapshotStrictlyEqual",true);return outcome;
        }finally{release.countDown();pool.shutdownNow();}
    }
    static void pauseCalculation(CountDownLatch entered,CountDownLatch release){entered.countDown();try{if(!release.await(30,TimeUnit.SECONDS))throw new IllegalStateException("Benchmark calculation pause timed out");}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}}
    boolean awaitSourceWriter(UUID tenant,Future<?> writer)throws Exception{
        String key="workforce.payroll.source:"+tenant;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline){int waiting=jdbc.queryForObject("select count(*) from pg_locks where locktype='advisory' and not granted and classid::bigint=((hashtextextended(?,0)>>32)&4294967295) and objid::bigint=(hashtextextended(?,0)&4294967295)",Integer.class,key,key);if(waiting>0)return true;if(writer.isDone())return false;Thread.sleep(20);}return false;
    }
}
