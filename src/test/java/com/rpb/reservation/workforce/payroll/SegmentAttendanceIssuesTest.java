package com.rpb.reservation.workforce.payroll;
import com.rpb.reservation.workforce.payroll.PayrollSources.AttendanceIssueScope;
import com.rpb.reservation.workforce.payroll.segment.SegmentAttendanceIssues;
import java.time.LocalDate;import java.util.*;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.assertThat;
class SegmentAttendanceIssuesTest {
 @Test void frozenPrefixIssuesAreRemovedByDateButIdenticalSuffixAndIndependentCodesSurvive(){var cutoff=LocalDate.of(2026,10,15);var scope=new AttendanceIssueScope(List.of("PROFILE_MISSING","SOURCE_CHANGED"),Map.of(cutoff.minusDays(10),List.of("SOURCE_CHANGED","PENDING_REQUEST","APPROVAL_STALE"),cutoff.plusDays(5),List.of("PENDING_REQUEST","EVALUATION_REQUIRED")),List.of(),Set.of());assertThat(SegmentAttendanceIssues.after(scope,cutoff,List.of())).containsExactly("PROFILE_MISSING","SOURCE_CHANGED","PENDING_REQUEST","EVALUATION_REQUIRED");}
}
