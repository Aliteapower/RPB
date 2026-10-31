package com.rpb.reservation.workforce.payroll;

import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.*;
import java.time.*;
import java.util.*;

/** Explicit contract units, never a reconstruction of punches or attendance. */
public final class ContractPayCalculator {
    private ContractPayCalculator() {}
    public record Terms(String employmentProration, String unpaidLeaveTreatment, String overtimeTreatment) {}
    public record Day(LocalDate date, long unpaidLeaveSeconds, long extraSeconds) {}
    public record Result(BigDecimal basicPay, BigDecimal overtimePay, BigDecimal workingDays,
                         BigDecimal payableDays, long normalPaySeconds, long overtimeSeconds) {}

    public static Terms validate(Terms terms) {
        if (terms == null || !Set.of("full", "working_days").contains(Objects.toString(terms.employmentProration(), ""))
            || !Set.of("no_deduction", "deduct").contains(Objects.toString(terms.unpaidLeaveTreatment(), ""))
            || !Set.of("included", "separate", "not_applicable").contains(Objects.toString(terms.overtimeTreatment(), "")))
            throw WorkforceException.invalid();
        return terms;
    }

    public static Result calculate(PayCalculator.Rule rule, Terms terms, YearMonth month,
                                   LocalDate from, LocalDate to, List<Integer> weekdays, List<Day> exceptions) {
        PayCalculator.validateRule(rule);
        validate(terms);
        if (rule.overtimeEnabled() || month == null || from == null || to == null || from.isAfter(to)
            || from.isBefore(month.atDay(1)) || to.isAfter(month.atEndOfMonth()) || weekdays == null || exceptions == null
            || weekdays.stream().anyMatch(d -> d == null || d < 1 || d > 7)
            || new HashSet<>(weekdays).size() != weekdays.size()) throw WorkforceException.invalid();
        boolean fixed = rule.basis().equals("monthly") && terms.employmentProration().equals("full")
            && terms.unpaidLeaveTreatment().equals("no_deduction");
        if (weekdays.isEmpty() && !fixed) throw WorkforceException.invalid();
        try {
            long daily = rule.dayHours().multiply(BigDecimal.valueOf(3600)).longValueExact();
            var expected = month.atDay(1).datesUntil(month.plusMonths(1).atDay(1))
                .filter(d -> weekdays.contains(d.getDayOfWeek().getValue())).toList();
            long monthSeconds = Math.multiplyExact(expected.size(), daily);
            long paidSeconds = terms.employmentProration().equals("full") ? monthSeconds
                : Math.multiplyExact(expected.stream().filter(d -> !d.isBefore(from) && !d.isAfter(to)).count(), daily);
            long overtime = 0;
            var dates = new HashSet<LocalDate>();
            for (var day : exceptions) {
                if (day == null || day.date() == null || day.date().isBefore(from) || day.date().isAfter(to)
                    || !dates.add(day.date()) || day.unpaidLeaveSeconds() < 0 || day.extraSeconds() < 0
                    || day.extraSeconds() > 86400 || day.unpaidLeaveSeconds() > daily) throw WorkforceException.invalid();
                if (terms.unpaidLeaveTreatment().equals("deduct") && day.unpaidLeaveSeconds() > 0) {
                    if (!expected.contains(day.date())) throw WorkforceException.invalid();
                    paidSeconds = Math.subtractExact(paidSeconds, day.unpaidLeaveSeconds());
                }
                if (terms.overtimeTreatment().equals("separate")) overtime = Math.addExact(overtime, day.extraSeconds());
            }
            if (paidSeconds < 0) throw WorkforceException.invalid();
            BigDecimal base;
            if (fixed) base = rule.rate().setScale(2, RoundingMode.HALF_UP);
            else {
                BigDecimal denominator = switch (rule.basis()) {
                    case "monthly" -> BigDecimal.valueOf(monthSeconds);
                    case "daily" -> BigDecimal.valueOf(daily);
                    default -> BigDecimal.valueOf(3600);
                };
                if (denominator.signum() <= 0) throw WorkforceException.invalid();
                base = rule.rate().multiply(BigDecimal.valueOf(paidSeconds)).divide(denominator, 2, RoundingMode.HALF_UP);
            }
            BigDecimal numerator = rule.rate().multiply(BigDecimal.valueOf(overtime)).multiply(rule.overtimeMultiplier());
            BigDecimal denominator = BigDecimal.valueOf(3600);
            if (rule.basis().equals("monthly")) {
                numerator = numerator.multiply(BigDecimal.valueOf(12));
                denominator = denominator.multiply(BigDecimal.valueOf(52 * 44));
            } else if (rule.basis().equals("daily")) denominator = denominator.multiply(rule.dayHours());
            if (overtime > 0 && rule.overtimeMultiplier().signum() <= 0) throw WorkforceException.invalid();
            var payableDays = fixed && expected.isEmpty() ? BigDecimal.ONE : BigDecimal.valueOf(paidSeconds)
                .divide(BigDecimal.valueOf(daily), 12, RoundingMode.HALF_UP).stripTrailingZeros();
            return new Result(base, numerator.divide(denominator, 2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(fixed && expected.isEmpty() ? 1 : expected.size()), payableDays, paidSeconds, overtime);
        } catch (ArithmeticException e) {
            throw WorkforceException.invalid();
        }
    }
}
