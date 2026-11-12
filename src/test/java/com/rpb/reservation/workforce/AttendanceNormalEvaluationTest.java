package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.attendance.daily.*;
import static com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.*;
import java.time.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class AttendanceNormalEvaluationTest {
 private EffectiveDay day(String state,String phase){return new EffectiveDay(UUID.randomUUID(),UUID.randomUUID(),LocalDate.of(2026,10,30),"default_normal",state,"arrangement","fp",1,null,state.equals("system_valid")?28800L:null,0,0,0,phase==null?Map.of():Map.of("phase",phase),List.of());}
 @Test void onlyFinishedSourcesAreEligible(){assertTrue(AttendanceNormalEvaluationService.finalized(day("system_valid","completed"),LocalDate.of(2026,10,30)));
 assertFalse(AttendanceNormalEvaluationService.finalized(day("system_valid","in_progress"),LocalDate.of(2026,10,30)));
 assertFalse(AttendanceNormalEvaluationService.finalized(day("future","future"),LocalDate.of(2026,10,30)));
 assertFalse(AttendanceNormalEvaluationService.finalized(day("unarranged",null),LocalDate.of(2026,10,30)));
 assertTrue(AttendanceNormalEvaluationService.finalized(day("pending","completed"),LocalDate.of(2026,10,30)));
 assertFalse(AttendanceNormalEvaluationService.finalized(day("rest",null),LocalDate.of(2026,10,30)));
 assertTrue(AttendanceNormalEvaluationService.finalized(day("not_applicable",null),LocalDate.of(2026,10,31)));}
 @Test void sameFingerprintPreservesHumanDecision(){assertFalse(AttendanceNormalEvaluationService.changed(day("human_valid","completed"),day("system_valid","completed")));assertFalse(AttendanceNormalEvaluationService.changed(day("system_valid","completed"),day("system_valid","completed")));assertTrue(AttendanceNormalEvaluationService.changed(null,day("system_valid","completed")));}
}
