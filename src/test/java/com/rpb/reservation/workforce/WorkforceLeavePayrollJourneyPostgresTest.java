package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.approval.*;
import com.rpb.reservation.workforce.attendance.*;
import com.rpb.reservation.workforce.calendar.CalendarRepository;
import com.rpb.reservation.workforce.compliance.ComplianceEvidence;
import com.rpb.reservation.workforce.leave.*;
import com.rpb.reservation.workforce.leave.encashment.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.payroll.governance.PayChangeDtos;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** One synthetic employee: adoption → leave → punches → hours → encashment → formal pay → own payslip. */
class WorkforceLeavePayrollJourneyPostgresTest extends WorkforceLeaveEncashmentPostgresTest {
    @Test void completeJourneyKeepsLeaveOvertimeAndEncashmentInSeparateAccounts() {
        setupAccounts("4","14","60");
        var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());var audit=new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules());
        var reader=new ApprovalSources(attendance,new LeaveRepository(jdbc),new CalendarRepository(jdbc),json,time);reader.compliance(new ComplianceEvidence(policies));
        var ar=new ApprovalRepository(jdbc,json);approvals=proxy(new ApprovalService(access,new AttendanceAccess(access,repository,schedules),repository,schedules,ar,reader,audit,time));
        sources=new PayrollSources(payRepository,configs,repository,schedules,ar,reader,json);sources.compliance(new ComplianceEvidence(policies));sources.encashments(encashBridge);
        batches=proxy(new PayrollBatchService(access,new PayrollAccess(access,directory,jdbc),payRepository,sources,new PayrollCalculationService(time),repository,audit,time,tx));batches.encashments(encashBridge);
        var rule=new PayrollDtos.Rule("hourly","10","8","44",false,"1.5",List.of());legacyProfile(new PayrollDtos.PayProfile(UUID.randomUUID(),company,employee,"Journey employee",LocalDate.of(2026,1,1),template,1,rule,new CpfProfile("foreign",LocalDate.of(1990,1,1),null,"GG",""),1));
        var leaveDay=day(2);var leaveType=type("annual");var request=managedLeaves.submit(worker,store,UUID.randomUUID(),new LeaveDtos.LeaveWrite(leaveDay.id(),leaveType,leaveDay.startAt(),leaveDay.endAt(),"Synthetic paid annual leave"));
        managedLeaves.decide(admin,store,request.id(),new AttendanceDtos.DecisionWrite(1L,"approved","Leave entitlement and period checked"));
        var shift=shifts.save(admin,store,UUID.randomUUID(),new ShiftWrite(0L,"Journey roster","09:00","18:00",false,"manual",60,0,0,0,false,true));var workDay=roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift.id(),LocalDate.of(2026,11,3),"scheduled"));
        String[] at={"2026-11-03T01:00:00Z","2026-11-03T04:00:00Z","2026-11-03T05:00:00Z","2026-11-03T11:00:00Z"},actions={"IN","BREAK_START","BREAK_END","OUT"};
        for(int i=0;i<at.length;i++){time.at(at[i]);punches.punch(worker,store,UUID.randomUUID(),new AttendanceDtos.ClockWrite(workDay.id(),actions[i]));}
        time.at("2026-11-04T00:00:00Z");var paid=approvals.detail(admin,store,leaveDay.id()).row();approvals.approve(admin,store,leaveDay.id(),new ApprovalDtos.ApprovalWrite(0L,paid.sourceFingerprint(),"Approved paid leave verified"));
        var worked=approvals.detail(admin,store,workDay.id()).row();assertThat(worked.findings()).anyMatch(f->f.code().equals("OUTSIDE_SCHEDULE"));approvals.approve(admin,store,workDay.id(),new ApprovalDtos.ApprovalWrite(0L,worked.sourceFingerprint(),"Actual work verified",List.of(new ApprovalDtos.ReviewDecision("OUTSIDE_SCHEDULE","confirmed","Supervisor confirms one additional hour of work"))));
        assertThat(balance("annual",2026).used()).isEqualByComparingTo("1");assertThat(balance("annual",2026).available()).isEqualByComparingTo("3");
        jdbc.update("update workforce_employees set leave_date='2026-11-03',status='left' where tenant_id=? and id=?",tenant,employee);
        var command=new LeaveEncashmentDtos.Write(employee,balance("annual",2026).account().id(),n("3"),LocalDate.of(2026,11,3),LocalDate.of(2026,11,4),"termination","Ordinary termination; remaining annual leave, no misconduct or notice offset",List.of(),"monthly","2026-11");
        var quote=encash.preview(admin,store,command);assertThat(quote.valuation().amount()).isEqualTo("240.00");assertThat(balance("annual",2026).reserved()).isZero();
        var cash=encash.create(admin,store,UUID.randomUUID(),new LeaveEncashmentDtos.Write(command.employeeId(),command.accountId(),command.quantity(),command.valuationOn(),command.payableOn(),command.purpose(),command.evidence(),command.classifications(),command.route(),command.originMonth(),quote.valuation()));
        var proposal=changes.detail(admin,store,cash.requestId());changes.action(reviewer,store,proposal.id(),new PayChangeDtos.Action(UUID.randomUUID(),proposal.revision(),proposal.hash(),"approve","Independent gross rate and unused leave verified"));
        jdbc.update("update workforce_employees set leave_date='2026-10-31' where tenant_id=? and id<>?",tenant,employee);time.at("2026-12-02T00:00:00Z");
        var batch=batches.create(admin,store,UUID.randomUUID(),new PayrollDtos.BatchWrite(company,"2026-11"));batch=action(batch,"calculate");assertThat(batch.calculation().issues()).isEmpty();
        var line=batch.calculation().lines().getFirst();assertThat(line.paidLeaveSeconds()).isEqualTo(8*3600);assertThat(line.basicPay()).isEqualTo("160.00");assertThat(line.overtimePay()).isEqualTo("15.00");assertThat(line.allowances()).isEqualTo("240.00");assertThat(line.grossPay()).isEqualTo("415.00");assertThat(line.netPay()).isEqualTo("415.00");
        batch=action(batch,"confirm");assertThat(balance("annual",2026).used()).isEqualByComparingTo("1");assertThat(balance("annual",2026).encashed()).isEqualByComparingTo("3");assertThat(balance("annual",2026).available()).isZero();
        batch=action(batch,"publish");var slip=payslips.mineDetail(worker,store,batch.batch().id());assertThat(slip.line().netPay()).isEqualTo("415.00");assertThat(slip.line().components()).anyMatch(c->c.label().equals("未休年假折现")&&c.amount().equals("240.00"));assertThat(attendance.events(tenant,workDay.id())).hasSize(4);
    }
}
