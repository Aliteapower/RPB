package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.approval.ApprovalCalculator;
import com.rpb.reservation.workforce.approval.ApprovalCalculator.LeaveSlice;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ApprovalCalculatorTest {
    final Instant start=Instant.parse("2026-10-28T01:00:00Z"),end=start.plusSeconds(8*3600),now=end.plusSeconds(1);
    Schedule schedule(String mode){return new Schedule(UUID.randomUUID(),UUID.randomUUID(),"Alice","001",UUID.randomUUID(),LocalDate.of(2026,10,28),start,end,"Asia/Singapore","scheduled",new Shift(UUID.randomUUID(),"Day","09:00","17:00",false,mode,30,360,180,240,false,true,1),UUID.randomUUID(),1);}
    @Test void wholeShiftPaidLeaveNeedsNoClockButPartialLeaveDoes(){var s=schedule("none");var c=new ApprovalCalculator();var full=c.calculate(s,null,List.of(new LeaveSlice(start,end,true)),now,false);assertThat(full.issues()).isEmpty();assertThat(full.paidLeaveSeconds()).isEqualTo(28800);assertThat(full.workSeconds()).isZero();assertThat(c.calculate(s,null,List.of(new LeaveSlice(start,start.plusSeconds(3600),true)),now,false).issues()).contains("MISSING_IN");}
    @Test void fixedBreakIsProratedAndWholeShiftNeverExceedsPlan(){var s=schedule("duration");var c=new ApprovalCalculator();var r=c.calculate(s,null,List.of(new LeaveSlice(start,start.plusSeconds(4*3600),true),new LeaveSlice(start.plusSeconds(4*3600),end,false)),now,false);assertThat(r.paidLeaveSeconds()).isEqualTo(13500);assertThat(r.unpaidLeaveSeconds()).isEqualTo(13500);assertThat(r.issues()).isEmpty();}
    @Test void windowBreakIntersectionExcludedFromLeave(){var s=schedule("window");var r=new ApprovalCalculator().calculate(s,null,List.of(new LeaveSlice(start,end,true)),now,false);assertThat(r.paidLeaveSeconds()).isEqualTo(7*3600);}
    @Test void partialLeaveAndActualWorkAreSeparateAndOverlapBlocks(){var s=schedule("none");var middle=start.plusSeconds(4*3600);var session=new Session(UUID.randomUUID(),s.id(),s.employeeId(),UUID.randomUUID(),middle,end,null,List.of(),false,2);var c=new ApprovalCalculator();var r=c.calculate(s,session,List.of(new LeaveSlice(start,middle,true)),now,false);assertThat(r.workSeconds()).isEqualTo(14400);assertThat(r.paidLeaveSeconds()).isEqualTo(14400);assertThat(r.issues()).isEmpty();assertThat(c.calculate(s,session,List.of(new LeaveSlice(start,middle.plusSeconds(1),true)),now,false).issues()).contains("LEAVE_ATTENDANCE_OVERLAP");}
    @Test void unfinishedShiftAndPendingRequestsCannotBeApproved(){var s=schedule("none");var session=new Session(UUID.randomUUID(),s.id(),s.employeeId(),UUID.randomUUID(),start,null,null,List.of(),false,1);assertThat(new ApprovalCalculator().calculate(s,session,List.of(),start.plusSeconds(60),true).issues()).contains("OPEN","SHIFT_NOT_ENDED","PENDING_REQUEST");}
    @Test void adjacentMicrosecondLeavesCoverTheWholeShiftWithoutLosingASecond(){
        var mid=start.plusSeconds(4*3600).plusNanos(500000000);var r=new ApprovalCalculator().calculate(schedule("none"),null,List.of(new LeaveSlice(start,mid,true),new LeaveSlice(mid,end,true)),now,false);
        assertThat(r.issues()).isEmpty();assertThat(r.paidLeaveSeconds()).isEqualTo(28800);
    }
}
