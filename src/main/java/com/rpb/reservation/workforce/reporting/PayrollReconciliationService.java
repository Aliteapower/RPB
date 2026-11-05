package com.rpb.reservation.workforce.reporting;

import static com.rpb.reservation.workforce.reporting.PayrollReportDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class PayrollReconciliationService {
    private final PayrollReportService reports;
    public PayrollReconciliationService(PayrollReportService reports) { this.reports=reports; }
    public Comparison compare(CurrentActor actor, UUID store, CompareWrite c) {
        if (c==null || c.companyId()==null) throw WorkforceException.invalid();
        var report=reports.read(actor,store,c.month(),List.of(c.companyId()));
        PayrollReportService.requireCurrent(report,c.fingerprint());
        if (!PayrollReportValues.frozen(report.companies().getFirst().status())) throw new WorkforceException("WORKFORCE_PAYROLL_BLOCKED",409);
        var expected=PayrollReportValues.values(PayrollReportValues.expected(c.expected()));
        var actual=PayrollReportValues.values(report.frozen().totals()); var rows=new ArrayList<Difference>();
        for(int i=0;i<actual.size();i++) rows.add(new Difference(PayrollReportValues.FIELDS.get(i),expected.get(i),actual.get(i),PayrollReportValues.money(PayrollReportValues.decimal(actual.get(i)).subtract(PayrollReportValues.decimal(expected.get(i))))));
        return new Comparison(report,rows.stream().allMatch(r->r.difference().equals("0.00")),List.copyOf(rows));
    }
}
