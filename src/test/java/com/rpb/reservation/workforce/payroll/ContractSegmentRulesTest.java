package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import com.rpb.reservation.workforce.payroll.segment.ContractSegmentRules;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractSegmentRulesTest {
    private Rule rule(String proration) {
        return new Rule("monthly","2600","8","44",false,"1.5",List.of(),
            new Generation("contract",List.of(1,2,3,4,5),new ContractPayCalculator.Terms(proration,"no_deduction","included")));
    }
    @Test void fullMonthTermsCannotPayFutureSalaryInAnIntermediateSegment() {
        var cutoff=LocalDate.of(2026,10,25);
        assertThat(ContractSegmentRules.requiresFinalRange(rule("full"),cutoff,null)).isTrue();
        assertThat(ContractSegmentRules.requiresFinalRange(rule("full"),cutoff,cutoff.plusDays(1))).isTrue();
        assertThat(ContractSegmentRules.requiresFinalRange(rule("full"),cutoff,cutoff)).isFalse();
        assertThat(ContractSegmentRules.requiresFinalRange(rule("full"),LocalDate.of(2026,10,31),null)).isFalse();
        assertThat(ContractSegmentRules.requiresFinalRange(rule("working_days"),cutoff,null)).isFalse();
    }
}
