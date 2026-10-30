package com.rpb.reservation.workforce;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.attendance.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Inherits the owned pointer-PG schema and phase-one regression scenarios. */
class WorkforceAttendancePostgresTest extends WorkforcePostgresTest {
    SchedulingRepository scheduling;AttendanceRepository attendance;
    ShiftService shifts;ScheduleService roster;ClockService punches;CorrectionService corrections;AttendanceQueryService query;
    UUID employee,employeeAccount;CurrentActor worker;MutableClock time;
    static final LocalDate DATE=LocalDate.of(2026,10,28);
    static class MutableClock extends Clock {Instant now=Instant.parse("2026-10-28T01:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}public Instant instant(){return now;}void at(String s){now=Instant.parse(s);}}
    @BeforeEach void attendanceSetup(){
        var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());var audit=new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules());time=new MutableClock();
        scheduling=new SchedulingRepository(jdbc,json);attendance=new AttendanceRepository(jdbc,json);
        var scope=new AttendanceAccess(access,repository,scheduling);
        shifts=proxy(new ShiftService(access,scheduling,audit));roster=proxy(new ScheduleService(access,scheduling,repository,audit,time));
        punches=proxy(new ClockService(scope,repository,scheduling,attendance,audit,time));
        corrections=proxy(new CorrectionService(access,scope,repository,scheduling,attendance,audit,time));query=new AttendanceQueryService(access,scope,scheduling,attendance,time);
        employeeAccount=account("store_staff",Set.of(store,sibling),Set.of("workforce.view"));employee=UUID.randomUUID();
        var seed=command(0,List.of(store,sibling),company,employeeAccount,null);
        employees.save(admin,store,employee,new WorkforceDtos.EmployeeWrite(seed.expectedVersion(),"ATT001",seed.name(),seed.phone(),seed.email(),seed.jobTitle(),seed.hireDate(),seed.leaveDate(),seed.status(),seed.companyId(),seed.companyEffectiveDate(),seed.mainStoreId(),seed.storeIds(),seed.accountId(),null,null));
        worker=new CurrentActor(tenant,employeeAccount,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store,sibling));
        clearInvocations(logs);
    }
    Shift shift(UUID shop,String mode){return shifts.save(admin,shop,UUID.randomUUID(),new ShiftWrite(0L,"Day","09:00","17:00",false,mode,30,360,120,180,false,true));}
    Schedule schedule(UUID shop,Shift shift,LocalDate day){return roster.save(admin,shop,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift.id(),day,"scheduled"));}
    Session punch(Schedule s,String action){return punches.punch(worker,store,UUID.randomUUID(),new ClockWrite(s.id(),action));}
    @Test void disabledEmployeeCannotStartOrReceiveNewSchedulesButCanFinishExistingSession(){
        var template=shift(store,"none");var first=schedule(store,template,DATE);var next=schedule(store,template,DATE.plusDays(1));punch(first,"IN");
        jdbc.update("update workforce_employees set enabled=false,version=version+1 where tenant_id=? and id=?",tenant,employee);
        assertThat(scheduling.assigned(tenant,employee,store,DATE)).isFalse();
        assertThatThrownBy(()->roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,template.id(),DATE.plusDays(2),"scheduled"))).isInstanceOf(WorkforceException.class);
        time.at("2026-10-28T09:00:00Z");punch(first,"OUT");assertThat(query.detail(worker,store,first.id()).attendance().session().outAt()).isNotNull();
        time.at("2026-10-29T01:00:00Z");assertThatThrownBy(()->punch(next,"IN")).isInstanceOf(WorkforceException.class);
    }
    @Test void snapshotAndRawEventsSurviveTemplateEditAndApprovedCorrection(){
        var template=shift(store,"duration");var s=schedule(store,template,DATE);punch(s,"IN");time.at("2026-10-28T09:00:00Z");punch(s,"OUT");
        shifts.save(admin,store,template.id(),new ShiftWrite(1L,"Changed","09:00","17:00",false,"none",0,0,0,0,false,true));
        var detail=query.detail(worker,store,s.id());assertThat(detail.attendance().workSeconds()).isEqualTo(450*60L);assertThat(detail.events()).hasSize(2);
        var c=corrections.submit(worker,store,UUID.randomUUID(),new CorrectionWrite(s.id(),2L,s.startAt(),s.endAt(),List.of(),true,"Actually worked during break"));
        assertThatThrownBy(()->corrections.decide(worker,store,c.id(),new DecisionWrite(1L,"approved","self"))).isInstanceOf(WorkforceException.class);
        corrections.decide(admin,store,c.id(),new DecisionWrite(1L,"approved","Checked"));
        detail=query.detail(worker,store,s.id());assertThat(detail.attendance().workSeconds()).isEqualTo(480*60L);assertThat(detail.events()).hasSize(2);assertThat(detail.corrections().getFirst().status()).isEqualTo("approved");
    }
    @Test void manualStateMachineIsIdempotentAndCannotDoubleDeduct(){
        var s=schedule(store,shift(store,"manual"),DATE);UUID key=UUID.randomUUID();
        var first=punches.punch(worker,store,key,new ClockWrite(s.id(),"IN"));assertThat(punches.punch(worker,store,key,new ClockWrite(s.id(),"IN"))).isEqualTo(first);
        assertThatThrownBy(()->punches.punch(worker,store,key,new ClockWrite(s.id(),"OUT"))).isInstanceOf(WorkforceException.class);
        time.at("2026-10-28T04:00:00Z");punch(s,"BREAK_START");assertThatThrownBy(()->punch(s,"OUT")).isInstanceOf(WorkforceException.class);
        time.at("2026-10-28T04:30:00Z");punch(s,"BREAK_END");time.at("2026-10-28T09:00:00Z");punch(s,"OUT");
        assertThat(query.detail(worker,store,s.id()).attendance().workSeconds()).isEqualTo(450*60L);
    }
    @Test void crossStoreScheduleOverlapRejectedButAdjacentShiftsAllowed(){
        var s=schedule(store,shift(store,"none"),DATE);var template=shift(sibling,"none");
        assertThatThrownBy(()->schedule(sibling,template,DATE)).isInstanceOf(WorkforceException.class);
        var late=shifts.save(admin,sibling,UUID.randomUUID(),new ShiftWrite(0L,"Evening","17:00","21:00",false,"none",0,0,0,0,false,true));
        assertThat(schedule(sibling,late,DATE).startAt()).isEqualTo(s.endAt());
    }
    @Test void concurrentDuplicateInCreatesOneSessionAndOneRawEvent()throws Exception{
        var s=schedule(store,shift(store,"none"),DATE);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var calls=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)calls.add(pool.submit(()->{start.await();try{punch(s,"IN");return true;}catch(WorkforceException e){return false;}}));start.countDown();int count=0;for(var c:calls)if(c.get(20,TimeUnit.SECONDS))count++;assertThat(count).isEqualTo(1);assertThat(attendance.events(tenant,s.id())).hasSize(1);}finally{pool.shutdownNow();}
    }
    @Test void incompleteSessionNotCountedAndStaleCorrectionCannotOverwriteLaterPunch(){
        var s=schedule(store,shift(store,"none"),DATE);punch(s,"IN");time.at("2026-10-28T05:00:00Z");
        assertThat(query.detail(worker,store,s.id()).attendance().workSeconds()).isNull();
        var c=corrections.submit(worker,store,UUID.randomUUID(),new CorrectionWrite(s.id(),1L,s.startAt(),time.instant(),List.of(),false,"Missing out"));
        time.at("2026-10-28T09:00:00Z");punch(s,"OUT");
        assertThatThrownBy(()->corrections.decide(admin,store,c.id(),new DecisionWrite(1L,"approved","check"))).isInstanceOf(WorkforceException.class);
        assertThat(attendance.correction(tenant,c.id()).orElseThrow().status()).isEqualTo("pending");
    }
    @Test void whollyMissingAttendanceCanBeCorrectedAndCannotChangeAnotherStore(){
        var s=schedule(store,shift(store,"none"),DATE);time.at("2026-10-28T10:00:00Z");var id=UUID.randomUUID();
        var c=corrections.submit(worker,store,id,new CorrectionWrite(s.id(),0L,s.startAt(),s.endAt(),List.of(),false,"Forgot both"));
        corrections.decide(admin,store,c.id(),new DecisionWrite(1L,"approved","verified"));
        assertThat(query.detail(worker,store,s.id()).attendance().workSeconds()).isEqualTo(8*3600L);
        assertThatThrownBy(()->query.detail(admin,sibling,s.id())).isInstanceOf(WorkforceException.class);
        assertThat(query.detail(worker,store,s.id()).events()).isEmpty();
    }
    @Test void copyWeekIsAtomicOnConflictAndReplaysSameCommand(){
        var template=shift(store,"none");schedule(store,template,DATE);var monday=DATE.with(DayOfWeek.MONDAY);var target=monday.plusWeeks(1);var key=UUID.randomUUID();
        var copy=roster.copy(admin,store,key,new CopyWrite(monday,target));assertThat(copy).hasSize(1);
        assertThat(roster.copy(admin,store,key,new CopyWrite(monday,target))).isEqualTo(copy);
        assertThatThrownBy(()->roster.copy(admin,store,UUID.randomUUID(),new CopyWrite(monday,target))).isInstanceOf(WorkforceException.class);
        assertThat(scheduling.copySource(tenant,store,target)).hasSize(1);
    }
    @Test void auditFailureRollsBackPunchAndEmploymentChangeCannotMoveActiveSession(){
        var s=schedule(store,shift(store,"none"),DATE);when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenThrow(new IllegalStateException("audit"));
        assertThatThrownBy(()->punch(s,"IN")).isInstanceOf(IllegalStateException.class);assertThat(attendance.bySchedule(tenant,s.id())).isEmpty();
        reset(logs);punch(s,"IN");assertThatThrownBy(()->employees.save(admin,store,employee,command(1,List.of(store,sibling),otherCompany,employeeAccount,null))).isInstanceOf(WorkforceException.class);
    }
    @Test void otherTenantCannotOverwriteShiftByGuessingItsId(){
        var s=shift(store,"none");UUID foreign=UUID.randomUUID(),foreignStore=UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Foreign','active')",foreign,"wf-"+foreign);
        jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'Foreign','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",foreignStore,foreign,"wf-"+foreignStore);
        assertThatThrownBy(()->scheduling.saveShift(foreign,foreignStore,s)).isInstanceOf(WorkforceException.class);
        assertThat(scheduling.shift(tenant,store,s.id()).orElseThrow()).isEqualTo(s);
    }
    @Test void currentStoreRemovalBlocksFirstInOnPreviousDaysNightSchedule(){
        time.at("2026-10-27T08:00:00Z");
        var shift=shifts.save(admin,store,UUID.randomUUID(),new ShiftWrite(0L,"Night","22:00","06:00",true,"none",0,0,0,0,false,true));
        var s=schedule(store,shift,DATE.minusDays(1));
        var e=repository.find(tenant,employee).orElseThrow();
        employees.save(admin,sibling,employee,new WorkforceDtos.EmployeeWrite(e.version(),e.employeeNo(),e.name(),e.phone(),e.email(),e.jobTitle(),e.hireDate(),null,e.status(),e.companyId(),e.companyEffectiveDate(),sibling,List.of(sibling),e.accountId(),null,null));
        time.at("2026-10-27T18:00:00Z");
        assertThatThrownBy(()->punch(s,"IN")).isInstanceOf(WorkforceException.class);
    }
    @Test void correctionRequestNanosecondsAreCanonicalForIdempotentRetry(){
        var s=schedule(store,shift(store,"none"),DATE);time.at("2026-10-28T10:00:00Z");
        var c=new CorrectionWrite(s.id(),0L,Instant.parse("2026-10-28T01:00:00.123456789Z"),Instant.parse("2026-10-28T08:00:00.123456789Z"),List.of(),false,"Precision check");
        UUID id=UUID.randomUUID();var first=corrections.submit(worker,store,id,c);
        assertThat(corrections.submit(worker,store,id,c)).isEqualTo(first);
    }
    @Test void differentStoresCannotStartTwoActiveSessionsConcurrently()throws Exception{
        var a=schedule(store,shift(store,"none"),DATE);
        var late=shifts.save(admin,sibling,UUID.randomUUID(),new ShiftWrite(0L,"Late","17:00","21:00",false,"none",0,0,0,0,false,true));var b=schedule(sibling,late,DATE);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var calls=new ArrayList<Future<Boolean>>();for(var pair:List.of(Map.entry(store,a),Map.entry(sibling,b)))calls.add(pool.submit(()->{start.await();try{punches.punch(worker,pair.getKey(),UUID.randomUUID(),new ClockWrite(pair.getValue().id(),"IN"));return true;}catch(WorkforceException e){return false;}}));start.countDown();int count=0;for(var call:calls)if(call.get(20,TimeUnit.SECONDS))count++;assertThat(count).isEqualTo(1);}finally{pool.shutdownNow();}
    }
    @Test void copyFailureAfterAnEarlierInsertRollsBackTheEntireWeek(){
        var t=shift(store,"none");schedule(store,t,DATE);schedule(store,t,DATE.plusDays(1));schedule(store,t,DATE.plusDays(8));
        var source=DATE.with(DayOfWeek.MONDAY);var target=source.plusWeeks(1);
        assertThatThrownBy(()->roster.copy(admin,store,UUID.randomUUID(),new CopyWrite(source,target))).isInstanceOf(WorkforceException.class);
        assertThat(scheduling.copySource(tenant,store,target)).hasSize(1);
    }
    @Test void correctionCannotExtendIntoAnotherStoresActualAttendance(){
        var a=schedule(store,shift(store,"none"),DATE);
        var late=shifts.save(admin,sibling,UUID.randomUUID(),new ShiftWrite(0L,"Late","17:00","21:00",false,"none",0,0,0,0,false,true));var b=schedule(sibling,late,DATE);
        punch(a,"IN");time.at("2026-10-28T09:00:00Z");punch(a,"OUT");time.at("2026-10-28T09:00:01Z");punches.punch(worker,sibling,UUID.randomUUID(),new ClockWrite(b.id(),"IN"));time.at("2026-10-28T10:00:00Z");punches.punch(worker,sibling,UUID.randomUUID(),new ClockWrite(b.id(),"OUT"));
        var c=corrections.submit(worker,store,UUID.randomUUID(),new CorrectionWrite(a.id(),2L,a.startAt(),time.instant(),List.of(),false,"Longer first shift"));
        assertThatThrownBy(()->corrections.decide(admin,store,c.id(),new DecisionWrite(1L,"approved","checked"))).isInstanceOf(WorkforceException.class);
        assertThat(attendance.bySchedule(tenant,a.id()).orElseThrow().outAt()).isEqualTo(a.endAt());
    }
    @Test void applicantCanWithdrawWithoutApprovalNote(){
        var s=schedule(store,shift(store,"none"),DATE);time.at("2026-10-28T10:00:00Z");
        var c=corrections.submit(worker,store,UUID.randomUUID(),new CorrectionWrite(s.id(),0L,s.startAt(),s.endAt(),List.of(),false,"Missing"));
        assertThat(corrections.decide(worker,store,c.id(),new DecisionWrite(1L,"withdrawn","" )).status()).isEqualTo("withdrawn");
    }
    @Test void workerClockStoreChoicesAreOwnAssignedAndAuthenticatedStoresOnly(){
        var homeService=new WorkforceHomeService(access,directory,employees);var home=homeService.home(worker,store);
        assertThat(home.stores()).isEmpty();assertThat(home.clockStores()).extracting(WorkforceDtos.StoreOption::id).containsExactlyInAnyOrder(store,sibling);
        var limited=new CurrentActor(tenant,employeeAccount,"staff",worker.roles(),worker.permissions(),Set.of(store));
        assertThat(homeService.home(limited,store).clockStores()).extracting(WorkforceDtos.StoreOption::id).containsExactly(store);
        assertThat(home.accounts()).isEmpty();assertThat(home.administratorAccounts()).isEmpty();
    }

    @Test void restoringCancelledScheduleRetainsOriginalSnapshotAndChecksOverlaps(){
        var template=shift(store,"duration");var original=schedule(store,template,DATE);var cancelled=roster.save(admin,store,original.id(),new ScheduleWrite(1L,employee,template.id(),DATE,"cancelled"));
        shifts.save(admin,store,template.id(),new ShiftWrite(1L,"Changed","10:00","18:00",false,"none",0,0,0,0,false,true));
        var restored=roster.save(admin,store,original.id(),new ScheduleWrite(2L,employee,template.id(),DATE,"scheduled"));assertThat(restored.shift()).isEqualTo(original.shift());assertThat(restored.startAt()).isEqualTo(original.startAt());assertThat(restored.endAt()).isEqualTo(original.endAt());assertThat(restored.id()).isEqualTo(original.id());assertThat(restored.version()).isEqualTo(3);
        assertThatThrownBy(()->roster.save(admin,store,original.id(),new ScheduleWrite(2L,employee,template.id(),DATE,"scheduled"))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(409);
        roster.save(admin,store,original.id(),new ScheduleWrite(3L,employee,template.id(),DATE,"cancelled"));var other=schedule(sibling,shift(sibling,"none"),DATE);
        assertThatThrownBy(()->roster.save(admin,store,original.id(),new ScheduleWrite(4L,employee,template.id(),DATE,"scheduled"))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_SCHEDULE_OVERLAP");assertThat(scheduling.find(tenant,original.id()).orElseThrow().status()).isEqualTo("cancelled");
    }
    @Test void usedScheduleAndRawClockEventsCannotBeChangedByRestore(){
        var original=schedule(store,shift(store,"manual"),DATE);punch(original,"IN");var before=jdbc.queryForList("select to_jsonb(e)::text from workforce_clock_events e where tenant_id=?",String.class,tenant);var session=jdbc.queryForList("select to_jsonb(s)::text from workforce_attendance_sessions s where tenant_id=?",String.class,tenant);
        jdbc.update("update workforce_schedules set status='cancelled' where id=?",original.id());assertThatThrownBy(()->roster.save(admin,store,original.id(),new ScheduleWrite(1L,employee,original.shiftId(),DATE,"scheduled"))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_ACTIVE_ATTENDANCE");assertThat(jdbc.queryForList("select to_jsonb(e)::text from workforce_clock_events e where tenant_id=?",String.class,tenant)).isEqualTo(before);assertThat(jdbc.queryForList("select to_jsonb(s)::text from workforce_attendance_sessions s where tenant_id=?",String.class,tenant)).isEqualTo(session);
    }
    @Test void rosterShowsActiveOrLatestCancelledWithoutDeletingHistory(){
        var template=shift(store,"none");var first=schedule(store,template,DATE);roster.save(admin,store,first.id(),new ScheduleWrite(1L,employee,template.id(),DATE,"cancelled"));var second=schedule(store,template,DATE);
        assertThat(scheduling.forEmployees(tenant,store,DATE,DATE,List.of(employee))).extracting(Schedule::id).containsExactly(second.id());roster.save(admin,store,second.id(),new ScheduleWrite(1L,employee,template.id(),DATE,"cancelled"));assertThat(scheduling.forEmployees(tenant,store,DATE,DATE,List.of(employee))).extracting(Schedule::id).containsExactly(second.id());assertThat(scheduling.list(tenant,store,DATE,DATE,employee,50,0)).hasSize(2);
    }

    @Test void restoreRejectsEditsExpiredDatesDisabledTemplatesAndUnauthorizedActors(){
        var template=shift(store,"none");var original=schedule(store,template,DATE);roster.save(admin,store,original.id(),new ScheduleWrite(1L,employee,template.id(),DATE,"cancelled"));var input=new ScheduleWrite(2L,employee,template.id(),DATE,"scheduled");
        assertThatThrownBy(()->roster.save(worker,store,original.id(),input)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
        assertThatThrownBy(()->roster.save(admin,store,original.id(),new ScheduleWrite(2L,employee,template.id(),DATE.plusDays(1),"scheduled"))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(400);
        shifts.save(admin,store,template.id(),new ShiftWrite(1L,"Day","09:00","17:00",false,"none",0,0,0,0,false,false));assertThatThrownBy(()->roster.save(admin,store,original.id(),input)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(400);
        time.at("2026-10-29T01:00:00Z");assertThatThrownBy(()->roster.save(admin,store,original.id(),input)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(400);assertThat(scheduling.find(tenant,original.id()).orElseThrow().status()).isEqualTo("cancelled");
    }
    @Test void restoreRollsBackOnAuditFailureAndCannotOverlapAnExistingSameStoreSchedule(){
        var template=shift(store,"none");var original=schedule(store,template,DATE);roster.save(admin,store,original.id(),new ScheduleWrite(1L,employee,template.id(),DATE,"cancelled"));var input=new ScheduleWrite(2L,employee,template.id(),DATE,"scheduled");
        doThrow(new IllegalStateException("audit unavailable")).when(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());assertThatThrownBy(()->roster.save(admin,store,original.id(),input)).isInstanceOf(IllegalStateException.class);assertThat(scheduling.find(tenant,original.id()).orElseThrow().version()).isEqualTo(2);org.mockito.Mockito.reset(logs);
        var latest=schedule(store,template,DATE);assertThatThrownBy(()->roster.save(admin,store,original.id(),input)).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_SCHEDULE_OVERLAP");assertThat(scheduling.forEmployees(tenant,store,DATE,DATE,List.of(employee))).extracting(Schedule::id).containsExactly(latest.id());
    }

    ScheduleDeletionService deletionService(){return proxy(new ScheduleDeletionService(access,scheduling,repository,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules())));}
    @Test void unpunchedScheduleDeletionIsSoftScopedVersionedAndIdempotent(){
        var original=schedule(store,shift(store,"none"),DATE);var service=deletionService();var input=new ScheduleDeletionDtos.Request(1L);assertThatThrownBy(()->service.delete(worker,store,original.id(),input)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);assertThatThrownBy(()->service.delete(admin,sibling,original.id(),input)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
        var result=service.delete(admin,store,original.id(),input);assertThat(result.deleted()).isTrue();assertThat(service.delete(admin,store,original.id(),input)).isEqualTo(result);assertThat(scheduling.find(tenant,original.id())).isPresent();assertThat(scheduling.deleted(tenant,original.id())).isTrue();assertThat(scheduling.forEmployees(tenant,store,DATE,DATE,List.of(employee))).isEmpty();assertThat(roster.list(admin,store,DATE,DATE).items()).isEmpty();assertThatThrownBy(()->roster.save(admin,store,original.id(),new ScheduleWrite(2L,employee,original.shiftId(),DATE,"scheduled"))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(404);verify(logs,times(3)).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
        doAnswer(call->{var r=(com.rpb.reservation.appgate.application.AppGateAccessRequest)call.getArgument(0);return com.rpb.reservation.appgate.domain.AppGateDecision.deny(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission(),com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED);}).when(gate).evaluate(any());assertThatThrownBy(()->service.delete(admin,store,original.id(),input)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
    }
    @Test void deletingLatestCancelledDoesNotResurfaceOldHistoryAndAuditFailureRollsBack(){
        var template=shift(store,"none");var first=schedule(store,template,DATE);roster.save(admin,store,first.id(),new ScheduleWrite(1L,employee,template.id(),DATE,"cancelled"));var latest=schedule(store,template,DATE);roster.save(admin,store,latest.id(),new ScheduleWrite(1L,employee,template.id(),DATE,"cancelled"));var service=deletionService();var input=new ScheduleDeletionDtos.Request(2L);
        doThrow(new IllegalStateException("audit unavailable")).when(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());assertThatThrownBy(()->service.delete(admin,store,latest.id(),input)).isInstanceOf(IllegalStateException.class);assertThat(scheduling.deleted(tenant,latest.id())).isFalse();org.mockito.Mockito.reset(logs);service.delete(admin,store,latest.id(),input);assertThat(scheduling.forEmployees(tenant,store,DATE,DATE,List.of(employee))).isEmpty();assertThat(jdbc.queryForObject("select count(*) from workforce_schedules where tenant_id=?",Long.class,tenant)).isEqualTo(2);
    }
    @Test void deletionNeverChangesExistingClockEventsAndSessions(){
        var original=schedule(store,shift(store,"none"),DATE);punch(original,"IN");var events=jdbc.queryForList("select to_jsonb(e)::text from workforce_clock_events e where tenant_id=?",String.class,tenant);var sessions=jdbc.queryForList("select to_jsonb(s)::text from workforce_attendance_sessions s where tenant_id=?",String.class,tenant);
        assertThatThrownBy(()->deletionService().delete(admin,store,original.id(),new ScheduleDeletionDtos.Request(1L))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_ACTIVE_ATTENDANCE");assertThat(scheduling.deleted(tenant,original.id())).isFalse();assertThat(jdbc.queryForList("select to_jsonb(e)::text from workforce_clock_events e where tenant_id=?",String.class,tenant)).isEqualTo(events);assertThat(jdbc.queryForList("select to_jsonb(s)::text from workforce_attendance_sessions s where tenant_id=?",String.class,tenant)).isEqualTo(sessions);
    }

    @Test void concurrentDeletionAndClockInCannotLeaveADeletedPunchedSchedule()throws Exception{
        var original=schedule(store,shift(store,"none"),DATE);var service=deletionService();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var deletion=pool.submit(()->{start.await();try{service.delete(admin,store,original.id(),new ScheduleDeletionDtos.Request(1L));return true;}catch(WorkforceException blocked){return false;}});var clockIn=pool.submit(()->{start.await();try{punch(original,"IN");return true;}catch(WorkforceException blocked){return false;}});start.countDown();boolean deleted=deletion.get(10,TimeUnit.SECONDS),punched=clockIn.get(10,TimeUnit.SECONDS);assertThat(deleted).isNotEqualTo(punched);assertThat(scheduling.deleted(tenant,original.id())).isEqualTo(deleted);assertThat(jdbc.queryForObject("select count(*) from workforce_clock_events where tenant_id=?",Long.class,tenant)).isEqualTo(punched?1L:0L);
        }
    }
}
