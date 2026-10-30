package com.rpb.reservation.workforce;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.attendance.*;
import com.rpb.reservation.workforce.approval.*;
import com.rpb.reservation.workforce.leave.*;
import com.rpb.reservation.workforce.calendar.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import static com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import static com.rpb.reservation.workforce.leave.LeaveDtos.*;
import static com.rpb.reservation.workforce.calendar.CalendarDtos.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class WorkforceApprovalPostgresTest extends WorkforceAttendancePostgresTest {
 ApprovalService approvals;WorkSummaryService summaries;LeaveService leaveService;CalendarService calendarService;
 @BeforeEach void approvalSetup(){
  var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());var audit=new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules());
  var scope=new AttendanceAccess(access,repository,scheduling);var leaves=new LeaveRepository(jdbc);var calendars=new CalendarRepository(jdbc);var types=new LeaveTypeRepository(jdbc);
  var sources=new ApprovalSources(attendance,leaves,calendars,json,time);var ap=new ApprovalRepository(jdbc,json);
  approvals=proxy(new ApprovalService(access,scope,repository,scheduling,ap,sources,audit,time));summaries=new WorkSummaryService(access,directory,scheduling,ap,sources);
  leaveService=proxy(new LeaveService(access,scope,repository,scheduling,types,leaves,audit,time));calendarService=proxy(new CalendarService(access,scope,scheduling,calendars,audit));
 }
 ApprovalWrite command(ApprovalRow r){return new ApprovalWrite(r.approval()==null?0L:r.approval().version(),r.sourceFingerprint(),"Checked");}
 com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule completed(){var s=schedule(store,shift(store,"none"),DATE);punch(s,"IN");time.at("2026-10-28T09:00:00Z");punch(s,"OUT");time.at("2026-10-28T10:00:00Z");return s;}
 @Test void approvalIsVersionedReplaysAndIsInvalidatedByCalendarChange(){
  var s=completed();var row=approvals.detail(admin,store,s.id()).row();var c=command(row);var a=approvals.approve(admin,store,s.id(),c);assertThat(approvals.approve(admin,store,s.id(),c)).isEqualTo(a);
  assertThat(summaries.summary(admin,store,DATE,DATE,null).items().getFirst().workSeconds()).isEqualTo(28800);
  calendarService.save(admin,store,UUID.randomUUID(),new CalendarWrite(0L,DATE,"store_rest","Maintenance",true));
  var stale=approvals.detail(admin,store,s.id());assertThat(stale.row().requiresReview()).isTrue();assertThat(stale.history()).hasSize(1);
  assertThat(summaries.summary(admin,store,DATE,DATE,null).items().getFirst().workSeconds()).isZero();
  approvals.approve(admin,store,s.id(),command(stale.row()));assertThat(approvals.detail(admin,store,s.id()).history()).hasSize(2);
 }
 @Test void wholeLeaveCanBeApprovedWithoutClockAndCancelRequestInvalidatesIt(){
  var s=schedule(store,shift(store,"duration"),DATE);var id=UUID.randomUUID();jdbc.update("insert into workforce_leave_types(id,tenant_id,store_id,name,paid,enabled,version) values(?,?,?,'Annual',true,true,1)",id,tenant,store);
  var l=leaveService.submit(worker,store,UUID.randomUUID(),new LeaveWrite(s.id(),id,s.startAt(),s.endAt(),"Leave"));leaveService.decide(admin,store,l.id(),new DecisionWrite(1L,"approved","Verified"));time.at("2026-10-28T10:00:00Z");
  var row=approvals.detail(admin,store,s.id()).row();assertThat(row.issues()).isEmpty();var a=approvals.approve(admin,store,s.id(),command(row));assertThat(a.paidLeaveSeconds()).isEqualTo(27000);
  leaveService.decide(worker,store,l.id(),new DecisionWrite(2L,"cancel_requested","Cancel"));assertThat(approvals.detail(admin,store,s.id()).row().requiresReview()).isTrue();
  assertThatThrownBy(()->approvals.approve(admin,store,s.id(),command(approvals.detail(admin,store,s.id()).row()))).isInstanceOf(WorkforceException.class);
 }
 @Test void changedAttendanceCannotUseOldFingerprintOrOldApprovalInSummary(){
  var s=completed();var row=approvals.detail(admin,store,s.id()).row();approvals.approve(admin,store,s.id(),command(row));
  var c=corrections.submit(worker,store,UUID.randomUUID(),new CorrectionWrite(s.id(),2L,s.startAt(),s.endAt().minusSeconds(3600),List.of(),false,"Correct end"));
  assertThat(approvals.detail(admin,store,s.id()).row().requiresReview()).isTrue();corrections.decide(admin,store,c.id(),new DecisionWrite(1L,"approved","Checked"));
  assertThatThrownBy(()->approvals.approve(admin,store,s.id(),new ApprovalWrite(1L,row.sourceFingerprint(),"Checked"))).isInstanceOf(WorkforceException.class);
  var next=approvals.detail(admin,store,s.id()).row();assertThat(approvals.approve(admin,store,s.id(),command(next)).workSeconds()).isEqualTo(25200);
 }
 @Test void employeesAndUndelegatedManagersCannotApproveOrViewManagementSummary(){
  var s=completed();var row=approvals.detail(worker,store,s.id()).row();assertThatThrownBy(()->approvals.approve(worker,store,s.id(),command(row))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->summaries.summary(worker,store,DATE,DATE,null)).isInstanceOf(WorkforceException.class);
  var fake=new CurrentActor(tenant,employeeAccount,"staff",Set.of("tenant_admin"),Set.of("workforce.view","workforce.admin.manage","tenant.admin.manage"),Set.of(store,sibling));
  assertThatThrownBy(()->approvals.approve(fake,store,s.id(),command(row))).isInstanceOf(WorkforceException.class);
 }
 @Test void companySummaryIncludesSupportStoreAndSeparatesEmployers(){
  var a=completed();approvals.approve(admin,store,a.id(),command(approvals.detail(admin,store,a.id()).row()));
  time.at("2026-10-28T01:00:00Z");var late=shifts.save(admin,sibling,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ShiftWrite(0L,"Late","17:00","21:00",false,"none",0,0,0,0,false,true));var b=schedule(sibling,late,DATE);
  time.at("2026-10-28T09:00:00Z");punches.punch(worker,sibling,UUID.randomUUID(),new ClockWrite(b.id(),"IN"));time.at("2026-10-28T13:00:01Z");punches.punch(worker,sibling,UUID.randomUUID(),new ClockWrite(b.id(),"OUT"));
  approvals.approve(admin,sibling,b.id(),command(approvals.detail(admin,sibling,b.id()).row()));var all=summaries.summary(admin,store,DATE,DATE,company).items();assertThat(all).hasSize(2);assertThat(all.stream().mapToLong(SummaryRow::workSeconds).sum()).isEqualTo(43201);
  assertThat(summaries.summary(admin,store,DATE,DATE,otherCompany).items()).isEmpty();
  var restricted=new CurrentActor(tenant,adminId,"staff",admin.roles(),admin.permissions(),Set.of(store));assertThatThrownBy(()->summaries.summary(restricted,store,DATE,DATE,company)).isInstanceOf(WorkforceException.class);
 }
 @Test void supportAtAnotherCompanysStoreStillBelongsToPayingCompany(){
  jdbc.update("insert into auth_account_store_access(account_id,tenant_id,store_id) values(?,?,?)",employeeAccount,tenant,otherStore);
  var seed=command(1,List.of(store,sibling,otherStore),company,employeeAccount,null);
  employees.save(admin,store,employee,new WorkforceDtos.EmployeeWrite(1L,"ATT001",seed.name(),seed.phone(),seed.email(),seed.jobTitle(),seed.hireDate(),null,seed.status(),company,repository.find(tenant,employee).orElseThrow().companyEffectiveDate(),store,seed.storeIds(),employeeAccount,null,null));
  worker=new CurrentActor(tenant,employeeAccount,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store,sibling,otherStore));
  var s=schedule(otherStore,shift(otherStore,"none"),DATE);punches.punch(worker,otherStore,UUID.randomUUID(),new ClockWrite(s.id(),"IN"));time.at("2026-10-28T10:00:00Z");punches.punch(worker,otherStore,UUID.randomUUID(),new ClockWrite(s.id(),"OUT"));
  approvals.approve(admin,otherStore,s.id(),command(approvals.detail(admin,otherStore,s.id()).row()));
  assertThat(summaries.summary(admin,store,DATE,DATE,company).items()).singleElement().satisfies(r->{assertThat(r.storeId()).isEqualTo(otherStore);assertThat(r.companyId()).isEqualTo(company);});
  assertThat(summaries.summary(admin,store,DATE,DATE,otherCompany).items()).isEmpty();
  var restricted=new CurrentActor(tenant,adminId,"staff",admin.roles(),admin.permissions(),Set.of(store,sibling));
  assertThatThrownBy(()->summaries.summary(restricted,store,DATE,DATE,company)).isInstanceOf(WorkforceException.class);
 }
 @Test void gateDenialOnAnySourceStoreRejectsWholeCompanySummary(){
  var s=completed();approvals.approve(admin,store,s.id(),command(approvals.detail(admin,store,s.id()).row()));
  doAnswer(call->{com.rpb.reservation.appgate.application.AppGateAccessRequest r=call.getArgument(0);return r.storeId().equals(sibling)?com.rpb.reservation.appgate.domain.AppGateDecision.deny(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission(),com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_EXPIRED):com.rpb.reservation.appgate.domain.AppGateDecision.allow(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission());}).when(gate).evaluate(any());
  assertThatThrownBy(()->summaries.summary(admin,store,DATE,DATE,company)).isInstanceOf(WorkforceException.class).hasMessage("STORE_APP_EXPIRED");
  assertThatThrownBy(()->approvals.list(worker,sibling,DATE,DATE)).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->leaveService.list(worker,sibling,DATE,DATE)).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->calendarService.list(worker,sibling,DATE,DATE)).isInstanceOf(WorkforceException.class);
 }
 @Test void concurrentApprovalsCannotOverwriteEachOthersVersion()throws Exception{
  var s=completed();var row=approvals.detail(admin,store,s.id()).row();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
  try{var calls=new ArrayList<java.util.concurrent.Future<Boolean>>();for(String note:List.of("First","Second"))calls.add(pool.submit(()->{start.await();try{approvals.approve(admin,store,s.id(),new ApprovalWrite(0L,row.sourceFingerprint(),note));return true;}catch(WorkforceException e){return false;}}));start.countDown();int count=0;for(var call:calls)if(call.get(20,java.util.concurrent.TimeUnit.SECONDS))count++;assertThat(count).isEqualTo(1);assertThat(approvals.detail(admin,store,s.id()).history()).hasSize(1);}finally{pool.shutdownNow();}
 }
 @Test void personalListAppliesLimitAfterEmployeeScope(){
  var own=schedule(store,shift(store,"none"),DATE);
  jdbc.update("insert into workforce_employees(id,tenant_id,employee_no,name,hire_date,status,company_id,company_effective_date,main_store_id,version) select gen_random_uuid(),?,'SCALE-'||n,'Other',?,'active',?,?,?,1 from generate_series(1,1001) n",tenant,DATE,company,DATE,store);
  jdbc.update("insert into workforce_schedules(id,tenant_id,store_id,employee_id,company_id,shift_id,work_date,start_at,end_at,timezone,shift_snapshot,status,version) select gen_random_uuid(),e.tenant_id,?,e.id,e.company_id,s.shift_id,s.work_date,s.start_at,s.end_at,s.timezone,s.shift_snapshot,'scheduled',1 from workforce_employees e cross join workforce_schedules s where e.tenant_id=? and e.employee_no like 'SCALE-%' and s.id=?",store,tenant,own.id());
  assertThat(approvals.list(worker,store,DATE,DATE)).extracting(r->r.schedule().id()).containsExactly(own.id());
 }
 @Test void approvalAuditFailureRollsBackSnapshot(){
  var s=completed();var c=command(approvals.detail(admin,store,s.id()).row());when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenThrow(new IllegalStateException("audit"));
  assertThatThrownBy(()->approvals.approve(admin,store,s.id(),c)).isInstanceOf(IllegalStateException.class);assertThat(approvals.detail(admin,store,s.id()).history()).isEmpty();
 }
}
