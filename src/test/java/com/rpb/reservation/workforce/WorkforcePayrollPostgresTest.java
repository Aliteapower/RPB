package com.rpb.reservation.workforce;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.attendance.*;
import com.rpb.reservation.workforce.approval.*;
import com.rpb.reservation.workforce.leave.*;
import com.rpb.reservation.workforce.calendar.*;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import java.time.*;import java.util.*;import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class WorkforcePayrollPostgresTest extends WorkforcePostgresTest {
 PayrollConfigurationService configuration;PayrollConfigurationRepository configs;PayrollRepository payRepository;PayrollSources sources;PayrollBatchService batches;PayrollPaymentService payments;PayslipService payslips;
 SchedulingRepository schedules;AttendanceRepository attendance;ScheduleService roster;ShiftService shifts;ClockService punches;CorrectionService corrections;ApprovalService approvals;
 WorkforceAttendancePostgresTest.MutableClock time;UUID employee,employeeAccount,template,profile;CurrentActor worker;
 @BeforeEach void payrollSetup(){
  var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());var audit=new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules());time=new WorkforceAttendancePostgresTest.MutableClock();
  var statutory=new com.rpb.reservation.workforce.statutory.StatutoryPolicyRepository(jdbc,json);var policySeed=com.rpb.reservation.workforce.statutory.OfficialStatutoryTemplates.forYear(2026);statutory.save(tenant,new com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy(UUID.randomUUID(),policySeed.name(),2026,"SGD",policySeed.effectiveFrom(),policySeed.effectiveTo(),true,1,policySeed.config()));
  var pa=new PayrollAccess(access,directory,jdbc);configs=new PayrollConfigurationRepository(jdbc,json);configuration=proxy(new PayrollConfigurationService(access,pa,configs,repository,audit,time));
  schedules=new SchedulingRepository(jdbc,json);attendance=new AttendanceRepository(jdbc,json);var scope=new AttendanceAccess(access,repository,schedules);var ar=new ApprovalRepository(jdbc,json);var as=new ApprovalSources(attendance,new LeaveRepository(jdbc),new CalendarRepository(jdbc),json,time);
  shifts=proxy(new ShiftService(access,schedules,audit));roster=proxy(new ScheduleService(access,schedules,repository,audit,time));punches=proxy(new ClockService(scope,repository,schedules,attendance,audit,time));corrections=proxy(new CorrectionService(access,scope,repository,schedules,attendance,audit,time));approvals=proxy(new ApprovalService(access,scope,repository,schedules,ar,as,audit,time));
  payRepository=new PayrollRepository(jdbc,json);sources=new PayrollSources(payRepository,configs,repository,schedules,ar,as,json);
  batches=proxy(new PayrollBatchService(access,pa,payRepository,sources,new PayrollCalculationService(time),repository,audit,time,tx));payments=proxy(new PayrollPaymentService(access,pa,payRepository,batches,repository,audit,time));payslips=new PayslipService(access,pa,payRepository,repository);
  employeeAccount=account("store_staff",Set.of(store,sibling),Set.of("workforce.view"));employee=UUID.randomUUID();var seed=command(0,List.of(store,sibling),company,employeeAccount,null);employees.save(admin,store,employee,new WorkforceDtos.EmployeeWrite(0L,"PAY001",seed.name(),seed.phone(),seed.email(),seed.jobTitle(),seed.hireDate(),null,seed.status(),company,seed.companyEffectiveDate(),store,seed.storeIds(),employeeAccount,null,null));worker=new CurrentActor(tenant,employeeAccount,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store,sibling));
  template=UUID.randomUUID();profile=UUID.randomUUID();configuration.saveTemplate(admin,store,template,new TemplateWrite(0L,company,"Hourly",rule(),true));configuration.saveProfile(admin,store,profile,new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,1),template,rule(),new CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG","")));clearInvocations(logs);
 }
 Rule rule(){return new Rule("hourly","100.00","8","44",false,"1.5",List.of());}
 Schedule completed(){var shift=shifts.save(admin,store,UUID.randomUUID(),new ShiftWrite(0L,"Day","09:00","17:00",false,"none",0,0,0,0,false,true));var s=roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift.id(),LocalDate.of(2026,10,28),"scheduled"));punches.punch(worker,store,UUID.randomUUID(),new ClockWrite(s.id(),"IN"));time.at("2026-10-28T09:00:00Z");punches.punch(worker,store,UUID.randomUUID(),new ClockWrite(s.id(),"OUT"));time.at("2026-10-28T10:00:00Z");var row=approvals.detail(admin,store,s.id()).row();approvals.approve(admin,store,s.id(),new com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalWrite(0L,row.sourceFingerprint(),"Checked"));return s;}
 BatchDetail create(){return batches.create(admin,store,UUID.randomUUID(),new BatchWrite(company,"2026-10"));}
 BatchDetail action(BatchDetail b,String action){return batches.action(admin,store,b.batch().id(),UUID.randomUUID(),new ActionWrite(b.batch().version(),action,"Checked"));}
 BatchDetail published(){completed();time.at("2026-11-02T01:00:00Z");var b=action(create(),"calculate");b=action(b,"confirm");return action(b,"publish");}
 @Test void calculationConfirmationPublicationAndOwnPayslipAreSeparate(){
  completed();var b=create();assertThat(payslips.mine(worker,store,2026)).isEmpty();b=action(b,"calculate");assertThat(b.calculation().issues()).isEmpty();assertThat(b.calculation().totalGross()).isEqualTo("800.00");assertThat(b.calculation().totalEmployeeCpf()).isEqualTo("160.00");assertThat(b.calculation().totalEmployerCpf()).isEqualTo("136.00");assertThat(b.calculation().totalNet()).isEqualTo("640.00");
  var current=b;assertThatThrownBy(()->action(current,"confirm")).isInstanceOf(WorkforceException.class);time.at("2026-11-02T01:00:00Z");b=action(b,"confirm");assertThat(payslips.mine(worker,store,2026)).isEmpty();b=action(b,"publish");assertThat(payslips.mine(worker,store,2026)).hasSize(1);assertThat(payslips.mineDetail(worker,store,b.batch().id()).line().netPay()).isEqualTo("640.00");
 }
 @Test void changedAttendanceBlocksConfirmationButDoesNotRewriteConfirmedMoney(){
  var s=completed();time.at("2026-11-02T01:00:00Z");var b=action(create(),"calculate");var c=corrections.submit(worker,store,UUID.randomUUID(),new CorrectionWrite(s.id(),2L,s.startAt(),s.endAt().minusSeconds(3600),List.of(),false,"Correction"));var old=b;
  assertThatThrownBy(()->action(old,"confirm")).isInstanceOf(WorkforceException.class);corrections.decide(admin,store,c.id(),new DecisionWrite(1L,"rejected","Keep"));var refreshed=approvals.detail(admin,store,s.id()).row();approvals.approve(admin,store,s.id(),new com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalWrite(refreshed.approval().version(),refreshed.sourceFingerprint(),"Rechecked sources"));b=action(b,"calculate");b=action(b,"confirm");
  var approved=schedules.find(tenant,s.id()).orElseThrow();jdbc.update("update workforce_schedules set version=version+1 where tenant_id=? and id=?",tenant,s.id());var detail=batches.detail(admin,store,b.batch().id());assertThat(detail.sourceChanged()).isTrue();assertThat(detail.calculation().totalNet()).isEqualTo("640.00");
 }
 @Test void newSchedulesInvalidateCalculatedSourceAndUnapprovedRowsBlock(){
  completed();var b=action(create(),"calculate");var shift=shifts.save(admin,sibling,UUID.randomUUID(),new ShiftWrite(0L,"Next","09:00","17:00",false,"none",0,0,0,0,false,true));roster.save(admin,sibling,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift.id(),LocalDate.of(2026,10,29),"scheduled"));time.at("2026-11-02T01:00:00Z");var stale=b;
  assertThatThrownBy(()->action(stale,"confirm")).isInstanceOf(WorkforceException.class);assertThat(action(b,"calculate").calculation().lines().getFirst().issues()).contains("APPROVAL_MISSING");
 }
 @Test void commandReplayIsExactAndCannotChangePayload(){
  completed();var b=create();UUID id=UUID.randomUUID();var c=new ActionWrite(b.batch().version(),"calculate","Check");var first=batches.action(admin,store,b.batch().id(),id,c);assertThat(batches.action(admin,store,b.batch().id(),id,c)).isEqualTo(first);
  assertThatThrownBy(()->batches.action(admin,store,b.batch().id(),id,new ActionWrite(c.expectedVersion(),"calculate","Different"))).isInstanceOf(WorkforceException.class);
 }
 @Test void monthlyInputsMustBeExplicitAndProfileIsLockedAfterConfirmation(){
  configuration.saveProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,new Rule("monthly","2600","8","44",true,"1.5",List.of()),new CpfProfile("foreign",null,null,"GG","")));completed();time.at("2026-11-02T01:00:00Z");var b=action(create(),"calculate");assertThat(b.calculation().lines().getFirst().issues()).contains("UNITS_REQUIRED");b=batches.input(admin,store,b.batch().id(),employee,new InputWrite(0L,"26","25",true,false,List.of(),"Verified payroll days"));b=action(b,"calculate");assertThat(b.calculation().totalGross()).isEqualTo("2500.00");b=action(b,"confirm");
  assertThatThrownBy(()->configuration.saveProfile(admin,store,profile,new ProfileWrite(2L,company,employee,LocalDate.of(2026,10,1),template,rule(),new CpfProfile("foreign",null,null,"GG","")))).isInstanceOf(WorkforceException.class);
 }
 @Test void paymentsAreIdempotentPartialBoundedAndReversibleOnce(){
  var b=published();UUID id=UUID.randomUUID();var c=new PaymentWrite(employee,"200.00",LocalDate.of(2026,11,2),"bank","R1","Paid",null,"DBS");var paid=payments.register(admin,store,b.batch().id(),id,c);assertThat(payments.register(admin,store,b.batch().id(),id,c).payments()).hasSize(1);
  assertThat(payslips.mineDetail(worker,store,b.batch().id()).remainingAmount()).isEqualTo("440.00");assertThatThrownBy(()->payments.register(admin,store,b.batch().id(),UUID.randomUUID(),new PaymentWrite(employee,"500.00",LocalDate.of(2026,11,2),"bank","R2","Over",null,"DBS"))).isInstanceOf(WorkforceException.class);
  payments.register(admin,store,b.batch().id(),UUID.randomUUID(),new PaymentWrite(employee,"200.00",LocalDate.of(2026,11,2),"bank","REV","Reverse",id,"DBS"));assertThat(payslips.mineDetail(worker,store,b.batch().id()).paidAmount()).isEqualTo("0.00");
  assertThatThrownBy(()->payments.register(admin,store,b.batch().id(),UUID.randomUUID(),new PaymentWrite(employee,"200.00",LocalDate.of(2026,11,2),"bank","REV2","Again",id,"DBS"))).isInstanceOf(WorkforceException.class);
 }
 @Test void selfConfirmationAndOrdinaryManagerSalaryAccessAreForbidden(){
  completed();time.at("2026-11-02T01:00:00Z");var b=action(create(),"calculate");var salarySelf=new CurrentActor(tenant,employeeAccount,"staff",Set.of("tenant_admin"),Set.of("workforce.view","workforce.admin.manage","tenant.admin.manage"),Set.of(store,sibling));
  assertThatThrownBy(()->batches.action(salarySelf,store,b.batch().id(),UUID.randomUUID(),new ActionWrite(b.batch().version(),"confirm","Self"))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->batches.detail(worker,store,b.batch().id())).isInstanceOf(WorkforceException.class);
 }
 @Test void duplicateCompanyMonthIsRejectedAndAuditFailureRollsBack(){
  create();assertThatThrownBy(this::create).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenThrow(new IllegalStateException("audit"));UUID id=UUID.randomUUID();assertThatThrownBy(()->batches.create(admin,otherStore,id,new BatchWrite(otherCompany,"2026-10"))).isInstanceOf(IllegalStateException.class);assertThat(payRepository.find(tenant,id)).isEmpty();
 }
 @Test void confirmationWaitsForConcurrentSourceWriteAndRejectsItsOldFingerprint()throws Exception{
  var s=completed();time.at("2026-11-02T01:00:00Z");var b=action(create(),"calculate");var pool=Executors.newFixedThreadPool(2);var written=new CountDownLatch(1);var release=new CountDownLatch(1);
  try{
   var writer=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(tx).execute(status->{corrections.submit(worker,store,UUID.randomUUID(),new CorrectionWrite(s.id(),2L,s.startAt(),s.endAt().minusSeconds(3600),List.of(),false,"Concurrent source"));written.countDown();try{if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}return true;}));
   assertThat(written.await(15,TimeUnit.SECONDS)).isTrue();var confirm=pool.submit(()->{try{action(b,"confirm");return false;}catch(WorkforceException ex){return ex.code().equals("WORKFORCE_PAYROLL_SOURCE_CHANGED");}});
   String key="workforce.payroll.source:"+tenant;boolean waiting=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
   while(System.nanoTime()<deadline){Integer count=jdbc.queryForObject("select count(*) from pg_locks where locktype='advisory' and not granted and mode='ExclusiveLock' and classid::bigint=((hashtextextended(?,0)>>32)&4294967295) and objid::bigint=(hashtextextended(?,0)&4294967295)",Integer.class,key,key);if(count!=null&&count>0){waiting=true;break;}Thread.sleep(20);}
   assertThat(waiting).isTrue();release.countDown();assertThat(writer.get(20,TimeUnit.SECONDS)).isTrue();assertThat(confirm.get(20,TimeUnit.SECONDS)).isTrue();assertThat(batches.detail(admin,store,b.batch().id()).batch().status()).isEqualTo("calculated");
  }finally{release.countDown();pool.shutdownNow();}
 }
 @Test void concurrentDistinctPaymentsCannotExceedTheFrozenNet()throws Exception{
  var b=published();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try{var calls=new ArrayList<Future<Boolean>>();for(int n=0;n<2;n++)calls.add(pool.submit(()->{start.await();try{payments.register(admin,store,b.batch().id(),UUID.randomUUID(),new PaymentWrite(employee,"400.00",LocalDate.of(2026,11,2),"bank","Parallel","Paid",null,"DBS"));return true;}catch(WorkforceException ex){assertThat(ex.code()).isEqualTo("WORKFORCE_PAYROLL_OVERPAYMENT");return false;}}));start.countDown();int successes=0;for(var call:calls)if(call.get(20,TimeUnit.SECONDS))successes++;assertThat(successes).isEqualTo(1);assertThat(payslips.mineDetail(worker,store,b.batch().id()).paidAmount()).isEqualTo("400.00");}finally{pool.shutdownNow();}
 }
 @Test void anElevatedEmployeeCannotRegisterHisOwnPayment(){
  var b=published();var selfAdmin=new CurrentActor(tenant,employeeAccount,"staff",Set.of("tenant_admin"),Set.of("workforce.view","workforce.admin.manage","tenant.admin.manage"),Set.of(store,sibling));
  assertThatThrownBy(()->payments.register(selfAdmin,store,b.batch().id(),UUID.randomUUID(),new PaymentWrite(employee,"1.00",LocalDate.of(2026,11,2),"cash","","Own payment",null))).isInstanceOf(WorkforceException.class);
 }
 @Test void changedStoreRegionInvalidatesCalculatedPayroll(){
  completed();time.at("2026-11-02T01:00:00Z");var b=action(create(),"calculate");jdbc.update("update stores set currency='USD' where tenant_id=? and id=?",tenant,store);
  assertThatThrownBy(()->action(b,"confirm")).isInstanceOf(WorkforceException.class);
 }
 @Test void publishedPayslipCannotLeakToAnotherEmployeeOrStoreManager(){
  var b=published();UUID otherAccount=account("store_staff",Set.of(store),Set.of("workforce.view")),otherEmployee=UUID.randomUUID();var seed=command(0,List.of(store),company,otherAccount,null);employees.save(admin,store,otherEmployee,new WorkforceDtos.EmployeeWrite(0L,"OTHER",seed.name(),seed.phone(),seed.email(),seed.jobTitle(),seed.hireDate(),null,seed.status(),company,seed.companyEffectiveDate(),store,List.of(store),otherAccount,null,null));
  var other=new CurrentActor(tenant,otherAccount,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));assertThat(payslips.mine(other,store,2026)).isEmpty();assertThatThrownBy(()->payslips.mineDetail(other,store,b.batch().id())).isInstanceOf(WorkforceException.class);
 }
}
