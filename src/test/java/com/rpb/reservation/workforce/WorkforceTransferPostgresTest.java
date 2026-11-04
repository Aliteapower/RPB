package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.attendance.*;
import com.rpb.reservation.workforce.attendance.daily.*;
import com.rpb.reservation.workforce.attendancepolicy.*;
import com.rpb.reservation.workforce.approval.*;
import com.rpb.reservation.workforce.calendar.CalendarRepository;
import com.rpb.reservation.workforce.leave.LeaveRepository;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.workmode.WorkModeRepository;
import java.time.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.scheduling.transfer.ScheduleTransferService;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkforceTransferPostgresTest extends WorkforceAttendancePostgresTest {
    private void removeDestinationMembership(){
        jdbc.update("delete from workforce_work_stores where tenant_id=? and employee_id=? and store_id=?",tenant,employee,sibling);
        worker=new com.rpb.reservation.walkin.api.CurrentActor(tenant,employeeAccount,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));
        when(gate.evaluateCommercial("workforce",tenant,sibling)).thenReturn(AppGateDecision.allow("workforce",tenant,sibling,null));
    }

    private ScheduleTransferService transfers(){
        var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());
        return proxy(new ScheduleTransferService(access,scheduling,repository,attendance,
            new com.rpb.reservation.workforce.scheduling.bulk.BulkScheduleRepository(jdbc,json),
            new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules()),json,time));
    }

    @Test void wholeTransferCreatesOneDestinationShiftAndPreservesStoreAccess(){
        var source=schedule(store,shift(store,"none"),DATE);
        var target=shift(sibling,"none");
        removeDestinationMembership();
        time.at("2026-10-28T00:00:00Z");
        var command=UUID.randomUUID();
        var write=new ScheduleTransferService.Transfer(source.id(),source.version(),sibling,target.id(),null);
        var transfers=transfers();
        var singleStoreAdmin=new com.rpb.reservation.walkin.api.CurrentActor(tenant,adminId,"staff",admin.roles(),admin.permissions(),Set.of(store));
        assertThatThrownBy(()->transfers.commit(singleStoreAdmin,store,command,write)).extracting("status").isEqualTo(403);
        var preview=transfers.preview(admin,store,write);
        assertThat(scheduling.find(tenant,source.id())).contains(source);
        assertThat(preview.source().status()).isEqualTo("cancelled");
        var result=transfers.commit(admin,store,command,write);
        assertThat(result.source().status()).isEqualTo("cancelled");
        assertThat(query.home(worker,store).schedules()).extracting(s->s.id()).containsExactly(result.target().id());
        assertThatThrownBy(()->punch(source,"IN")).extracting("code").isEqualTo("WORKFORCE_CLOCK_STATE");
        assertThatThrownBy(()->query.home(worker,sibling)).extracting("status").isEqualTo(403);
        assertThatThrownBy(()->roster.list(worker,sibling,DATE,DATE)).extracting("status").isEqualTo(403);
        when(gate.evaluateCommercial("workforce",tenant,sibling)).thenReturn(AppGateDecision.deny("workforce",tenant,sibling,null,
            com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_EXPIRED));
        assertThat(query.home(worker,store).schedules()).isEmpty();
        assertThatThrownBy(()->punch(result.target(),"IN")).extracting("status").isEqualTo(404);
        when(gate.evaluateCommercial("workforce",tenant,sibling)).thenReturn(AppGateDecision.allow("workforce",tenant,sibling,null));
        time.at("2026-10-28T01:00:00Z");
        assertThat(punch(result.target(),"IN").storeId()).isEqualTo(sibling);
        assertThat(query.home(worker,store).actions()).contains("OUT");
        assertThat(query.home(worker,store).transferStores().get(result.target().id()).id()).isEqualTo(sibling);
        time.at("2026-10-28T09:00:00Z");
        punch(result.target(),"OUT");
        assertThat(query.list(worker,store,DATE,DATE,null,50,0).items()).extracting(item->item.schedule().id()).contains(result.target().id());
        assertThat(query.list(admin,sibling,DATE,DATE,null,50,0).items()).singleElement()
            .satisfies(item->assertThat(item.workSeconds()).isEqualTo(28800));
        assertThat(scheduling.hasStoreAssignment(tenant,employee,sibling)).isFalse();
    }

    @Test void destinationCannotClockInBeforeTheTransferCutoff(){
        var source=schedule(store,shift(store,"none"),DATE);
        var target=shifts.save(admin,sibling,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ShiftWrite(
            0L,"Support","14:00","17:00",false,"none",0,0,0,0,false,true));
        removeDestinationMembership();
        punch(source,"IN");
        var write=new ScheduleTransferService.Transfer(source.id(),source.version(),sibling,target.id(),Instant.parse("2026-10-28T05:00:00Z"));
        var result=transfers().commit(admin,store,UUID.randomUUID(),write);
        time.at("2026-10-28T04:59:00Z");
        punch(source,"OUT");
        assertThat(query.home(worker,store).schedules()).extracting(s->s.id()).doesNotContain(result.target().id());
        assertThatThrownBy(()->punch(result.target(),"IN")).extracting("code").isEqualTo("WORKFORCE_CLOCK_STATE");
    }

    @Test void transfersAnActiveShiftAndClocksBothStoresWithoutDestinationMembership(){
        var command=UUID.randomUUID();
        var early=new com.rpb.reservation.workforce.scheduling.rules.AttendanceRules(1,List.of(),0,0,0,"full",
            List.of(new com.rpb.reservation.workforce.scheduling.rules.AttendanceRules.AnomalyRule("EARLY","Early departure",true,true)),null,null);
        var sourceShift=shifts.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ShiftWrite(
            0L,"Original","09:00","17:00",false,"none",0,0,0,0,false,true,early));
        var source=schedule(store,sourceShift,DATE);
        var target=shifts.save(admin,sibling,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ShiftWrite(
            0L,"Support","14:00","17:00",false,"none",0,0,0,0,false,true));
        removeDestinationMembership();
        var transfers=transfers();
        var in=punch(source,"IN");
        var write=new ScheduleTransferService.Transfer(source.id(),source.version(),sibling,target.id(),java.time.Instant.parse("2026-10-28T05:00:00Z"));
        var result=transfers.commit(admin,store,command,write);
        assertThat(transfers.commit(admin,store,command,write)).isEqualTo(result);
        assertThat(attendance.bySchedule(tenant,source.id())).contains(in);
        assertThat(new AttendanceJudgement().findings(source,
            new Session(in.id(),in.scheduleId(),in.employeeId(),in.storeId(),in.inAt(),result.source().endAt(),null,List.of(),false,2),
            List.of(),source.endAt())).extracting(ApprovalDtos.Finding::code).contains("EARLY");
        time.at("2026-10-28T05:00:00Z");
        assertThatThrownBy(()->punch(result.target(),"IN")).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_ATTENDANCE_OVERLAP");
        punch(source,"OUT");
        time.at("2026-10-28T06:00:00Z");
        assertThat(query.home(worker,store).schedules()).extracting(s->s.id()).contains(result.target().id());
        assertThat(punch(result.target(),"IN").storeId()).isEqualTo(sibling);
        time.at("2026-10-28T09:00:00Z");
        punch(result.target(),"OUT");
        assertThat(query.detail(worker,store,source.id()).attendance().workSeconds()).isEqualTo(14400);
        assertThat(query.detail(worker,store,result.target().id()).attendance().workSeconds()).isEqualTo(10800);
        assertThat(query.list(admin,store,DATE,DATE,null,50,0).items()).extracting(Attendance::workSeconds).containsExactly(14400L);
        assertThat(query.list(admin,sibling,DATE,DATE,null,50,0).items()).extracting(Attendance::workSeconds).containsExactly(10800L);
        assertPayrollDayTotals();

        assertThat(scheduling.hasStoreAssignment(tenant,employee,sibling)).isFalse();
    }
    private void assertPayrollDayTotals(){
        var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());
        var policies=new AttendancePolicyRepository(jdbc,json);
        var resolver=new AttendancePolicyResolver(policies,repository,new WorkModeRepository(jdbc,json));
        var sources=new ApprovalSources(attendance,new LeaveRepository(jdbc),new CalendarRepository(jdbc),json,time);
        var approvals=new ApprovalRepository(jdbc,json);
        for(var item:List.of(store,sibling)){
            var schedule=scheduling.list(tenant,item,DATE,DATE,employee,10,0).getFirst();
            var raw=sources.read(tenant,item,schedule);
            assertThat(new AttendanceJudgement().findings(schedule,raw.session(),List.of(),time.instant()))
                .noneMatch(finding->finding.code().equals("EARLY"));
            var seconds=item.equals(store)?14400:10800;
            approvals.save(tenant,item,employee,new ApprovalDtos.Approval(schedule.id(),1,sources.fingerprint(raw),
                seconds,0,0,raw,adminId,"Verified segment",time.instant()));
        }
        var daily=new AttendanceDailyService(resolver,policies,new AttendanceDailyRepository(jdbc,json),scheduling,sources,
            approvals,repository,access,json,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules()),time);
        var summary=new WorkSummaryService(access,directory,scheduling,approvals,sources,daily,
            new AttendanceDailyRepository(jdbc,json),repository);
        for(var shop:List.of(store,sibling)){
            var manager=new com.rpb.reservation.walkin.api.CurrentActor(tenant,adminId,"staff",admin.roles(),admin.permissions(),Set.of(shop));
            assertThat(summary.summary(manager,shop,DATE,DATE,null).items()).singleElement()
                .satisfies(row->{
                    assertThat(row.storeId()).isEqualTo(shop);
                    assertThat(row.workSeconds()).isEqualTo(shop.equals(store)?14400:10800);
                });
        }
        assertThat(summary.summary(admin,store,DATE,DATE,company).items()).singleElement()
            .satisfies(row->assertThat(row.workSeconds()).isEqualTo(25200));
        var day=daily.capture(tenant,company,employee,DATE,DATE).getFirst();
        assertThat(day.workSeconds()).as("issues: %s",day.issues()).isEqualTo(25200);
        assertThat((List<?>)day.snapshot().get("storeIds")).hasSize(2);
        var profile=new PayrollDtos.PayProfile(UUID.randomUUID(),company,employee,"Hourly",DATE,UUID.randomUUID(),1,
            new PayrollDtos.Rule("hourly","10","8","44",false,"1.5",List.of(),
                new PayrollDtos.Generation("attendance",List.of(1,2,3,4,5))),null,1);
        var payroll=new AttendancePayrollSources(daily).capture(tenant,company,employee,YearMonth.from(DATE),profile);
        assertThat(payroll.sources().stream().filter(row->row.workDate().equals(DATE)).toList())
            .singleElement().satisfies(row->assertThat(row.workSeconds()).isEqualTo(25200));
    }

}
