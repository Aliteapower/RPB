package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.payroll.*;import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import java.time.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class FullAttendanceBonusTest {
 SourceRef ref(String status){return new SourceRef(UUID.randomUUID(),UUID.randomUUID(),LocalDate.of(2026,10,28),1,"a",28800,0,0,true,new SourceEvidence(28800,"current",28800L,null,null,List.of(),status));}
 @Test void onlyReviewedEntireMonthCanEarnMonthlyBonus(){var m=YearMonth.of(2026,10);assertThat(FullAttendanceBonus.status(List.of(ref("eligible")),m,m.atDay(28))).isEqualTo("deferred");assertThat(FullAttendanceBonus.status(List.of(ref("eligible")),m,m.atEndOfMonth())).isEqualTo("eligible");assertThat(FullAttendanceBonus.status(List.of(ref("eligible"),ref("ineligible")),m,m.atEndOfMonth())).isEqualTo("ineligible");assertThat(FullAttendanceBonus.status(List.of(ref(null)),m,m.atEndOfMonth())).isEqualTo("pending");assertThat(FullAttendanceBonus.status(List.of(),m,m.atEndOfMonth())).isEqualTo("pending");}
}
