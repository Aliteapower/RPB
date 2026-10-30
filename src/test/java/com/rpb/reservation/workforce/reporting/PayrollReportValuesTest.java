package com.rpb.reservation.workforce.reporting;

import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PayrollReportValuesTest {
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
