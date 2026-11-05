package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.payroll.ContractPayCalculator;
import com.rpb.reservation.workforce.payroll.PayCalculator;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContractPayCalculatorTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 10);
    private static final List<Integer> WEEK = List.of(1, 2, 3, 4, 5);
    private final PayCalculator.Rule monthly = new PayCalculator.Rule("monthly", new BigDecimal("2600"),
        new BigDecimal("8"), new BigDecimal("44"), false, new BigDecimal("1.5"));

    @Test void explicitFullContractNeedsNoInventedAttendanceOrWorkSchedule() {
        var terms = new ContractPayCalculator.Terms("full", "no_deduction", "included");
        var result = ContractPayCalculator.calculate(monthly, terms, MONTH, MONTH.atDay(16), MONTH.atEndOfMonth(), List.of(), List.of());
        assertThat(result.basicPay()).isEqualByComparingTo("2600");
        assertThat(result.normalPaySeconds()).isZero();
        assertThat(result.overtimeSeconds()).isZero();
    }

    @Test void datedEmploymentAndUnpaidLeaveUseExplicitContractWorkingDayPolicy() {
        var terms = new ContractPayCalculator.Terms("working_days", "deduct", "included");
        var result = ContractPayCalculator.calculate(monthly, terms, MONTH, MONTH.atDay(16), MONTH.atEndOfMonth(), WEEK,
            List.of(new ContractPayCalculator.Day(MONTH.atDay(19), 4 * 3600, 0)));
        assertThat(result.workingDays()).isEqualByComparingTo("22");
        assertThat(result.payableDays()).isEqualByComparingTo("10.5");
        assertThat(result.basicPay()).isEqualByComparingTo("1240.91");
    }

    @Test void separateOvertimeUsesOnlyApprovedDeclaredUnitsAndContractRate() {
        var terms = new ContractPayCalculator.Terms("working_days", "deduct", "separate");
        var result = ContractPayCalculator.calculate(monthly, terms, MONTH, MONTH.atDay(1), MONTH.atEndOfMonth(), WEEK,
            List.of(new ContractPayCalculator.Day(MONTH.atDay(19), 0, 2 * 3600)));
        assertThat(result.overtimeSeconds()).isEqualTo(7200);
        assertThat(result.overtimePay()).isEqualByComparingTo("40.91");
        var included = ContractPayCalculator.calculate(monthly,
            new ContractPayCalculator.Terms("working_days", "deduct", "included"), MONTH,
            MONTH.atDay(1), MONTH.atEndOfMonth(), WEEK, List.of(new ContractPayCalculator.Day(MONTH.atDay(19), 0, 7200)));
        assertThat(included.overtimePay()).isEqualByComparingTo("0");
    }

    @Test void hourlyContractUsesDeclaredStandardUnitsWithoutPresentingThemAsActualAttendance() {
        var rule = new PayCalculator.Rule("hourly", new BigDecimal("12.50"), new BigDecimal("8"),
            new BigDecimal("44"), false, new BigDecimal("1.5"));
        var result = ContractPayCalculator.calculate(rule,
            new ContractPayCalculator.Terms("working_days", "deduct", "not_applicable"), MONTH,
            MONTH.atDay(30), MONTH.atEndOfMonth(), WEEK, List.of());
        assertThat(result.normalPaySeconds()).isEqualTo(28800);
        assertThat(result.basicPay()).isEqualByComparingTo("100.00");
    }

    @Test void missingTermsConflictingDaysAndOutOfEmploymentInputsAreRejected() {
        assertThatThrownBy(() -> ContractPayCalculator.calculate(monthly, null, MONTH,
            MONTH.atDay(1), MONTH.atEndOfMonth(), WEEK, List.of())).isInstanceOf(WorkforceException.class);
        var terms = new ContractPayCalculator.Terms("working_days", "deduct", "separate");
        assertThatThrownBy(() -> ContractPayCalculator.calculate(monthly, terms, MONTH,
            MONTH.atDay(16), MONTH.atEndOfMonth(), WEEK, List.of(new ContractPayCalculator.Day(MONTH.atDay(1), 3600, 0))))
            .isInstanceOf(WorkforceException.class);
        assertThatThrownBy(() -> ContractPayCalculator.calculate(monthly, terms, MONTH,
            MONTH.atDay(1), MONTH.atEndOfMonth(), WEEK, List.of(
                new ContractPayCalculator.Day(MONTH.atDay(19), 0, 3600), new ContractPayCalculator.Day(MONTH.atDay(19), 0, 3600))))
            .isInstanceOf(WorkforceException.class);
    }
}
