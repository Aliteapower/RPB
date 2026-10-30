package com.rpb.reservation.workforce.reporting;

import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.YearMonth;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PayrollReportRepository {
    private final JdbcTemplate jdbc;
    public PayrollReportRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<UUID> batch(UUID tenant, UUID company, YearMonth month) {
        var ids = jdbc.queryForList("""
            select id from workforce_pay_batches
            where tenant_id=? and company_id=? and period_month=? and status<>'voided'
            order by id limit 2
            """, UUID.class, tenant, company, month.atDay(1));
        if (ids.size() > 1) throw new IllegalStateException("Multiple active company payroll batches");
        return ids.stream().findFirst();
    }
}
