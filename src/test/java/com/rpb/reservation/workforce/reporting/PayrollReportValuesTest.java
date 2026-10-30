package com.rpb.reservation.workforce.reporting;

import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PayrollReportValuesTest {
    @Test void unknownFinancialFieldsRemainUnknownThroughCompanyAggregation() {
        var unknown=new PayrollReportDtos.Totals("1000.00",null,null,null,"2.50",null,"0.00",null);
        var known=new PayrollReportDtos.Totals("1000.00","800.00","200.00","170.00","2.50","1172.50","0.00","800.00");
        var sum=PayrollReportValues.sum(java.util.List.of(unknown,known));
        assertThat(sum.gross()).isEqualTo("2000.00");assertThat(sum.net()).isNull();assertThat(sum.employeeCpf()).isNull();assertThat(sum.employerCost()).isNull();assertThat(sum.remaining()).isNull();assertThat(sum.sdl()).isEqualTo("5.00");
    }
    @Test void acceptsConfiguredPolicyYearsAndRejectsUnsupportedOrMalformedPeriods() {
        for (String period : new String[]{"2000-01", "2027-01", "2100-12"})
            assertThat(PayrollReportValues.month(period)).isEqualTo(YearMonth.parse(period));
        for (String period : new String[]{"1999-12", "2101-01"})
            assertThatThrownBy(() -> PayrollReportValues.month(period))
                .isInstanceOfSatisfying(WorkforceException.class,
                    e -> assertThat(e.code()).isEqualTo("WORKFORCE_PAYROLL_UNSUPPORTED"));
        for (String period : new String[]{"2027-00", "2027-13", "2027-1", ""})
            assertThatThrownBy(() -> PayrollReportValues.month(period))
                .isInstanceOfSatisfying(WorkforceException.class,
                    e -> assertThat(e.code()).isEqualTo("WORKFORCE_INVALID"));
    }
}
