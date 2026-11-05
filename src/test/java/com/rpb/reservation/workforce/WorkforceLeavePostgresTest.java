package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.attendance.*;
import com.rpb.reservation.workforce.leave.*;
import com.rpb.reservation.workforce.calendar.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import static com.rpb.reservation.workforce.leave.LeaveDtos.*;
import static com.rpb.reservation.workforce.calendar.CalendarDtos.*;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkforceLeavePostgresTest extends WorkforceAttendancePostgresTest {
    LeaveTypeService types; LeaveService leaves; CalendarService calendar;
    LeaveRepository leaveRepository;CalendarRepository calendarRepository;
    @BeforeEach void leaveSetup(){
        var audit=new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules());
        var attendanceAccess=new AttendanceAccess(access,repository,scheduling);
        var typeRepository=new LeaveTypeRepository(jdbc);leaveRepository=new LeaveRepository(jdbc);calendarRepository=new CalendarRepository(jdbc);
        types=proxy(new LeaveTypeService(access,scheduling,typeRepository,audit));
        leaves=proxy(new LeaveService(access,attendanceAccess,repository,scheduling,typeRepository,leaveRepository,audit,time));
        calendar=proxy(new CalendarService(access,attendanceAccess,scheduling,calendarRepository,audit));
    }
    LeaveType type(boolean paid){return types.save(admin,store,UUID.randomUUID(),new LeaveTypeWrite(0L,paid?"Annual":"Unpaid",paid,true));}
    @Test void fullLeaveSnapshotAndRetrySurviveLaterPolicyChange(){
        var s=schedule(store,shift(store,"none"),DATE);var t=type(true);var id=UUID.randomUUID();
        var cmd=new LeaveWrite(s.id(),t.id(),s.startAt(),s.endAt(),"Annual leave");var first=leaves.submit(worker,store,id,cmd);
        types.save(admin,store,t.id(),new LeaveTypeWrite(1L,"Changed",false,false));
        assertThat(leaves.submit(worker,store,id,cmd)).isEqualTo(first);
        assertThat(leaves.decide(admin,store,id,new DecisionWrite(1L,"approved","Verified")).paid()).isTrue();
        assertThat(leaveRepository.forSchedule(tenant,s.id()).getFirst().typeName()).isEqualTo("Annual");
        assertThatThrownBy(()->leaves.submit(worker,store,id,new LeaveWrite(s.id(),t.id(),s.startAt(),s.endAt(),"Other"))).isInstanceOf(WorkforceException.class);
    }
    @Test void partialLeaveApprovalOverlapAndPendingUniquenessAreSerialized(){
        var s=schedule(store,shift(store,"none"),DATE);var t=type(false);
        var a=leaves.submit(worker,store,UUID.randomUUID(),new LeaveWrite(s.id(),t.id(),s.startAt(),s.startAt().plusSeconds(3600),"First"));
        assertThatThrownBy(()->leaves.submit(worker,store,UUID.randomUUID(),new LeaveWrite(s.id(),t.id(),s.startAt().plusSeconds(7200),s.endAt(),"Second"))).isInstanceOf(WorkforceException.class);
        leaves.decide(admin,store,a.id(),new DecisionWrite(1L,"approved","Checked"));
        var b=leaves.submit(worker,store,UUID.randomUUID(),new LeaveWrite(s.id(),t.id(),s.startAt().plusSeconds(1800),s.startAt().plusSeconds(7200),"Overlap"));
        assertThatThrownBy(()->leaves.decide(admin,store,b.id(),new DecisionWrite(1L,"approved","Check"))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_LEAVE_OVERLAP");
        assertThat(leaveRepository.forSchedule(tenant,s.id()).stream().filter(l->l.id().equals(b.id())).findFirst().orElseThrow().status()).isEqualTo("pending");
    }
    @Test void approvedLeaveCancellationRequiresAnotherManagerAndKeepsHistory(){
        var s=schedule(store,shift(store,"none"),DATE);var t=type(true);
        var a=leaves.submit(worker,store,UUID.randomUUID(),new LeaveWrite(s.id(),t.id(),s.startAt(),s.endAt(),"Day off"));
        assertThatThrownBy(()->leaves.decide(worker,store,a.id(),new DecisionWrite(1L,"approved","Self"))).isInstanceOf(WorkforceException.class);
        var approved=leaves.decide(admin,store,a.id(),new DecisionWrite(1L,"approved","Checked"));
        assertThat(leaves.decide(admin,store,a.id(),new DecisionWrite(1L,"approved","Checked"))).isEqualTo(approved);
        var requested=leaves.decide(worker,store,a.id(),new DecisionWrite(2L,"cancel_requested","Return to work"));assertThat(requested.status()).isEqualTo("cancel_requested");
        assertThatThrownBy(()->leaves.decide(worker,store,a.id(),new DecisionWrite(3L,"cancelled","Self"))).isInstanceOf(WorkforceException.class);
        var rejected=leaves.decide(admin,store,a.id(),new DecisionWrite(3L,"cancel_rejected","Leave still required"));assertThat(rejected.status()).isEqualTo("approved");
        assertThat(leaves.decide(admin,store,a.id(),new DecisionWrite(3L,"cancel_rejected","Leave still required"))).isEqualTo(rejected);
        leaves.decide(worker,store,a.id(),new DecisionWrite(4L,"cancel_requested","Resubmit"));
        assertThat(leaves.decide(admin,store,a.id(),new DecisionWrite(5L,"cancelled","Agreed")).status()).isEqualTo("cancelled");
        verify(logs,atLeast(6)).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
    }
    @Test void applicantCanWithdrawPendingAndApprovedLeaveBlocksScheduleChanges(){
        var s=schedule(store,shift(store,"none"),DATE);var t=type(true);
        var a=leaves.submit(worker,store,UUID.randomUUID(),new LeaveWrite(s.id(),t.id(),s.startAt(),s.endAt(),"Day off"));
        assertThatThrownBy(()->roster.save(admin,store,s.id(),new ScheduleWrite(1L,employee,s.shiftId(),DATE,"cancelled"))).isInstanceOf(WorkforceException.class);
        assertThat(leaves.decide(worker,store,a.id(),new DecisionWrite(1L,"withdrawn","")).status()).isEqualTo("withdrawn");
        assertThat(roster.save(admin,store,s.id(),new ScheduleWrite(1L,employee,s.shiftId(),DATE,"cancelled")).status()).isEqualTo("cancelled");
    }
    @Test void leaveScopesDoNotExposeOtherEmployeesAndRejectWrongStore(){
        var s=schedule(store,shift(store,"none"),DATE);var t=type(true);var a=leaves.submit(worker,store,UUID.randomUUID(),new LeaveWrite(s.id(),t.id(),s.startAt(),s.endAt(),"Private"));
        UUID other=account("store_staff",Set.of(store),Set.of("workforce.view"));
        var observer=new CurrentActor(tenant,other,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));
        employees.save(admin,store,UUID.randomUUID(),command(0,List.of(store),company,other,null));
        assertThat(leaves.list(observer,store,DATE,DATE)).isEmpty();
        assertThat(calendar.list(observer,store,DATE,DATE).days().getFirst().schedules()).isZero();
        assertThatThrownBy(()->leaves.decide(admin,sibling,a.id(),new DecisionWrite(1L,"approved","Wrong store"))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->types.save(worker,store,UUID.randomUUID(),new LeaveTypeWrite(0L,"Self",true,true))).isInstanceOf(WorkforceException.class);
    }
    @Test void crossMidnightLeaveAndMicrosecondRetryAreSupported(){
        var n=shifts.save(admin,store,UUID.randomUUID(),new ShiftWrite(0L,"Night","22:00","06:00",true,"none",0,0,0,0,false,true));var s=schedule(store,n,DATE);var t=type(false);
        var c=new LeaveWrite(s.id(),t.id(),s.startAt().plusNanos(123456789),s.endAt().minusSeconds(1),"Night partial");var id=UUID.randomUUID();var first=leaves.submit(worker,store,id,c);
        assertThat(leaves.submit(worker,store,id,c)).isEqualTo(first);
        assertThat(leaves.list(worker,store,DATE,DATE)).hasSize(1);
        assertThatThrownBy(()->leaves.submit(worker,store,UUID.randomUUID(),new LeaveWrite(s.id(),t.id(),s.startAt().minusSeconds(1),s.endAt(),"Outside"))).isInstanceOf(WorkforceException.class);
    }
    @Test void calendarCountsOnlyOwnSessionsAndEventsDoNotRemoveSchedules(){
        var s=schedule(store,shift(store,"none"),DATE);punch(s,"IN");
        var event=calendar.save(admin,store,UUID.randomUUID(),new CalendarWrite(0L,DATE,"public_holiday","Public holiday",true));
        assertThat(calendar.list(worker,store,DATE,DATE).days().getFirst().schedules()).isEqualTo(1);
        assertThat(calendar.list(worker,store,DATE,DATE).days().getFirst().attended()).isEqualTo(1);
        assertThat(calendar.list(worker,store,DATE,DATE).items()).containsExactly(event);
        assertThat(scheduling.find(tenant,s.id()).orElseThrow().status()).isEqualTo("scheduled");
        assertThatThrownBy(()->calendar.save(worker,store,UUID.randomUUID(),new CalendarWrite(0L,DATE,"store_rest","Rest",true))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->calendar.save(admin,store,UUID.randomUUID(),new CalendarWrite(0L,DATE,"public_holiday","Duplicate",true))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var disabled=calendar.save(admin,store,event.id(),new CalendarWrite(1L,DATE,"public_holiday","Public holiday",false));assertThat(disabled.version()).isEqualTo(2);
        assertThatThrownBy(()->calendar.save(admin,store,event.id(),new CalendarWrite(1L,DATE,"public_holiday","Stale",true))).isInstanceOf(WorkforceException.class);
    }
    @Test void leaveAndCalendarWritesRollbackWhenAuditFails(){
        var s=schedule(store,shift(store,"none"),DATE);var t=type(true);UUID id=UUID.randomUUID(),event=UUID.randomUUID();
        when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenThrow(new IllegalStateException("audit"));
        assertThatThrownBy(()->leaves.submit(worker,store,id,new LeaveWrite(s.id(),t.id(),s.startAt(),s.endAt(),"Day off"))).isInstanceOf(IllegalStateException.class);
        assertThat(leaveRepository.forSchedule(tenant,s.id())).isEmpty();
        assertThatThrownBy(()->calendar.save(admin,store,event,new CalendarWrite(0L,DATE,"store_rest","Rest",true))).isInstanceOf(IllegalStateException.class);
        assertThat(calendarRepository.list(tenant,store,DATE,DATE)).isEmpty();
    }
    @Test void concurrentDecisionsDoNotLoseVersionOrApproveTwice()throws Exception{
        var s=schedule(store,shift(store,"none"),DATE);var t=type(true);
        var l=leaves.submit(worker,store,UUID.randomUUID(),new LeaveWrite(s.id(),t.id(),s.startAt(),s.endAt(),"Concurrency"));
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            var calls=new ArrayList<Future<Boolean>>();
            for(var status:List.of("approved","rejected"))calls.add(pool.submit(()->{start.await();try{leaves.decide(admin,store,l.id(),new DecisionWrite(1L,status,"Concurrent"));return true;}catch(WorkforceException e){return false;}}));
            start.countDown();int accepted=0;for(var call:calls)if(call.get(20,TimeUnit.SECONDS))accepted++;
            assertThat(accepted).isEqualTo(1);assertThat(leaveRepository.find(tenant,l.id()).orElseThrow().version()).isEqualTo(2);
        }finally{pool.shutdownNow();}
    }

}
