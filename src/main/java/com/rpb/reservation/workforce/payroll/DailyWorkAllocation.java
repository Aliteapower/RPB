package com.rpb.reservation.workforce.payroll;

import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** One paying company's daily allocation. Calendar and payroll consume the same result. */
public record DailyWorkAllocation(LocalDate workDate, long workSeconds, long normalSeconds,
                                  long overtimeSeconds, long paidLeaveSeconds, boolean payable) {
    private record Day(long work, long leave, boolean payable) {}

    public static List<DailyWorkAllocation> allocate(PayCalculator.Rule rule, List<PayCalculator.WorkDay> input) {
        return allocate(rule,input,rule.overtimeEnabled());
    }

    static List<DailyWorkAllocation> allocate(PayCalculator.Rule rule, List<PayCalculator.WorkDay> input, boolean premium) {
        PayCalculator.validateRule(rule);
        if (input == null) throw WorkforceException.invalid();
        try {
            var days = new TreeMap<LocalDate, Day>();
            for (var entry : input) {
                if (entry == null || entry.date() == null || entry.workSeconds() < 0 || entry.paidLeaveSeconds() < 0)
                    throw WorkforceException.invalid();
                var old = days.get(entry.date());
                if (old != null && old.payable() != entry.payable()) throw WorkforceException.invalid();
                days.put(entry.date(), new Day(Math.addExact(old == null ? 0 : old.work(), entry.workSeconds()),
                    Math.addExact(old == null ? 0 : old.leave(), entry.paidLeaveSeconds()), entry.payable()));
            }
            long dailyLimit = rule.dayHours().multiply(BigDecimal.valueOf(3600)).longValueExact();
            long weeklyLimit = rule.weekHours().multiply(BigDecimal.valueOf(3600)).longValueExact();
            var weeklyNormal = new HashMap<LocalDate, Long>();
            var result = new ArrayList<DailyWorkAllocation>();
            for (var entry : days.entrySet()) {
                var day = entry.getValue();
                var monday = entry.getKey().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                long normal = day.work();
                if (premium) {
                    long prior = weeklyNormal.getOrDefault(monday, 0L);
                    normal = Math.min(Math.min(day.work(), dailyLimit), Math.max(0, weeklyLimit - prior));
                    weeklyNormal.put(monday, Math.addExact(prior, normal));
                }
                result.add(new DailyWorkAllocation(entry.getKey(), day.work(), normal,
                    day.work() - normal, day.leave(), day.payable()));
            }
            return List.copyOf(result);
        } catch (ArithmeticException e) {
            throw WorkforceException.invalid();
        }
    }
}
