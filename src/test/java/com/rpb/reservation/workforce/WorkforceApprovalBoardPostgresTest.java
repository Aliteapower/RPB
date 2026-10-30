package com.rpb.reservation.workforce;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.approval.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.attendance.*;
import com.rpb.reservation.workforce.calendar.*;
import com.rpb.reservation.workforce.leave.*;
import com.rpb.reservation.workforce.payroll.PayrollAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;
import static com.rpb.reservation.workforce.approval.ApprovalBoardDtos.*;
import static com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import static org.assertj.core.api.Assertions.*;
class WorkforceApprovalBoardPostgresTest extends WorkforceApprovalPostgresTest {
 ApprovalBoardService board;ApprovalBatchService batch;ApprovalReadinessService readiness;ApprovalSources sourceReader;ApprovalRepository approvalRecords;
 @BeforeEach void boardSetup(){var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());sourceReader=new ApprovalSources(attendance,new LeaveRepository(jdbc),new CalendarRepository(jdbc),json,time);approvalRecords=new ApprovalRepository(jdbc,json);var repo=new ApprovalBoardRepository(jdbc);board=proxy(new ApprovalBoardService(access,new AttendanceAccess(access,repository,scheduling),scheduling,approvalRecords,sourceReader,repo));batch=new ApprovalBatchService(access,approvals,tx);readiness=proxy(new ApprovalReadinessService(new PayrollAccess(access,directory,jdbc),repo,scheduling,approvalRecords,sourceReader));}
 Filter filter(){return new Filter(DATE,DATE.plusDays(3),null,"","all");}
 Schedule normal(Shift shift,LocalDate day){var s=schedule(store,shift,day);attendance.save(tenant,new AttendanceDtos.Session(UUID.randomUUID(),s.id(),employee,store,s.startAt(),s.endAt(),null,List.of(),false,1));return s;}
 CurrentActor manager(){var id=account("store_manager",Set.of(store,sibling),Set.of("workforce.view"));admins.save(admin,store,id,new WorkforceDtos.AdministratorWrite(0L,true,null));return new CurrentActor(tenant,id,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store,sibling));}
 @Test void boardNormalPreviewBatchReplayAndPerItemSourceConflict(){
  var shift=shift(store,"none");var a=normal(shift,DATE);var b=normal(shift,DATE.plusDays(1));time.at("2026-10-31T10:00:00Z");
  var preview=board.preview(admin,store,new Selection(filter(),true,List.of(),List.of()));assertThat(preview.items()).hasSize(2);assertThat(preview.workSeconds()).isEqualTo(57600);
  jdbc.update("update workforce_attendance_sessions set out_at=out_at-interval '1 minute',version=version+1 where tenant_id=? and schedule_id=?",tenant,b.id());
  var result=batch.commit(admin,store,new Commit("Checked",preview.items()));assertThat(result.succeeded()).isEqualTo(1);assertThat(result.failed()).isEqualTo(1);assertThat(approvalRecords.history(tenant,a.id())).hasSize(1);assertThat(approvalRecords.history(tenant,b.id())).isEmpty();
  var retry=batch.commit(admin,store,new Commit("Checked",preview.items()));assertThat(retry.succeeded()).isEqualTo(1);assertThat(approvalRecords.history(tenant,a.id())).hasSize(1);
 }
 @Test void boardManagerRecordsRequireAdministratorOnOldAndNewInterfaces(){
  var actor=manager();admins.save(admin,store,employeeAccount,new WorkforceDtos.AdministratorWrite(0L,true,null));var s=normal(shift(store,"none"),DATE);time.at("2026-10-28T10:00:00Z");
  var page=board.board(actor,store,filter(),50,0);assertThat(page.items()).singleElement().satisfies(e->{assertThat(e.category()).isEqualTo("manager");assertThat(e.canReview()).isFalse();assertThat(e.reviewBlock()).isEqualTo("WORKFORCE_MANAGER_ADMIN_REVIEW");});
  var d=approvals.detail(actor,store,s.id());assertThat(d.canReview()).isFalse();var forged=new Candidate(s.id(),s.employeeName(),s.employeeNo(),s.workDate(),0L,d.row().sourceFingerprint(),d.row().workSeconds(),0,0);assertThat(batch.commit(actor,store,new Commit("Forbidden manager",List.of(forged))).items()).singleElement().extracting(Outcome::code).isEqualTo("WORKFORCE_MANAGER_ADMIN_REVIEW");assertThatThrownBy(()->approvals.approve(actor,store,s.id(),command(d.row()))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_MANAGER_ADMIN_REVIEW");
  assertThatThrownBy(()->board.preview(actor,store,new Selection(filter(),true,List.of(),List.of()))).isInstanceOf(WorkforceException.class);
  var preview=board.preview(admin,store,new Selection(filter(),true,List.of(),List.of()));assertThat(batch.commit(admin,store,new Commit("Admin manager review",preview.items())).succeeded()).isEqualTo(1);
 }
 @Test void boardManagerCanReviewOrdinaryStaffButNotSelfOrAnotherStore(){
  var actor=manager();var s=normal(shift(store,"none"),DATE);time.at("2026-10-28T10:00:00Z");var preview=board.preview(actor,store,new Selection(filter(),true,List.of(),List.of()));assertThat(batch.commit(actor,store,new Commit("Manager review",preview.items())).succeeded()).isEqualTo(1);
  assertThatThrownBy(()->board.preview(actor,sibling,new Selection(filter(),true,List.of(),List.of()))).isInstanceOf(WorkforceException.class);
  var ownerManager=new CurrentActor(tenant,employeeAccount,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store,sibling));admins.save(admin,store,employeeAccount,new WorkforceDtos.AdministratorWrite(0L,true,null));assertThat(approvals.detail(ownerManager,store,s.id()).canReview()).isFalse();
 }
 @Test void boardBatchProjectionExactlyMatchesSingleSourceAndFindsStaleAfterPendingCorrection(){
  var s=normal(shift(store,"duration"),DATE);time.at("2026-10-28T10:00:00Z");var single=sourceReader.read(tenant,store,s);var many=sourceReader.readAll(tenant,store,List.of(s)).get(s.id());assertThat(many).isEqualTo(single);assertThat(sourceReader.fingerprint(many)).isEqualTo(sourceReader.fingerprint(single));
  approvals.approve(admin,store,s.id(),command(approvals.detail(admin,store,s.id()).row()));corrections.submit(worker,store,UUID.randomUUID(),new AttendanceDtos.CorrectionWrite(s.id(),1L,s.startAt(),s.endAt().minusSeconds(600),List.of(),false,"Review correction"));
  assertThat(board.board(admin,store,filter(),50,0).items()).singleElement().extracting(Entry::category).isEqualTo("stale");assertThatThrownBy(()->board.preview(admin,store,new Selection(filter(),true,List.of(),List.of()))).isInstanceOf(WorkforceException.class);
 }
 @Test void boardWaitingUnfinishedAndMissingAttendanceAreNeverBulkEligible(){
  var shift=shift(store,"none");normal(shift,DATE);schedule(store,shift,DATE.plusDays(1));
  var rows=board.board(admin,store,filter(),50,0);assertThat(rows.counts().get("waiting")).isEqualTo(2);
  time.at("2026-10-31T10:00:00Z");rows=board.board(admin,store,filter(),50,0);assertThat(rows.counts().get("exception")).isEqualTo(1);assertThat(board.preview(admin,store,new Selection(filter(),true,List.of(),List.of())).items()).hasSize(1);
 }
 @Test void boardReadinessUsesCurrentApprovalsAndOriginalPayrollScope(){
  var s=normal(shift(store,"none"),DATE);time.at("2026-10-28T10:00:00Z");var month=YearMonth.of(2026,10);assertThat(readiness.check(admin,store,company,month).pending()).isEqualTo(1);
  approvals.approve(admin,store,s.id(),command(approvals.detail(admin,store,s.id()).row()));assertThat(readiness.check(admin,store,company,month).ready()).isTrue();assertThat(readiness.check(admin,store,company,month).stores().getFirst().workSeconds()).isEqualTo(28800);
  assertThatThrownBy(()->readiness.check(manager(),store,company,month)).isInstanceOf(WorkforceException.class);assertThatThrownBy(()->readiness.check(worker,store,company,month)).isInstanceOf(WorkforceException.class);
  var salaryId=account("store_manager",Set.of(store,sibling),Set.of("workforce.view"));admins.save(admin,store,salaryId,new WorkforceDtos.AdministratorWrite(0L,false,company));var salaryOnly=new CurrentActor(tenant,salaryId,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store,sibling));assertThat(readiness.check(salaryOnly,store,company,month).ready()).isTrue();assertThatThrownBy(()->board.preview(salaryOnly,store,new Selection(filter(),true,List.of(),List.of()))).isInstanceOf(WorkforceException.class);
 }
 @Test void boardPaginationCrossPageSelectionAndExclusionBeyondThousand(){
  var s=normal(shift(store,"none"),DATE);time.at("2026-10-28T10:00:00Z");
  // Read-model stress fixture: distinct employees and complete closed sessions, no overlap per employee.
  jdbc.update("""
   insert into workforce_employees(id,tenant_id,employee_no,name,job_title,hire_date,status,company_id,company_effective_date,main_store_id,version)
   select gen_random_uuid(),?,'PAGE'||n,'Pagination '||n,'Service','2026-10-01','active',?,'2026-10-01',?,1 from generate_series(1,1001) n
   """,tenant,company,store);
  jdbc.update("""
   insert into workforce_schedules(id,tenant_id,store_id,employee_id,shift_id,work_date,start_at,end_at,timezone,status,shift_snapshot,company_id,version)
   select gen_random_uuid(),e.tenant_id,?,e.id,s.shift_id,s.work_date,s.start_at,s.end_at,s.timezone,s.status,s.shift_snapshot,s.company_id,1
   from workforce_employees e join workforce_schedules s on s.id=? and s.tenant_id=e.tenant_id where e.tenant_id=? and e.employee_no like 'PAGE%'
   """,store,s.id(),tenant);
  jdbc.update("""
   insert into workforce_attendance_sessions(id,tenant_id,store_id,employee_id,schedule_id,in_at,out_at,breaks,override_break,version)
   select gen_random_uuid(),s.tenant_id,s.store_id,s.employee_id,s.id,s.start_at,s.end_at,'[]'::jsonb,false,1 from workforce_schedules s join workforce_employees e on e.id=s.employee_id and e.tenant_id=s.tenant_id where s.tenant_id=? and e.employee_no like 'PAGE%'
   """,tenant);
  var page=board.board(admin,store,filter(),50,1000);assertThat(page.total()).isEqualTo(1002);assertThat(page.items()).hasSize(2);var preview=board.preview(admin,store,new Selection(filter(),true,List.of(),List.of(s.id())));assertThat(preview.items()).hasSize(1001);assertThat(summaries.summary(admin,store,DATE,DATE,null).items()).hasSize(1002);
 }
 @Test void boardAppGateAndCrossTenantScopeStayEnforced(){
  var s=normal(shift(store,"none"),DATE);time.at("2026-10-28T10:00:00Z");var foreign=new CurrentActor(UUID.randomUUID(),admin.actorId(),admin.actorType(),admin.roles(),admin.permissions(),admin.storeIds());assertThatThrownBy(()->board.board(foreign,store,filter(),50,0)).isInstanceOf(WorkforceException.class);
  for(var reason:List.of(com.rpb.reservation.appgate.domain.AppGateDenyReason.APP_DISABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.TENANT_APP_NOT_ENABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED)){
   org.mockito.Mockito.doReturn(com.rpb.reservation.appgate.domain.AppGateDecision.deny("workforce",tenant,store,"workforce.view",reason)).when(gate).evaluate(org.mockito.ArgumentMatchers.any());assertThatThrownBy(()->board.board(admin,store,filter(),50,0)).isInstanceOf(WorkforceException.class);
  }
 }
}
