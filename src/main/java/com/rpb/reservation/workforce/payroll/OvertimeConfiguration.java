package com.rpb.reservation.workforce.payroll;

import com.rpb.reservation.workforce.compliance.ComplianceEvidence;
import com.rpb.reservation.workforce.compliance.ComplianceRules;
import java.util.List;

/** Shared hours configuration for clock/default-normal payroll and calendar projections.
 * Contract and free arrangements retain their separate, explicit settlement rules. */
public record OvertimeConfiguration(PayCalculator.Rule allocationRule, boolean premium) {
    public static OvertimeConfiguration resolve(PayCalculator.Rule rule, ComplianceEvidence.Terms terms) {
        if (terms == null) return new OvertimeConfiguration(rule, rule.overtimeEnabled());
        var contract = terms.contract();
        boolean premium = rule.overtimeEnabled() || ComplianceRules.partFour(terms.policy().rules(), contract.category(),
            ComplianceRules.monthlyBasic(rule.basis(), rule.rate(), contract.dailyHours(), contract.weeklyHours(), contract.weeklyDays()));
        var allocation = new PayCalculator.Rule(rule.basis(), rule.rate(),
            contract.partTime() ? contract.comparableDailyHours() : contract.dailyHours(),
            contract.partTime() ? contract.comparableWeeklyHours() : contract.weeklyHours(), false, rule.overtimeMultiplier());
        return new OvertimeConfiguration(allocation, premium);
    }

    public List<DailyWorkAllocation> allocate(List<PayCalculator.WorkDay> workDays) {
        return DailyWorkAllocation.allocate(allocationRule, workDays, premium);
    }

    public boolean sameThresholds(OvertimeConfiguration other) {
        return premium == other.premium && allocationRule.dayHours().compareTo(other.allocationRule.dayHours()) == 0
            && allocationRule.weekHours().compareTo(other.allocationRule.weekHours()) == 0;
    }
}
