package com.rpb.reservation.workforce;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import com.rpb.reservation.workforce.payroll.*;
import java.nio.file.*;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Run only #benchmarkIncrementalScales, with both payroll performance and incremental scale properties true. */
@EnabledIfSystemProperty(named="rpb.payroll.incremental.scale",matches="true")
@Tag("payroll-incremental-scale")
class WorkforcePayrollIncrementalScalePostgresTest extends WorkforcePayrollPerformancePostgresTest {
 private static final Path SCALE_OUTPUT=Path.of("target/workforce-payroll-incremental-20261030/scales.json");
 private final List<Object> scales=new ArrayList<>();

 @Test void benchmarkIncrementalScales() throws Exception {
  var originalJdbc=jdbc;var originalTx=tx;
  try(var executor=new PayrollComputeExecutor(4)) {
   var counted=counting(originalJdbc.getDataSource());jdbc=new JdbcTemplate(counted);tx=new DataSourceTransactionManager(counted);initialize();
   for(int size:List.of(100,300,500)) {
    if(size!=100)newCompany();new TransactionTemplate(tx).executeWithoutResult(status->seed(size));
    var calculator=new CountedCalculator(executor);
    var batches=new PayrollBatchService(access,payAccess,payroll,sources,calculator,repository,audit,FIXED,tx);
    var inputs=proxy(batches);var cases=new ArrayList<Object>();
    cases.add(sample("initial_full_fallback",batches,calculator,executor,size,size));
    var baseline=payroll.computation(tenant,batch).orElseThrow();
    assertThat(baseline.cache().snapshot().lines()).hasSize(size);
    var first=baseline.cache().snapshot().lines().get(0).employeeId();var second=baseline.cache().snapshot().lines().get(1).employeeId();
    cases.add(sample("unchanged",batches,calculator,executor,size,0));
    bonus(inputs,first,"75.00");cases.add(sample("one_employee",batches,calculator,executor,size,1));
    bonus(inputs,first,"150.00");bonus(inputs,second,"75.00");cases.add(sample("two_employees",batches,calculator,executor,size,2));
    jdbc.update("update operating_entities set display_name=? where tenant_id=? and id=?","Changed scale company "+size,tenant,company);
    cases.add(sample("global_company",batches,calculator,executor,size,size));
    scales.add(Map.of("employees",size,"schedules",size*20,"cases",cases));writeScales("running");
   }
   writeScales("complete");
  } finally {active.set(null);jdbc=originalJdbc;tx=originalTx;}
 }

 private void bonus(PayrollBatchService inputs,UUID employee,String amount) {
  var old=payroll.inputs(tenant,batch).stream().filter(i->i.employeeId().equals(employee)).findFirst().orElse(PayrollSources.defaultInput(employee));
  inputs.input(admin,store,batch,employee,new InputWrite(old.version(),old.workingDays(),old.payableDays(),true,true,List.of(new Item("Scale bonus","ow_additional",amount)),"Incremental scale input"));
 }
 private Map<String,Object> sample(String mode,PayrollBatchService batches,CountedCalculator calculator,PayrollComputeExecutor executor,int size,int expected) throws Exception {
  // Source capture is deliberately outside the pure engine timers, but inside both service measurements below.
  var capture=sources.capture(tenant,company,MONTH,payroll.inputs(tenant,batch));
  var previous=payroll.computation(tenant,batch).map(PayrollComputation.State::cache).orElse(null);
  var pureCalculator=new PayrollCalculationService(FIXED,executor);var engine=new PayrollIncremental(pureCalculator);
  long began=System.nanoTime();var legacy=new LegacyPayrollCalculationService(FIXED).calculate(capture,MONTH);long legacyNs=System.nanoTime()-began;
  began=System.nanoTime();var full=pureCalculator.calculate(capture,MONTH);long fullNs=System.nanoTime()-began;
  began=System.nanoTime();var pure=engine.calculate(capture,MONTH,previous,false);long incrementalNs=System.nanoTime()-began;
  assertSnapshot(full,legacy,size);assertSnapshot(pure.snapshot(),full,size);assertThat(pure.recalculated()).isEqualTo(expected);assertThat(pure.reused()).isEqualTo(size-expected);
  var incrementalMeasurement=serviceSample(batches,calculator,"calculate");
  var incremental=payroll.computation(tenant,batch).orElseThrow();
  assertThat(incremental.recalculated()).isEqualTo(expected);assertThat(incremental.reused()).isEqualTo(size-expected);
  assertThat(incrementalMeasurement.rows()).isEqualTo(expected);assertSnapshot(incremental.cache().snapshot(),full,size);
  var incrementalSnapshot=incremental.cache().snapshot();
  var fullMeasurement=serviceSample(batches,calculator,"calculate_full");var recalculated=payroll.computation(tenant,batch).orElseThrow();
  assertThat(recalculated.recalculated()).isEqualTo(size);assertThat(recalculated.reused()).isZero();assertThat(fullMeasurement.rows()).isEqualTo(size);
  assertSnapshot(recalculated.cache().snapshot(),incrementalSnapshot,size);
  var result=new LinkedHashMap<String,Object>();result.put("mode",mode);result.put("recalculated",incremental.recalculated());result.put("reused",incremental.reused());
  result.put("pureLegacyFullMs",millis(legacyNs));result.put("pureCurrentFullMs",millis(fullNs));result.put("pureIncrementalEngineMs",millis(incrementalNs));
  result.put("incrementalOverall",incrementalMeasurement.metrics());result.put("fullOverall",fullMeasurement.metrics());result.put("snapshotsStrictlyEqual",true);
  return result;
 }
 private record ServiceMeasurement(int rows,Map<String,Object> metrics){}
 private ServiceMeasurement serviceSample(PayrollBatchService batches,CountedCalculator calculator,String action) {
  var sample=new Sample(action);calculator.rows.set(0);active.set(sample);long began=System.nanoTime();
  try {batches.action(admin,store,batch,UUID.randomUUID(),new ActionWrite(payroll.find(tenant,batch).orElseThrow().version(),action,"Incremental scale calculation"));}
  finally {sample.totalNanos=System.nanoTime()-began;active.set(null);}
  assertThat(sample.sql.get()).isPositive();var metrics=new LinkedHashMap<String,Object>(sample.values());metrics.put("calculatedRows",calculator.rows.get());
  return new ServiceMeasurement(calculator.rows.get(),metrics);
 }
 private void writeScales(String status) throws Exception {
  var value=new LinkedHashMap<String,Object>();value.put("status",status);value.put("month",MONTH.toString());value.put("clock",FIXED.instant().toString());value.put("parallelism",4);
  value.put("environment","Local PostgreSQL from target/local-postgres-current.txt; isolated inherited UUID schema; dated work-mode and 20 approved real schedule/attendance sources per employee.");
  value.put("measurementLimit","Single local observations, no speed guarantee or production/load claim. Every service calculation still captures all database sources. Pure engine times exclude capture; rowCalculationMs (pureCalculationMs) excludes dependency hashing/aggregation. Service overall includes captures, transactions, cache persistence and row calculation. Pure timers always run legacy/full/incremental in that order, so JIT/cache effects may favor later samples.");
  value.put("results",scales);Files.createDirectories(SCALE_OUTPUT.getParent());mapper.writerWithDefaultPrettyPrinter().writeValue(SCALE_OUTPUT.toFile(),value);
 }
 private class CountedCalculator extends PayrollCalculationService {
  private final AtomicInteger rows=new AtomicInteger();
  CountedCalculator(PayrollComputeExecutor executor){super(FIXED,executor);}
  @Override public PayrollSnapshot calculate(PayrollSources.Capture capture,YearMonth month) {
   rows.addAndGet(capture.lines().size());long began=System.nanoTime();
   try{return super.calculate(capture,month);}finally{var sample=active.get();if(sample!=null)sample.computeNanos.addAndGet(System.nanoTime()-began);}
  }
 }
}
