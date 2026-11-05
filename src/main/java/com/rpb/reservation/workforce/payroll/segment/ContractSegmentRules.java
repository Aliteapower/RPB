package com.rpb.reservation.workforce.payroll.segment;

import com.rpb.reservation.workforce.payroll.PayrollDtos.Rule;
import com.rpb.reservation.workforce.payroll.PayrollGeneration;
import java.time.LocalDate;
import java.time.YearMonth;

/** A full-month contract cannot silently become an earned-to-cutoff proration. */
public final class ContractSegmentRules {
    private ContractSegmentRules() {}
    public static boolean requiresFinalRange(Rule rule, LocalDate cutoff, LocalDate leaveDate) {
        return PayrollGeneration.contract(rule)
            && "full".equals(rule.generation().contractTerms().employmentProration())
            && !cutoff.equals(YearMonth.from(cutoff).atEndOfMonth())
            && (leaveDate == null || leaveDate.isAfter(cutoff));
    }
}
