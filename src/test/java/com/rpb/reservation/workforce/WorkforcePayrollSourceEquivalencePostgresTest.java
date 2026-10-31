package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.approval.*;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import com.rpb.reservation.workforce.calendar.*;
import com.rpb.reservation.workforce.documents.*;
import com.rpb.reservation.workforce.leave.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import com.rpb.reservation.workforce.workmode.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import static org.assertj.core.api.Assertions.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Independent old reader oracle for unchanged facts; new evidence and invalid-approval preview semantics are asserted separately. */
class WorkforcePayrollSourceEquivalencePostgresTest extends WorkforcePayrollPostgresTest {
 final YearMonth month=YearMonth.of(2026,10);
 WorkforceJson json(){return new WorkforceJson(new ObjectMapper().findAndRegisterModules());}
 PayrollSources.Capture equivalent(WorkModeResolver resolver,List<LineInput> inputs){
  var json=json();
  // The legacy oracle models valid existing inputs; install them through real approval first.
  for(var input:inputs){var requests=new com.rpb.reservation.workforce.payroll.governance.PayChangeRepository(jdbc,json);var current=requests.monthlyBaseline(tenant,company,input.employeeId(),month);if(!com.rpb.reservation.workforce.payroll.governance.EffectivePayGovernance.inputEconomic(current).equals(com.rpb.reservation.workforce.payroll.governance.EffectivePayGovernance.inputEconomic(input)))approveTest(new com.rpb.reservation.workforce.payroll.governance.PayChangeDtos.Write(0L,company,input.employeeId(),null,null,null,List.of(new com.rpb.reservation.workforce.payroll.governance.PayChangeDtos.MonthWrite(month.toString(),new InputWrite(current.version(),input.workingDays(),input.payableDays(),input.unitsReviewed(),input.specialDaysReviewed(),input.items(),input.note(),input.cdacReview()))),null,"Oracle fixture inputs"));}
  var approvalRepository=new ApprovalRepository(jdbc,json);
  var modes=resolver==null?null:new WorkModeClock(new WorkModeRepository(jdbc,json),resolver,time);
  var approvalSources=new ApprovalSources(attendance,new LeaveRepository(jdbc),new CalendarRepository(jdbc),json,time,modes);
  var oldSources=new LegacyPayrollSources(payRepository,configs,repository,schedules,approvalRepository,approvalSources,json,resolver);
  var newSources=new PayrollSources(payRepository,configs,repository,schedules,approvalRepository,approvalSources,json,resolver);
  var expected=oldSources.capture(tenant,company,month,inputs);var actual=newSources.capture(tenant,company,month,inputs);
  assertThat(actual.company()).isEqualTo(expected.company());assertThat(actual.stores()).isEqualTo(expected.stores());assertThat(actual.statutoryPolicy()).isEqualTo(expected.statutoryPolicy());assertThat(actual.lines()).hasSameSizeAs(expected.lines());
  for(int n=0;n<actual.lines().size();n++){
   var a=actual.lines().get(n);var e=expected.lines().get(n);assertThat(a.employee()).isEqualTo(e.employee());assertThat(a.profile()).isEqualTo(e.profile());assertThat(a.issues()).isEqualTo(e.issues());assertThat(a.specialDays()).isEqualTo(e.specialDays());assertThat(a.sources()).hasSameSizeAs(e.sources());
   boolean allCurrent=true;
   for(int r=0;r<a.sources().size();r++){
    var x=a.sources().get(r);var y=e.sources().get(r);assertThat(x.evidence()).isNotNull();assertThat(x.evidence().actualSeconds()).isEqualTo(y.workSeconds());
    assertThat(new SourceRef(x.sourceId(),x.storeId(),x.workDate(),x.sourceVersion(),x.sourceFingerprint(),y.workSeconds(),y.paidLeaveSeconds(),y.unpaidLeaveSeconds(),x.payable())).as("unchanged source identity/order/fingerprint and current actual facts").isEqualTo(y);
    if(x.evidence().approvalState().equals("current")){assertThat(x.workSeconds()).isEqualTo(y.workSeconds());assertThat(x.paidLeaveSeconds()).isEqualTo(y.paidLeaveSeconds());assertThat(x.unpaidLeaveSeconds()).isEqualTo(y.unpaidLeaveSeconds());assertThat(x.evidence().approvedSeconds()).isEqualTo(y.workSeconds());}
    else {allCurrent=false;assertThat(x.evidence().approvedSeconds()).isNull();assertThat(x.workSeconds()).isZero();assertThat(x.paidLeaveSeconds()).isZero();assertThat(x.unpaidLeaveSeconds()).isZero();}
   }
   if(allCurrent||a.profile()==null||!PayrollGeneration.automatic(a.profile().rule()))assertThat(a.input()).isEqualTo(e.input());
   else {assertThat(a.input().version()).isEqualTo(e.input().version());assertThat(a.input().items()).isEqualTo(e.input().items());assertThat(a.input().note()).isEqualTo(e.input().note());assertThat(a.input().workingDays()).isEqualTo(e.input().workingDays());assertThat(new java.math.BigDecimal(a.input().payableDays())).isLessThanOrEqualTo(new java.math.BigDecimal(e.input().payableDays()));}
  }
  if(actual.lines().stream().flatMap(l->l.sources().stream()).allMatch(r->r.evidence().approvalState().equals("current"))){
   var oldResult=new LegacyPayrollCalculationService(time).calculate(expected,month);var newResult=new PayrollCalculationService(time).calculate(actual,month);
   for(int n=0;n<newResult.lines().size();n++){var a=newResult.lines().get(n);var e=oldResult.lines().get(n);assertThat(List.of(a.basicPay(),a.overtimePay(),a.allowances(),a.reimbursements(),a.deductions(),a.grossPay(),a.employeeCpf(),a.employerCpf(),a.sdl(),a.employerCharges(),a.netPay())).as("valid approved monetary facts remain exactly equal to legacy").isEqualTo(List.of(e.basicPay(),e.overtimePay(),e.allowances(),e.reimbursements(),e.deductions(),e.grossPay(),e.employeeCpf(),e.employerCpf(),e.sdl(),e.employerCharges(),e.netPay()));}
  }
  assertThat(newSources.capture(tenant,company,month,inputs)).as("new evidence and fingerprints are deterministic").isEqualTo(actual);
  return actual;
 }
 void plan(Rule rule,CpfProfile cpf){
  var p=configs.findProfile(tenant,profile).orElseThrow();
  approvedProfile(admin,store,p.id(),new ProfileWrite(p.version(),p.companyId(),p.employeeId(),p.effectiveFrom(),p.templateId(),rule,cpf,p.payment(),p.cdac()));
 }
 Schedule scheduled(UUID shop,LocalDate date,String name){
  time.at(date.atStartOfDay(ZoneId.of("Asia/Singapore")).toInstant().toString());
  var shift=shifts.save(admin,shop,UUID.randomUUID(),new ShiftWrite(0L,name,"09:00","17:00",false,"none",0,0,0,0,false,true));
  return roster.save(admin,shop,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift.id(),date,"scheduled"));
 }
 void leave(UUID shop,Schedule schedule,boolean paid,String status,int startHour){
  UUID type=UUID.randomUUID();jdbc.update("insert into workforce_leave_types(tenant_id,id,store_id,name,paid,enabled,version) values(?,?,?,?,?,true,1)",tenant,type,shop,paid?"Paid":"Unpaid",paid);
  var from=schedule.startAt().plusSeconds(startHour*3600L);
  jdbc.update("insert into workforce_leaves(id,tenant_id,store_id,schedule_id,employee_id,type_id,type_name,paid,start_at,end_at,reason,status,applicant_id,version) values(?,?,?,?,?,?,?,?,?,?,'Synthetic leave',?,?,1)",UUID.randomUUID(),tenant,shop,schedule.id(),employee,type,paid?"Paid":"Unpaid",paid,Timestamp.from(from),Timestamp.from(from.plusSeconds(3600)),status,employeeAccount);
 }
 @Test void complexMonthlySourcesMatchAcrossCorrectionAndPrEvidenceTransitions(){
  var worked=completed();var cancelled=scheduled(store,LocalDate.of(2026,10,29),"Cancelled source");
  roster.save(admin,store,cancelled.id(),new ScheduleWrite(1L,employee,cancelled.shiftId(),cancelled.workDate(),"cancelled"));
  var leaveDay=scheduled(sibling,LocalDate.of(2026,10,30),"Mixed leave source");
  leave(sibling,leaveDay,true,"approved",0);leave(sibling,leaveDay,false,"cancel_requested",2);leave(sibling,leaveDay,true,"rejected",4);
  new CalendarRepository(jdbc).save(tenant,store,new CalendarDtos.CalendarEvent(UUID.randomUUID(),worked.workDate(),"public_holiday","Special",true,1));
  var docs=proxy(new EmployeeDocumentService(access,repository,new DocumentRepository(jdbc),new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules())));
  var type=docs.types(admin,store).getFirst().id();var prDate=LocalDate.of(2025,10,1);
  var document=docs.save(admin,store,employee,UUID.randomUUID(),new DocumentDtos.Write(0L,type,"S0000000A",LocalDate.of(2030,1,1),"Synthetic",false,"Synthetic Worker",prDate.minusDays(10),prDate));
  plan(new Rule("monthly","3000","8","44",true,"1.5",List.of()),new CpfProfile("spr",LocalDate.of(1990,1,1),prDate,"GG","",document.id()));
  var inputs=List.of(new LineInput(employee,7,"22","20",true,true,List.of(new Item("One off","ow_additional","75")),"Reviewed"));
  time.at("2026-11-02T01:00:00Z");
  var pending=corrections.submit(worker,store,UUID.randomUUID(),new CorrectionWrite(worked.id(),2L,worked.startAt(),worked.endAt().minusSeconds(1800),List.of(),false,"Pending correction"));
  var first=equivalent(null,inputs);assertThat(first.stores()).contains(store,sibling);assertThat(first.lines().getFirst().specialDays()).isTrue();assertThat(first.lines().getFirst().issues()).contains("SOURCE_PENDING","APPROVAL_STALE");
  assertThat(first.lines().getFirst().sources()).extracting(SourceRef::sourceId).doesNotContain(cancelled.id());
  assertThat(first.lines().getFirst().sources()).filteredOn(s->s.sourceId().equals(leaveDay.id())).singleElement().satisfies(s->{assertThat(s.evidence().approvalState()).isEqualTo("missing");assertThat(s.evidence().approvedSeconds()).isNull();assertThat(s.paidLeaveSeconds()).isZero();assertThat(s.unpaidLeaveSeconds()).isZero();});
  corrections.decide(admin,store,pending.id(),new DecisionWrite(1L,"rejected","Keep original"));equivalent(null,inputs);
  var accepted=corrections.submit(worker,store,UUID.randomUUID(),new CorrectionWrite(worked.id(),2L,worked.startAt(),worked.endAt().minusSeconds(1800),List.of(),false,"Accepted correction"));
  corrections.decide(admin,store,accepted.id(),new DecisionWrite(1L,"approved","Corrected"));var corrected=equivalent(null,inputs);assertThat(corrected.fingerprint()).isNotEqualTo(first.fingerprint());
  jdbc.update("update workforce_employee_documents set pr_effective_on=? where tenant_id=? and id=?",prDate.minusYears(1),tenant,document.id());
  assertThat(equivalent(null,inputs).lines().getFirst().issues()).contains("CPF_DOCUMENT_REVIEW_REQUIRED");
  jdbc.update("update workforce_employee_documents set deleted_at=now() where tenant_id=? and id=?",tenant,document.id());equivalent(null,inputs);
  jdbc.update("update stores set currency='USD' where tenant_id=? and id=?",tenant,sibling);
  assertThat(equivalent(null,inputs).lines().getFirst().issues()).contains("UNSUPPORTED_STORE");
 }
 @Test void manualAutomaticAndFixedPlansMatchWithCrossMonthOvertimeContext(){
  // Backdate this synthetic employee's entire company/store membership consistently.
  jdbc.update("update workforce_employees set hire_date='2026-09-01',company_effective_date='2026-09-01' where tenant_id=? and id=?",tenant,employee);
  jdbc.update("update workforce_company_history set effective_date='2026-09-01' where tenant_id=? and employee_id=?",tenant,employee);
  jdbc.update("update workforce_work_stores set started_on='2026-09-01' where tenant_id=? and employee_id=?",tenant,employee);
  jdbc.update("update workforce_pay_profiles set effective_from='2026-09-01',snapshot=jsonb_set(snapshot,'{effectiveFrom}','\"2026-09-01\"'::jsonb) where tenant_id=? and id=?",tenant,profile);
  var context=scheduled(store,LocalDate.of(2026,9,30),"Prior month context");var payable=scheduled(sibling,LocalDate.of(2026,10,1),"Payable month");
  attendance.save(tenant,new Session(UUID.randomUUID(),context.id(),employee,store,context.startAt(),context.endAt(),null,List.of(),false,1));
  attendance.save(tenant,new Session(UUID.randomUUID(),payable.id(),employee,sibling,payable.startAt(),payable.endAt(),null,List.of(),false,1));
  time.at("2026-11-02T01:00:00Z");
  var cpf=new CpfProfile("foreign",null,null,"GG","");var manual=new Rule("monthly","3000","8","44",true,"1.5",List.of());plan(manual,cpf);
  var inputs=List.of(new LineInput(employee,3,"22","21",true,false,List.of(),"Manual"));
  var first=equivalent(null,inputs);assertThat(first.lines().getFirst().sources()).extracting(SourceRef::sourceId).containsExactly(context.id(),payable.id());
  assertThat(first.lines().getFirst().sources()).extracting(SourceRef::payable).containsExactly(false,true);
  for(var schedule:List.of(context,payable)){UUID shop=schedule.id().equals(context.id())?store:sibling;var row=approvals.detail(admin,shop,schedule.id()).row();approvals.approve(admin,shop,schedule.id(),new ApprovalDtos.ApprovalWrite(0L,row.sourceFingerprint(),"Valid old/new monetary comparison"));}
  var approved=equivalent(null,inputs);assertThat(approved.lines().getFirst().sources()).allSatisfy(r->assertThat(r.evidence().approvalState()).isEqualTo("current"));
  plan(new Rule("monthly","3000","8","44",true,"1.5",List.of(),new Generation("attendance",List.of(1,2,3,4,5))),cpf);
  var auto=equivalent(null,inputs);assertThat(auto.lines().getFirst().issues()).contains("AUTO_SCHEDULE_MISSING");
  plan(new Rule("monthly","3000","8","44",false,"1.5",List.of(),new Generation("fixed",List.of(1,2,3,4,5))),cpf);
  var fixed=equivalent(null,inputs);assertThat(fixed.lines().getFirst().sources()).isEmpty();assertThat(fixed.lines().getFirst().input().workingDays()).isEqualTo("1");
 }
 @Test void workModeSnapshotsAndUnpunchedDatedAssignmentsMatchWithoutGeometryDependencies(){
  var worked=completed();var home=scheduled(sibling,LocalDate.of(2026,10,29),"Home without punch");var free=scheduled(store,LocalDate.of(2026,10,30),"Free without punch");
  var modes=new WorkModeRepository(jdbc,json());var resolver=new WorkModeResolver(modes);
  var onsiteAssignment=new Assignment(UUID.randomUUID(),employee,LocalDate.of(2026,10,1),"onsite","personal",null,0,1);
  modes.insert(tenant,onsiteAssignment);modes.insert(tenant,new Assignment(UUID.randomUUID(),employee,home.workDate(),"home","personal",null,0,2));modes.insert(tenant,new Assignment(UUID.randomUUID(),employee,free.workDate(),"free","personal",null,0,3));modes.insert(tenant,new Assignment(UUID.randomUUID(),employee,LocalDate.of(2026,11,1),"field","personal",null,0,4));
  modes.site(tenant,store,new Site(new Point("Current site",1d,2d,100d),4));
  var addressId=UUID.randomUUID();modes.insertHome(tenant,new HomeAddress(addressId,employee,new Point("Home",1d,2d,50d),LocalDate.of(2026,10,1),"pending",1,employeeAccount,null,null,time.instant()));
  modes.decide(tenant,addressId,adminId,new HomeDecision(1L,"approved","Approved"));
  var session=attendance.bySchedule(tenant,worked.id()).orElseThrow();var historical=new ClockPolicy("onsite",onsiteAssignment.id(),1,new Point("Historical site",3d,4d,60d),null,0,2L);
  attendance.save(tenant,new Session(session.id(),session.scheduleId(),session.employeeId(),session.storeId(),session.inAt(),session.outAt(),session.breakStartedAt(),session.breaks(),session.overrideBreak(),session.version()+1,session.breakStartedRuleId(),historical));
  time.at("2026-11-02T01:00:00Z");var first=equivalent(resolver,List.of());assertThat(first.lines().getFirst().issues()).contains("WORK_MODE_PAYROLL_MISMATCH");
  var approvalSources=new ApprovalSources(attendance,new LeaveRepository(jdbc),new CalendarRepository(jdbc),json(),time,new WorkModeClock(modes,resolver,time));
  var bulk=approvalSources.readAll(tenant,sibling,List.of(home));assertThat(bulk.get(home.id())).isEqualTo(approvalSources.read(tenant,sibling,home));assertThat(bulk.get(home.id()).clockPolicy().point()).isNull();
  modes.site(tenant,store,new Site(new Point("Moved site",10d,20d,150d),5));jdbc.update("update workforce_home_addresses set point=?::jsonb where tenant_id=? and id=?",json().write(new Point("Moved home",5d,6d,75d)),tenant,addressId);
  assertThat(equivalent(resolver,List.of())).as("unpunched geometry and current site do not rewrite historical policy").isEqualTo(first);
 }
}
