package com.rpb.reservation.workforce;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.bulk.*;
import com.rpb.reservation.workforce.scheduling.copy.*;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import static com.rpb.reservation.workforce.scheduling.copy.WeekCopyDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
class WorkforceWeekCopyPostgresTest extends WorkforcePostgresTest {
 SchedulingRepository scheduling;BulkScheduleRepository bulk;ScheduleService schedules;WeekCopyPlanService plans;WeekCopyService copies;UUID employee,shift;List<UUID> sourceIds;
 @BeforeEach void copySetup(TestInfo info){
  if(info.getTestMethod().orElseThrow().getDeclaringClass()!=WorkforceWeekCopyPostgresTest.class)return;
  var mapper=new ObjectMapper().findAndRegisterModules();var json=new WorkforceJson(mapper);var audit=new WorkforceAudit(logs,mapper);
  scheduling=new SchedulingRepository(jdbc,json);bulk=new BulkScheduleRepository(jdbc,json);schedules=proxy(new ScheduleService(access,scheduling,repository,audit,clock));plans=proxy(new WeekCopyPlanService(access,scheduling,bulk,json,clock));copies=proxy(new WeekCopyService(access,scheduling,bulk,repository,plans,schedules,audit,json));
  employee=UUID.randomUUID();employees.save(admin,store,employee,command(0,List.of(store,sibling),company,null,null));shift=UUID.randomUUID();proxy(new ShiftService(access,scheduling,audit)).save(admin,store,shift,new ShiftWrite(0L,"Early","09:00","17:00",false,"none",0,0,0,0,false,true));sourceIds=new ArrayList<>();for(int day:List.of(29,30)){UUID id=UUID.randomUUID();sourceIds.add(id);schedules.save(admin,store,id,new ScheduleWrite(0L,employee,shift,LocalDate.of(2026,10,day),"scheduled"));}
 }
 Request request(){return new Request(LocalDate.of(2026,10,26),LocalDate.of(2026,11,2));}
 @Test void previewsSkipExistingAndDoNotMutateAndCommitIsActorBoundAndIdempotent(){
  schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift,LocalDate.of(2026,11,5),"scheduled"));
  var before=jdbc.queryForList("select to_jsonb(s)::text from workforce_schedules s where tenant_id=? order by id",String.class,tenant);var p=plans.preview(admin,store,request());assertThat(p.total()).isEqualTo(2);assertThat(p.existing()).isEqualTo(1);assertThat(p.ready()).isEqualTo(1);assertThat(jdbc.queryForList("select to_jsonb(s)::text from workforce_schedules s where tenant_id=? order by id",String.class,tenant)).isEqualTo(before);
  var id=UUID.randomUUID();var c=new Commit(request(),p.fingerprint());var result=copies.commit(admin,store,id,c);assertThat(result.created()).isEqualTo(1);assertThat(copies.commit(admin,store,id,c)).isEqualTo(result);assertThat(jdbc.queryForObject("select count(*) from workforce_schedules where tenant_id=?",Long.class,tenant)).isEqualTo(4);
  assertThatThrownBy(()->copies.commit(admin,store,id,new Commit(request(),"0".repeat(64)))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(409);
  for(UUID source:sourceIds)assertThat(scheduling.find(tenant,source).orElseThrow().version()).isEqualTo(1);
 }
 @Test void sourceChangesRequireNewPreviewAndAuditFailureRollsBackEverything(){
  var old=plans.preview(admin,store,request());schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift,LocalDate.of(2026,10,31),"scheduled"));
  assertThatThrownBy(()->copies.commit(admin,store,UUID.randomUUID(),new Commit(request(),old.fingerprint()))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_COPY_CHANGED");
  var fresh=plans.preview(admin,store,request());doThrow(new IllegalStateException("audit unavailable")).when(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
  assertThatThrownBy(()->copies.commit(admin,store,UUID.randomUUID(),new Commit(request(),fresh.fingerprint()))).isInstanceOf(IllegalStateException.class);assertThat(jdbc.queryForObject("select count(*) from workforce_schedules where tenant_id=? and work_date>='2026-11-02'",Long.class,tenant)).isZero();assertThat(jdbc.queryForObject("select count(*) from workforce_bulk_schedule_commands where tenant_id=?",Long.class,tenant)).isZero();
 }
 @Test void cancelledTargetDoesNotBlockAndCrossStoreOverlapIsExplained(){
  UUID cancelled=UUID.randomUUID();schedules.save(admin,store,cancelled,new ScheduleWrite(0L,employee,shift,LocalDate.of(2026,11,6),"scheduled"));schedules.save(admin,store,cancelled,new ScheduleWrite(1L,employee,shift,LocalDate.of(2026,11,6),"cancelled"));
  UUID other=UUID.randomUUID();proxy(new ShiftService(access,scheduling,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules()))).save(admin,sibling,other,new ShiftWrite(0L,"Other","10:00","18:00",false,"none",0,0,0,0,false,true));schedules.save(admin,sibling,UUID.randomUUID(),new ScheduleWrite(0L,employee,other,LocalDate.of(2026,11,5),"scheduled"));
  var p=plans.preview(admin,store,request());assertThat(p.ready()).isEqualTo(1);assertThat(p.conflicts()).isEqualTo(1);assertThat(p.items().getFirst().reason()).isEqualTo("TIME_OVERLAP");var result=copies.commit(admin,store,UUID.randomUUID(),new Commit(request(),p.fingerprint()));assertThat(result.created()).isEqualTo(1);assertThat(scheduling.find(tenant,cancelled).orElseThrow().status()).isEqualTo("cancelled");
 }

 UUID cloneEmployee(String number){var id=UUID.randomUUID();jdbc.update("insert into workforce_employees(id,tenant_id,employee_no,name,hire_date,status,company_id,company_effective_date,main_store_id,version,position_id) select ?,tenant_id,?,name,hire_date,status,company_id,company_effective_date,main_store_id,1,position_id from workforce_employees where id=?",id,number,employee);jdbc.update("insert into workforce_work_stores(employee_id,tenant_id,store_id,started_on) values(?,?,?,'2026-10-01')",id,tenant,store);return id;}
 void rawSource(UUID id,LocalDate date){jdbc.update("insert into workforce_schedules(id,tenant_id,store_id,employee_id,company_id,shift_id,work_date,start_at,end_at,timezone,shift_snapshot,status,version) select gen_random_uuid(),tenant_id,store_id,?,company_id,shift_id,?,?::date+time '09:00' at time zone timezone,?::date+time '17:00' at time zone timezone,timezone,shift_snapshot,'scheduled',1 from workforce_schedules where id=?",id,date,date,date,sourceIds.getFirst());}
 @Test void hundredsOfEmployeesCopyMoreThanOneHundredWithoutTruncation(){
  new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(status->{for(int i=0;i<699;i++){var id=cloneEmployee("B"+i);for(int day=0;day<7;day++)rawSource(id,request().sourceWeek().plusDays(day));}});
  var p=plans.preview(admin,store,request());assertThat(p.employeeCount()).isEqualTo(700);assertThat(p.total()).isEqualTo(4895);assertThat(p.ready()).isEqualTo(4895);
  var command=UUID.randomUUID();var input=new Commit(request(),p.fingerprint());var result=copies.commit(admin,store,command,input);assertThat(result.created()).isEqualTo(4895);assertThat(copies.commit(admin,store,command,input)).isEqualTo(result);assertThat(jdbc.queryForObject("select count(*) from workforce_schedules where tenant_id=? and work_date between '2026-11-02' and '2026-11-08'",Long.class,tenant)).isEqualTo(4895);
  new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(status->{for(int i=0;i<106;i++)rawSource(employee,request().sourceWeek());});assertThatThrownBy(()->plans.preview(admin,store,request())).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_COPY_TOO_LARGE");
 }
 @Test void permissionsDisabledTemplatesAndEmploymentChangesRemainEnforced(){
  var worker=new com.rpb.reservation.walkin.api.CurrentActor(tenant,adminId,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));assertThatThrownBy(()->plans.preview(worker,store,request())).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);assertThatThrownBy(()->copies.commit(worker,store,UUID.randomUUID(),new Commit(request(),"0".repeat(64)))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
  assertThatThrownBy(()->plans.preview(admin,store,new Request(request().sourceWeek(),request().sourceWeek()))).isInstanceOf(WorkforceException.class);
  jdbc.update("update workforce_shifts set snapshot=jsonb_set(snapshot,'{enabled}','false'),enabled=false where id=?",shift);assertThat(plans.preview(admin,store,request()).items()).allMatch(row->row.reason().equals("SHIFT_UNAVAILABLE"));jdbc.update("update workforce_work_stores set ended_on='2026-11-01' where employee_id=?",employee);assertThat(plans.preview(admin,store,request()).items()).allMatch(row->row.reason().equals("NOT_ASSIGNED"));
  doAnswer(call->{var r=(com.rpb.reservation.appgate.application.AppGateAccessRequest)call.getArgument(0);return com.rpb.reservation.appgate.domain.AppGateDecision.deny(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission(),com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED);}).when(gate).evaluate(any());assertThatThrownBy(()->plans.preview(admin,store,request())).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
 }
 @Test void concurrentSameCommandReplaysAndNewSourceEmployeeWaitsForWholeWeekLock()throws Exception{
  var input=new Commit(request(),plans.preview(admin,store,request()).fingerprint());var command=UUID.randomUUID();var other=cloneEmployee("B999");var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var once=new java.util.concurrent.atomic.AtomicBoolean();
  doAnswer(call->{if(once.compareAndSet(false,true)){entered.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("latch timeout");}return null;}).when(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(3)){
   var first=pool.submit(()->copies.commit(admin,store,command,input));assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();var replay=pool.submit(()->copies.commit(admin,store,command,input));var addition=pool.submit(()->schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,other,shift,LocalDate.of(2026,10,31),"scheduled")));
   try{assertThatThrownBy(()->addition.get(250,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);}finally{release.countDown();}assertThat(first.get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(replay.get(15,java.util.concurrent.TimeUnit.SECONDS));assertThat(addition.get(15,java.util.concurrent.TimeUnit.SECONDS).workDate()).isEqualTo(LocalDate.of(2026,10,31));
  }finally{release.countDown();}
 }

 @Test void changedTemplatesCannotCreateOverlappingCandidates(){
  var shifts=proxy(new ShiftService(access,scheduling,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules())));var second=UUID.randomUUID();shifts.save(admin,store,shift,new ShiftWrite(1L,"Early","09:00","12:00",false,"none",0,0,0,0,false,true));shifts.save(admin,store,second,new ShiftWrite(0L,"Later","13:00","16:00",false,"none",0,0,0,0,false,true));
  schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift,LocalDate.of(2026,10,31),"scheduled"));schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,second,LocalDate.of(2026,10,31),"scheduled"));shifts.save(admin,store,second,new ShiftWrite(1L,"Later","11:00","14:00",false,"none",0,0,0,0,false,true));
  var p=plans.preview(admin,store,request());assertThat(p.conflicts()).isEqualTo(1);assertThat(p.items()).anyMatch(row->row.reason().equals("CANDIDATE_OVERLAP"));assertThat(copies.commit(admin,store,UUID.randomUUID(),new Commit(request(),p.fingerprint())).created()).isEqualTo(3);
 }
 @Test void tooManyDistinctEmployeesIsRejectedAndActorCannotReplayOthersReceipt(){
  var command=UUID.randomUUID();var input=new Commit(request(),plans.preview(admin,store,request()).fingerprint());copies.commit(admin,store,command,input);var otherId=account("tenant_admin",Set.of(store),admin.permissions());var other=new com.rpb.reservation.walkin.api.CurrentActor(tenant,otherId,"staff",admin.roles(),admin.permissions(),Set.of(store));assertThatThrownBy(()->copies.commit(other,store,command,input)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(409);
  new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(status->{for(int i=0;i<1000;i++)rawSource(cloneEmployee("C"+i),request().sourceWeek());});assertThatThrownBy(()->plans.preview(admin,store,request())).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_COPY_TOO_LARGE");
 }

 @Test void cancelledSourcesAreNeverCopiedIntoFollowingWeek(){
  var source=sourceIds.getFirst();schedules.save(admin,store,source,new ScheduleWrite(1L,employee,shift,LocalDate.of(2026,10,29),"cancelled"));var p=plans.preview(admin,store,request());assertThat(p.total()).isEqualTo(1);assertThat(p.items()).extracting(Item::sourceId).doesNotContain(source);var result=copies.commit(admin,store,UUID.randomUUID(),new Commit(request(),p.fingerprint()));assertThat(result.created()).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from workforce_schedules where tenant_id=? and work_date='2026-11-05'",Long.class,tenant)).isZero();assertThat(scheduling.find(tenant,source).orElseThrow().status()).isEqualTo("cancelled");
 }

 @Test void administratorAndManagerShareTheSameStoreCalendarBidirectionallyWithoutCrossStoreWrites(){
  var managerId=account("store_manager",Set.of(store,sibling),Set.of("workforce.view"));admins.save(admin,store,managerId,new WorkforceDtos.AdministratorWrite(0L,true,null));var manager=new com.rpb.reservation.walkin.api.CurrentActor(tenant,managerId,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store,sibling));
  var c=new Commit(request(),plans.preview(admin,store,request()).fingerprint());var command=UUID.randomUUID();copies.commit(admin,store,command,c);var boardPlans=proxy(new BulkSchedulePlanService(access,bulk,scheduling,new WorkforceJson(new ObjectMapper().findAndRegisterModules()),clock));assertThat(boardPlans.board(manager,store,request().targetWeek(),null,"",50,0)).isEqualTo(boardPlans.board(admin,store,request().targetWeek(),null,"",50,0));
  var next=new Request(request().targetWeek(),request().targetWeek().plusDays(7));copies.commit(manager,store,UUID.randomUUID(),new Commit(next,plans.preview(manager,store,next).fingerprint()));assertThat(boardPlans.board(admin,store,next.targetWeek(),null,"",50,0).items()).hasSize(2);assertThat(boardPlans.board(admin,sibling,next.targetWeek(),null,"",50,0).items()).isEmpty();
  assertThatThrownBy(()->plans.preview(manager,sibling,next)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);assertThatThrownBy(()->copies.commit(manager,sibling,UUID.randomUUID(),c)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);assertThatThrownBy(()->copies.commit(admin,sibling,command,c)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(409);
 }
}
