package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.payroll.DailyWorkAllocation;
import com.rpb.reservation.workforce.payroll.PayCalculator;
import com.rpb.reservation.workforce.domain.WorkforceException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DailyWorkAllocationTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);
    private final PayCalculator.Rule rule = new PayCalculator.Rule("hourly", new BigDecimal("20"),
        new BigDecimal("8"), new BigDecimal("44"), true, new BigDecimal("1.5"));
    private PayCalculator.WorkDay day(int offset, int workHours, int leaveHours, boolean payable) {
        return new PayCalculator.WorkDay(MONDAY.plusDays(offset), workHours * 3600L, leaveHours * 3600L, payable);
    }

    @Test void combinesSameDayStoresBeforeApplyingDailyThreshold() {
        var days = DailyWorkAllocation.allocate(rule, List.of(day(0, 5, 0, true), day(0, 5, 0, true)));
        assertThat(days).singleElement().satisfies(d -> {
            assertThat(d.workDate()).isEqualTo(MONDAY);
            assertThat(d.normalSeconds()).isEqualTo(8 * 3600);
            assertThat(d.overtimeSeconds()).isEqualTo(2 * 3600);
            assertThat(d.payable()).isTrue();
        });
    }

    @Test void precedingMonthDaysConsumeWeekThresholdWithoutBeingPaidAgain() {
        var input = List.of(day(0, 8, 0, false), day(1, 8, 0, false), day(2, 8, 0, false),
            day(3, 8, 0, true), day(4, 8, 0, true), day(5, 8, 0, true));
        var days = DailyWorkAllocation.allocate(rule, input);
        assertThat(days.get(5).normalSeconds()).isEqualTo(4 * 3600);
        assertThat(days.get(5).overtimeSeconds()).isEqualTo(4 * 3600);
        var result = new PayCalculator().calculate(rule, input, new PayCalculator.Units(null, null), List.of(), List.of());
        assertThat(days.stream().filter(DailyWorkAllocation::payable).mapToLong(DailyWorkAllocation::overtimeSeconds).sum())
            .isEqualTo(result.overtimeSeconds()).isEqualTo(4 * 3600);
        assertThat(result.basicPay()).isEqualByComparingTo("400");
        assertThat(result.overtimePay()).isEqualByComparingTo("120");
    }

    @Test void paidLeaveRemainsSeparateFromOvertimeThresholdAndInputOrderDoesNotMatter() {
        var days = DailyWorkAllocation.allocate(rule, List.of(day(1, 8, 4, true), day(0, 10, 0, true)));
        assertThat(days).extracting(DailyWorkAllocation::workDate).containsExactly(MONDAY, MONDAY.plusDays(1));
        assertThat(days.get(1).normalSeconds()).isEqualTo(8 * 3600);
        assertThat(days.get(1).overtimeSeconds()).isZero();
        assertThat(days.get(1).paidLeaveSeconds()).isEqualTo(4 * 3600);
    }

    @Test void disabledOvertimeKeepsEveryWorkSecondAtNormalRate() {
        var disabled = new PayCalculator.Rule("hourly", new BigDecimal("20"), new BigDecimal("8"),
            new BigDecimal("44"), false, new BigDecimal("1.5"));
        var days = DailyWorkAllocation.allocate(disabled, List.of(day(0, 12, 0, true)));
        assertThat(days.getFirst().normalSeconds()).isEqualTo(12 * 3600);
        assertThat(days.getFirst().overtimeSeconds()).isZero();
    }

    @Test void contradictoryPayabilityAndOverflowAreRejectedInsteadOfSilentlyTruncated() {
        assertThatThrownBy(() -> DailyWorkAllocation.allocate(rule, List.of(day(0, 1, 0, true), day(0, 1, 0, false))))
            .isInstanceOf(WorkforceException.class);
        assertThatThrownBy(() -> DailyWorkAllocation.allocate(rule, List.of(
            new PayCalculator.WorkDay(MONDAY, Long.MAX_VALUE, 0, true), day(0, 1, 0, true))))
            .isInstanceOf(WorkforceException.class);
    }
}
