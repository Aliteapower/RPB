package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.attendance.AttendanceCalculator;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.Interval;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Shift;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AttendanceCalculatorTest {
    Instant start=Instant.parse("2026-10-28T14:00:00Z");
    Shift shift(String mode,boolean paid){return new Shift(UUID.randomUUID(),"Night","22:00","06:00",true,mode,30,360,120,180,paid,true,1);}
    @Test void durationThresholdDoesNotDeductFromShortAttendance(){
        var c=new AttendanceCalculator();
        assertThat(c.calculate(shift("duration",false),start,start,start.plusSeconds(300*60),List.of(),false).workSeconds()).isEqualTo(300*60L);
        assertThat(c.calculate(shift("duration",false),start,start,start.plusSeconds(480*60),List.of(),false).workSeconds()).isEqualTo(450*60L);
    }
    @Test void windowUsesActualIntersectionAcrossMidnight(){
        var r=new AttendanceCalculator().calculate(shift("window",false),start,start.plusSeconds(150*60),start.plusSeconds(240*60),List.of(),false);
        assertThat(r.breakSeconds()).isEqualTo(30*60L);assertThat(r.workSeconds()).isEqualTo(60*60L);
    }
    @Test void paidAndActualBreakOverrideAreIndependent(){
        var breaks=List.of(new Interval(start.plusSeconds(3600),start.plusSeconds(5400)));
        var c=new AttendanceCalculator();assertThat(c.calculate(shift("manual",true),start,start,start.plusSeconds(7200),breaks,false).workSeconds()).isEqualTo(7200);
        assertThat(c.calculate(shift("duration",false),start,start,start.plusSeconds(28800),List.of(),true).workSeconds()).isEqualTo(28800);
    }
    @Test void openAndExcessiveSessionsCannotBecomePayableHours(){
        var c=new AttendanceCalculator();assertThat(c.calculate(shift("none",false),start,start,null,List.of(),false).workSeconds()).isNull();
        assertThat(c.calculate(shift("none",false),start,start,start.plusSeconds(37*3600),List.of(),false).anomalies()).contains("LONG_SHIFT");
    }
    @Test void overlappingBreaksAndBreakOutsideWorkAreRejected(){
        var c=new AttendanceCalculator();
        assertThatThrownBy(()->c.calculate(shift("manual",false),start,start,start.plusSeconds(7200),List.of(new Interval(start,start.plusSeconds(4000)),new Interval(start.plusSeconds(3600),start.plusSeconds(6000))),false)).isInstanceOf(RuntimeException.class);
    }
}
