package com.rpb.reservation.workforce.attendance.daily;
import org.junit.jupiter.api.Test;import java.time.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class ArrangementEvaluatorTest {
 @Test void completedNormalDoesNotFabricateActual(){var r=new ArrangementEvaluator().evaluate(Instant.EPOCH,Instant.EPOCH.plusSeconds(28800),28800,0,Instant.EPOCH.plusSeconds(30000),List.of());assertEquals(28800L,r.workSeconds());assertTrue(r.issues().isEmpty());}
 @Test void pendingBlocks(){var r=new ArrangementEvaluator().evaluate(Instant.EPOCH,Instant.EPOCH.plusSeconds(28800),28800,0,Instant.EPOCH.plusSeconds(30000),List.of(exception("pending",0,3600)));assertTrue(r.issues().contains("PENDING_REQUEST"));assertNull(r.workSeconds());}
 @Test void approvedPartialLeaveDeductsExactly(){var r=new ArrangementEvaluator().evaluate(Instant.EPOCH,Instant.EPOCH.plusSeconds(28800),28800,0,Instant.EPOCH.plusSeconds(30000),List.of(exception("approved",0,7200)));assertEquals(21600L,r.workSeconds());assertEquals(7200,r.paidLeaveSeconds());}
 @Test void unknownBreakIntersectionBlocks(){var r=new ArrangementEvaluator().evaluate(Instant.EPOCH,Instant.EPOCH.plusSeconds(32400),28800,3600,Instant.EPOCH.plusSeconds(33000),List.of(exception("approved",0,7200)));assertTrue(r.issues().contains("BREAK_WINDOW_REQUIRED"));assertNull(r.workSeconds());}
 @Test void overlapBlocks(){var r=new ArrangementEvaluator().evaluate(Instant.EPOCH,Instant.EPOCH.plusSeconds(28800),28800,0,Instant.EPOCH.plusSeconds(30000),List.of(exception("approved",0,7200),exception("approved",3600,8000)));assertTrue(r.issues().contains("EXCEPTION_OVERLAP"));}
 private AttendanceDailyDtos.IntervalException exception(String status,long a,long b){return new AttendanceDailyDtos.IntervalException(UUID.randomUUID(),null,null,null,LocalDate.of(1970,1,1),"leave",null,"Annual",true,Instant.EPOCH.plusSeconds(a),Instant.EPOCH.plusSeconds(b),"reason",status,null,null,null,null,1,Instant.EPOCH);}
}
