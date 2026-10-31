package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.bulk.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import static com.rpb.reservation.workforce.scheduling.bulk.BulkScheduleDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;
class WorkforceBulkSchedulePostgresTest extends WorkforcePostgresTest {
 BulkScheduleRepository bulk;BulkSchedulePlanService plans;BulkScheduleService service;SchedulingRepository scheduling;ScheduleService schedules;UUID employee,shift;
 @BeforeEach void bulkSetup(TestInfo info){if(info.getTestMethod().orElseThrow().getDeclaringClass()!=WorkforceBulkSchedulePostgresTest.class)return;var mapper=new ObjectMapper().findAndRegisterModules();var json=new WorkforceJson(mapper);var audit=new WorkforceAudit(logs,mapper);scheduling=new SchedulingRepository(jdbc,json);bulk=new BulkScheduleRepository(jdbc,json);schedules=proxy(new ScheduleService(access,scheduling,repository,audit,clock));plans=proxy(new BulkSchedulePlanService(access,bulk,scheduling,json,clock));service=proxy(new BulkScheduleService(access,bulk,scheduling,repository,plans,schedules,audit,json));employee=UUID.randomUUID();employees.save(admin,store,employee,command(0,List.of(store,sibling),company,null,null));shift=UUID.randomUUID();proxy(new ShiftService(access,scheduling,audit)).save(admin,store,shift,new ShiftWrite(0L,"Early","09:00","17:00",false,"none",0,0,0,0,false,true));}
 Request request(){return new Request(List.of(employee),shift,LocalDate.of(2026,10,29),LocalDate.of(2026,10,30),List.of(1,2,3,4,5,6,7));}
 @Test void previewIsReadonlyCommitSkipsExistingAndReplayCannotDuplicate(){
  schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift,request().from(),"scheduled"));
  long before=jdbc.queryForObject("select count(*) from workforce_schedules",Long.class);var p=plans.preview(admin,store,request());assertThat(p.ready()).isEqualTo(1);assertThat(p.existing()).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from workforce_schedules",Long.class)).isEqualTo(before);
  var id=UUID.randomUUID();var c=new Commit(request(),p.fingerprint());var result=service.commit(admin,store,id,c);assertThat(result.created()).isEqualTo(1);assertThat(service.commit(admin,store,id,c)).isEqualTo(result);assertThat(jdbc.queryForObject("select count(*) from workforce_schedules",Long.class)).isEqualTo(before+1);
  assertThatThrownBy(()->service.commit(admin,store,id,new Commit(request(),"0".repeat(64)))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(409);
 }
 @Test void changedPreviewAndAuditFailureWriteNothing(){
  var p=plans.preview(admin,store,request());schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift,request().from(),"scheduled"));
  assertThatThrownBy(()->service.commit(admin,store,UUID.randomUUID(),new Commit(request(),p.fingerprint()))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_BULK_CHANGED");
  var fresh=plans.preview(admin,store,request());long count=jdbc.queryForObject("select count(*) from workforce_schedules",Long.class);doThrow(new IllegalStateException("audit unavailable")).when(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
  assertThatThrownBy(()->service.commit(admin,store,UUID.randomUUID(),new Commit(request(),fresh.fingerprint()))).isInstanceOf(IllegalStateException.class);assertThat(jdbc.queryForObject("select count(*) from workforce_schedules",Long.class)).isEqualTo(count);assertThat(jdbc.queryForObject("select count(*) from workforce_bulk_schedule_commands where tenant_id=?",Long.class,tenant)).isZero();
 }
 @Test void foreignEmployeesAndUnmanagedStoresAreRejectedAndOptionsRespectPosition(){
  assertThatThrownBy(()->plans.preview(admin,store,new Request(List.of(UUID.randomUUID()),shift,request().from(),request().to(),request().weekdays()))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(404);
  var page=plans.options(admin,store,null,"Alice",50,0);assertThat(page.total()).isEqualTo(1);var position=repository.find(tenant,employee).orElseThrow().positionId();assertThat(plans.options(admin,store,position,"",50,0).total()).isEqualTo(1);assertThat(plans.options(admin,store,UUID.randomUUID(),"",50,0).total()).isZero();
  var worker=new com.rpb.reservation.walkin.api.CurrentActor(tenant,adminId,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));assertThatThrownBy(()->plans.preview(worker,store,request())).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
 }
 @Test void crossStoreOverlapIsAConflictWithoutExposingOtherStoreDetails(){
  var otherShift=UUID.randomUUID();proxy(new ShiftService(access,scheduling,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules()))).save(admin,sibling,otherShift,new ShiftWrite(0L,"Other","10:00","18:00",false,"none",0,0,0,0,false,true));schedules.save(admin,sibling,UUID.randomUUID(),new ScheduleWrite(0L,employee,otherShift,request().from(),"scheduled"));
  var p=plans.preview(admin,store,request());assertThat(p.conflicts()).isEqualTo(1);assertThat(p.items().getFirst().reason()).isEqualTo("TIME_OVERLAP");assertThat(p.ready()).isEqualTo(1);
 }

 @Test void limitsDisabledShiftAndChangedEmploymentAreValidated(){
  assertThatThrownBy(()->plans.preview(admin,store,new Request(List.of(employee),shift,request().from(),request().from().plusDays(31),List.of(1)))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->plans.preview(admin,store,new Request(List.of(employee),shift,request().from().minusDays(5),request().to(),List.of(1,2,3,4,5,6,7)))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->plans.preview(admin,store,new Request(List.of(employee),shift,request().from(),request().to(),List.of(0)))).isInstanceOf(WorkforceException.class);
  var p=plans.preview(admin,store,request());jdbc.update("update workforce_employees set version=version+1 where id=?",employee);
  assertThatThrownBy(()->service.commit(admin,store,UUID.randomUUID(),new Commit(request(),p.fingerprint()))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_BULK_CHANGED");
  jdbc.update("update workforce_shifts set snapshot=jsonb_set(snapshot,'{enabled}','false'),enabled=false where id=?",shift);
  assertThatThrownBy(()->plans.preview(admin,store,request())).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(400);
 }
 @Test void storeTenantAndAppGateBoundariesApplyToEveryPath(){
  assertThatThrownBy(()->plans.preview(admin,otherStore,request())).isInstanceOf(WorkforceException.class);
  var foreign=new com.rpb.reservation.walkin.api.CurrentActor(UUID.randomUUID(),adminId,"staff",Set.of("tenant_admin"),admin.permissions(),Set.of(store));
  assertThatThrownBy(()->plans.preview(foreign,store,request())).isInstanceOf(RuntimeException.class);
  var restricted=new com.rpb.reservation.walkin.api.CurrentActor(tenant,adminId,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(sibling));
  assertThatThrownBy(()->plans.board(restricted,store,LocalDate.of(2026,10,26),null,"",50,0)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
  for(var reason:List.of(com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_EXPIRED)){
   doAnswer(call->{var r=(com.rpb.reservation.appgate.application.AppGateAccessRequest)call.getArgument(0);return com.rpb.reservation.appgate.domain.AppGateDecision.deny(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission(),reason);}).when(gate).evaluate(any());
   assertThatThrownBy(()->plans.options(admin,store,null,"",50,0)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
   assertThatThrownBy(()->plans.preview(admin,store,request())).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
   assertThatThrownBy(()->service.commit(admin,store,UUID.randomUUID(),new Commit(request(),"0".repeat(64)))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
  }
 }
 @Test void concurrentCommandsCannotDuplicateAndSameCommandReplays()throws Exception{
  var c=new Commit(request(),plans.preview(admin,store,request()).fingerprint());var id=UUID.randomUUID();
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var a=pool.submit(()->service.commit(admin,store,id,c));var b=pool.submit(()->service.commit(admin,store,id,c));assertThat(a.get()).isEqualTo(b.get());
   assertThat(jdbc.queryForObject("select count(*) from workforce_schedules where tenant_id=?",Long.class,tenant)).isEqualTo(2);
   var fresh=new Request(List.of(employee),shift,request().to().plusDays(1),request().to().plusDays(1),List.of(1,2,3,4,5,6,7));var next=new Commit(fresh,plans.preview(admin,store,fresh).fingerprint());
   var results=pool.invokeAll(List.<java.util.concurrent.Callable<String>>of(()->commitOutcome(next),()->commitOutcome(next)));
   assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder("created","WORKFORCE_BULK_CHANGED");
  }
 }
 String commitOutcome(Commit c){try{service.commit(admin,store,UUID.randomUUID(),c);return "created";}catch(WorkforceException e){return e.code();}}
 @Test void storeTimezoneCannotChangeBetweenPreviewRecheckAndScheduleWrites()throws Exception{
  var c=new Commit(request(),plans.preview(admin,store,request()).fingerprint());var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  doAnswer(call->{entered.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("test timeout");return null;}).when(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var saved=pool.submit(()->service.commit(admin,store,UUID.randomUUID(),c));try{
    assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    var update=pool.submit(()->{new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(status->{jdbc.execute("set local lock_timeout='200ms'");jdbc.update("update stores set timezone='UTC' where id=?",store);});return true;});
    assertThatThrownBy(()->update.get(5,java.util.concurrent.TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(org.springframework.dao.DataAccessException.class);
   }finally{release.countDown();}
   assertThat(saved.get(10,java.util.concurrent.TimeUnit.SECONDS).created()).isEqualTo(2);assertThat(scheduling.timezone(tenant,store)).isEqualTo("Asia/Singapore");
  }
 }
 @Test void delegatedManagerUsesExistingScopeAndZeroReadyCannotCreateAReceipt(){
  UUID manager=account("store_manager",Set.of(store),Set.of("workforce.view"));admins.save(admin,store,manager,new WorkforceDtos.AdministratorWrite(0L,true,null));
  var actor=new com.rpb.reservation.walkin.api.CurrentActor(tenant,manager,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store));
  var preview=plans.preview(actor,store,request());var command=UUID.randomUUID();var input=new Commit(request(),preview.fingerprint());assertThat(service.commit(actor,store,command,input).created()).isEqualTo(2);
  assertThatThrownBy(()->service.commit(admin,store,command,input)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(409);
  var full=plans.preview(actor,store,request());assertThat(full.ready()).isZero();assertThatThrownBy(()->service.commit(actor,store,UUID.randomUUID(),new Commit(request(),full.fingerprint()))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(400);
  assertThatThrownBy(()->plans.options(actor,sibling,null,"",50,0)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
  jdbc.update("update workforce_administrators set employee_manage=false where tenant_id=? and account_id=?",tenant,manager);
  assertThatThrownBy(()->service.commit(actor,store,command,input)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
 }
 @Test void capacityFiveThousandAndBoardPaginationRetainHistoricalEmployees(){
  var ids=new ArrayList<UUID>();ids.add(employee);
  new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(status->{
  for(int i=1;i<500;i++){UUID id=UUID.randomUUID();ids.add(id);jdbc.update("insert into workforce_employees(id,tenant_id,employee_no,name,hire_date,status,company_id,company_effective_date,main_store_id,version,position_id) select ?,tenant_id,?,name,hire_date,status,company_id,company_effective_date,main_store_id,version,position_id from workforce_employees where id=?",id,"B"+String.format("%04d",i),employee);jdbc.update("insert into workforce_work_stores(employee_id,tenant_id,store_id,started_on) values(?,?,?,'2026-10-01')",id,tenant,store);}
  });
  var c=new Request(ids,shift,request().from(),request().from().plusDays(9),List.of(1,2,3,4,5,6,7));var p=plans.preview(admin,store,c);assertThat(p.ready()).isEqualTo(5000);
  assertThatThrownBy(()->plans.preview(admin,store,new Request(ids,shift,c.from(),c.to().plusDays(1),c.weekdays()))).isInstanceOf(WorkforceException.class);
  long started=System.nanoTime();var result=service.commit(admin,store,UUID.randomUUID(),new Commit(c,p.fingerprint()));assertThat(result.created()).isEqualTo(5000);System.out.println("BULK_5000_MS="+(System.nanoTime()-started)/1_000_000);
  jdbc.update("update workforce_employees set status='left',leave_date='2026-10-30' where id=?",employee);jdbc.update("update workforce_work_stores set ended_on='2026-10-31' where employee_id=?",employee);
  var seen=new HashSet<UUID>();int schedules=0;for(int at=0;at<500;at+=50){var board=plans.board(admin,store,LocalDate.of(2026,10,26),null,"",50,at);assertThat(board.total()).isEqualTo(500);board.employees().forEach(e->assertThat(seen.add(e.id())).isTrue());schedules+=board.items().size();assertThat(board.weekScheduleCount()).isEqualTo(2000);}
  assertThat(seen).hasSize(500).contains(employee);assertThat(schedules).isEqualTo(2000);assertThat(plans.options(admin,store,null,"",50,0).total()).isEqualTo(499);
 }

 @Test void monthBoardUsesOneEmployeeScopeForTheWholeMonthAndIncludesLastDay(){
  schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift,LocalDate.of(2026,10,31),"scheduled"));
  jdbc.update("update workforce_employees set status='left',leave_date='2026-10-31' where id=?",employee);jdbc.update("update workforce_work_stores set ended_on='2026-11-01' where employee_id=?",employee);
  var month=plans.monthBoard(admin,store,LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),null,"",1,0);assertThat(month.total()).isEqualTo(1);assertThat(month.employees().getFirst().id()).isEqualTo(employee);assertThat(month.items()).hasSize(1);assertThat(month.items().getFirst().workDate()).isEqualTo(LocalDate.of(2026,10,31));assertThat(month.periodScheduleCount()).isEqualTo(1);assertThat(month.weekScheduleCount()).isZero();
  assertThat(plans.monthBoard(admin,store,LocalDate.of(2026,11,1),LocalDate.of(2026,11,30),null,"",1,0).total()).isZero();
  assertThat(plans.monthBoard(admin,store,LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),UUID.randomUUID(),"",1,0).total()).isZero();
  for(LocalDate to:List.of(LocalDate.of(2026,10,30),LocalDate.of(2026,11,1)))assertThatThrownBy(()->plans.monthBoard(admin,store,LocalDate.of(2026,10,1),to,null,"",50,0)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(400);
  assertThatThrownBy(()->plans.monthBoard(admin,store,LocalDate.of(2026,10,2),LocalDate.of(2026,10,31),null,"",50,0)).isInstanceOf(WorkforceException.class);
  var worker=new com.rpb.reservation.walkin.api.CurrentActor(tenant,adminId,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));assertThatThrownBy(()->plans.monthBoard(worker,store,LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),null,"",50,0)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
 }

 @Test void staffingCountsAllPagesDistinctPeopleAndDaysWithFiltersAndStoreIsolation(){
  var second=UUID.randomUUID();var position=UUID.randomUUID();jdbc.update("insert into workforce_positions(id,tenant_id,name,version) values(?,?,'Kitchen',1)",position,tenant);jdbc.update("insert into workforce_employees(id,tenant_id,employee_no,name,hire_date,status,company_id,company_effective_date,main_store_id,version,position_id) select ?,tenant_id,'E002','Bob',hire_date,status,company_id,company_effective_date,main_store_id,1,? from workforce_employees where id=?",second,position,employee);jdbc.update("insert into workforce_work_stores(employee_id,tenant_id,store_id,started_on) values(?,?,?,'2026-10-01')",second,tenant,store);
  for(var id:List.of(employee,second))schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,id,shift,LocalDate.of(2026,10,29),"scheduled"));schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,second,shift,LocalDate.of(2026,10,30),"scheduled"));var cancelled=UUID.randomUUID();schedules.save(admin,store,cancelled,new ScheduleWrite(0L,employee,shift,LocalDate.of(2026,10,30),"scheduled"));schedules.save(admin,store,cancelled,new ScheduleWrite(1L,employee,shift,LocalDate.of(2026,10,30),"cancelled"));
  var removed=UUID.randomUUID();schedules.save(admin,store,removed,new ScheduleWrite(0L,employee,shift,LocalDate.of(2026,10,31),"scheduled"));proxy(new ScheduleDeletionService(access,scheduling,repository,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules()))).delete(admin,store,removed,new ScheduleDeletionDtos.Request(1L));
  var a=plans.board(admin,store,LocalDate.of(2026,10,26),null,"",1,0);var b=plans.board(admin,store,LocalDate.of(2026,10,26),null,"",1,1);assertThat(a.staffing()).isEqualTo(b.staffing());assertThat(a.staffing().shifts().getFirst().people()).isEqualTo(2);assertThat(a.staffing().shifts().getFirst().personDays()).isEqualTo(3);assertThat(a.staffing().days()).extracting(BulkScheduleDtos.ShiftDayCount::people).containsExactly(2L,1L);
  assertThat(plans.board(admin,store,LocalDate.of(2026,10,26),position,"",50,0).staffing().shifts().getFirst().people()).isEqualTo(1);assertThat(plans.board(admin,store,LocalDate.of(2026,10,26),null,"Alice",50,0).staffing().shifts().getFirst().personDays()).isEqualTo(1);assertThat(plans.monthBoard(admin,store,LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),null,"",50,0).staffing().shifts().getFirst().personDays()).isEqualTo(3);assertThat(plans.board(admin,sibling,LocalDate.of(2026,10,26),null,"",50,0).staffing().shifts()).isEmpty();assertThat(bulk.staffing(UUID.randomUUID(),store,LocalDate.of(2026,10,26),LocalDate.of(2026,11,1),null,"").shifts()).isEmpty();
 }

 @Test void staffingEmployeeListMatchesCountsDeduplicatesAndPaginatesWithinStore(){
  var audit=new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules());var shifts=proxy(new ShiftService(access,scheduling,audit));shifts.save(admin,store,shift,new ShiftWrite(1L,"Early","09:00","12:00",false,"none",0,0,0,0,false,true));schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift,LocalDate.of(2026,10,29),"scheduled"));shifts.save(admin,store,shift,new ShiftWrite(2L,"Early","13:00","16:00",false,"none",0,0,0,0,false,true));for(int day:List.of(29,30))schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift,LocalDate.of(2026,10,day),"scheduled"));
  var kitchen=UUID.randomUUID();jdbc.update("insert into workforce_positions(id,tenant_id,name,version) values(?,?,'Kitchen',1)",kitchen,tenant);var people=new ArrayList<UUID>();
  new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(status->{for(int n=1;n<=51;n++){var id=UUID.randomUUID();people.add(id);jdbc.update("insert into workforce_employees(id,tenant_id,employee_no,name,hire_date,status,company_id,company_effective_date,main_store_id,version,position_id) select ?,tenant_id,?,?,hire_date,status,company_id,company_effective_date,main_store_id,1,? from workforce_employees where id=?",id,"P"+String.format("%03d",n),n==1?"Person_1":"Person"+n,n==1?kitchen:repository.find(tenant,employee).orElseThrow().positionId(),employee);jdbc.update("insert into workforce_work_stores(employee_id,tenant_id,store_id,started_on) values(?,?,?,'2026-10-01')",id,tenant,store);schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,id,shift,LocalDate.of(2026,10,29),"scheduled"));}});
  var cancelled=UUID.randomUUID();schedules.save(admin,store,cancelled,new ScheduleWrite(0L,people.get(0),shift,LocalDate.of(2026,10,30),"scheduled"));schedules.save(admin,store,cancelled,new ScheduleWrite(1L,people.get(0),shift,LocalDate.of(2026,10,30),"cancelled"));var deleted=UUID.randomUUID();schedules.save(admin,store,deleted,new ScheduleWrite(0L,people.get(1),shift,LocalDate.of(2026,10,30),"scheduled"));proxy(new ScheduleDeletionService(access,scheduling,repository,audit)).delete(admin,store,deleted,new ScheduleDeletionDtos.Request(1L));
  var first=plans.staffingEmployees(admin,store,LocalDate.of(2026,10,29),LocalDate.of(2026,10,30),shift,null,"",50,0);var second=plans.staffingEmployees(admin,store,LocalDate.of(2026,10,29),LocalDate.of(2026,10,30),shift,null,"",50,50);assertThat(first.total()).isEqualTo(53);assertThat(first.people()).isEqualTo(52);var multi=first.items().stream().filter(row->row.employeeId().equals(employee)&&row.date().equals(LocalDate.of(2026,10,29))).findFirst().orElseThrow();assertThat(multi.schedules()).hasSize(2);assertThat(multi.schedules()).allMatch(slot->slot.timezone().equals("Asia/Singapore"));assertThat(multi.schedules().get(0).startAt()).isBefore(multi.schedules().get(1).startAt());for(var slot:multi.schedules()){assertThat(scheduling.store(tenant,slot.id())).isEqualTo(store);assertThat(scheduling.find(tenant,slot.id()).orElseThrow().employeeId()).isEqualTo(employee);}
  assertThat(first.items()).hasSize(50);assertThat(second.items()).hasSize(3);var all=new ArrayList<BulkScheduleDtos.StaffingEmployee>(first.items());all.addAll(second.items());assertThat(all.stream().map(row->row.employeeId()+":"+row.date()).distinct().count()).isEqualTo(53);
  assertThat(plans.staffingEmployees(admin,store,LocalDate.of(2026,10,29),LocalDate.of(2026,10,29),shift,null,"",50,0).total()).isEqualTo(52);assertThat(plans.staffingEmployees(admin,store,LocalDate.of(2026,10,29),LocalDate.of(2026,10,30),shift,kitchen,"",50,0).total()).isEqualTo(1);assertThat(plans.staffingEmployees(admin,store,LocalDate.of(2026,10,29),LocalDate.of(2026,10,30),shift,null,"Person_1",50,0).total()).isEqualTo(1);assertThat(plans.staffingEmployees(admin,store,LocalDate.of(2026,10,29),LocalDate.of(2026,10,30),shift,null,"%",50,0).total()).isZero();
  assertThat(plans.staffingEmployees(admin,sibling,LocalDate.of(2026,10,29),LocalDate.of(2026,10,30),shift,null,"",50,0).total()).isZero();assertThat(bulk.staffingEmployees(UUID.randomUUID(),store,LocalDate.of(2026,10,29),LocalDate.of(2026,10,30),shift,null,"",50,0).total()).isZero();assertThat(first.total()).isEqualTo(plans.board(admin,store,LocalDate.of(2026,10,26),null,"",1,0).staffing().shifts().getFirst().personDays());
 }
 @Test void staffingEmployeeListKeepsPermissionAppGateAndParameterGuards(){
  var from=LocalDate.of(2026,10,29);var worker=new com.rpb.reservation.walkin.api.CurrentActor(tenant,adminId,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));assertThatThrownBy(()->plans.staffingEmployees(worker,store,from,from,shift,null,"",50,0)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
  assertThatThrownBy(()->plans.staffingEmployees(admin,store,from,from.plusDays(31),shift,null,"",50,0)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(400);assertThatThrownBy(()->plans.staffingEmployees(admin,store,from,from,shift,null,"",101,0)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(400);
  doAnswer(call->{var r=(com.rpb.reservation.appgate.application.AppGateAccessRequest)call.getArgument(0);return com.rpb.reservation.appgate.domain.AppGateDecision.deny(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission(),com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED);}).when(gate).evaluate(any());assertThatThrownBy(()->plans.staffingEmployees(admin,store,from,from,shift,null,"",50,0)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
 }
 @Test void dailyTotalsDeduplicateEmployeesAcrossShiftsAndIgnoreCancelledRows(){
  var day=LocalDate.of(2026,10,29);var late=UUID.randomUUID();var mapper=new ObjectMapper().findAndRegisterModules();var audit=new WorkforceAudit(logs,mapper);
  proxy(new ShiftService(access,scheduling,audit)).save(admin,store,late,new ShiftWrite(0L,"Late","18:00","23:00",false,"none",0,0,0,0,false,true));
  schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift,day,"scheduled"));
  var second=schedules.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,late,day,"scheduled"));
  var stats=plans.board(admin,store,LocalDate.of(2026,10,26),null,"",1,0).staffing();assertThat(stats.shifts()).hasSize(2);assertThat(stats.days()).allMatch(d->d.people()==1);assertThat(stats.dailyTotals()).containsExactly(new DailyTotal(day,1));
  schedules.save(admin,store,second.id(),new ScheduleWrite(second.version(),employee,late,day,"cancelled"));
  assertThat(plans.board(admin,store,LocalDate.of(2026,10,26),null,"",1,0).staffing().dailyTotals()).containsExactly(new DailyTotal(day,1));
  assertThat(bulk.staffing(UUID.randomUUID(),store,day,day,null,"%").dailyTotals()).isEmpty();
 }
}
