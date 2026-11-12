package com.rpb.reservation.workforce.reporting;

import static com.rpb.reservation.workforce.reporting.PayrollReportDtos.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class PayrollReportService {
    private final PayrollAccess access;
    private final PayrollReportRepository repository;
    private final PayrollRepository payroll;
    private final PayrollSources sources;
    private final WorkforceJson json;
    private final Clock clock;
    public PayrollReportService(PayrollAccess access, PayrollReportRepository repository, PayrollRepository payroll, PayrollSources sources, WorkforceJson json, Clock clock) {
        this.access=access; this.repository=repository; this.payroll=payroll; this.sources=sources; this.json=json; this.clock=clock;
    }
    public Report read(CurrentActor actor, UUID store, String period, List<UUID> requested) {
        var month = PayrollReportValues.month(period);
        var ids = PayrollReportValues.companies(requested);
        // Establish the entire requested scope before reading any salary data.
        for (UUID id : ids) access.enter(actor, store, id);
        var names = access.companies(actor, store).stream().collect(java.util.stream.Collectors.toMap(c->c.id(),c->c.name()));
        var companies = new ArrayList<CompanyRow>(); var employees = new ArrayList<EmployeeRow>(); var payments = new ArrayList<PaymentRow>(); var revisions=new ArrayList<Object>();
        for (UUID company : ids) {
            var id = repository.batch(actor.tenantId(), company, month);
            if (id.isEmpty()) { companies.add(new CompanyRow(company,names.get(company),null,"missing",false,0,null,List.of("NO_BATCH"),null)); continue; }
            var b = payroll.find(actor.tenantId(),id.get()).orElseThrow(WorkforceException::missing); var c = b.calculation();
            var historicalStores=new TreeSet<UUID>();
            if(c!=null)c.lines().forEach(l->l.sources().forEach(s->historicalStores.add(s.storeId())));
            access.enter(actor,store,company,historicalStores);
            var capture=sources.capture(actor.tenantId(),company,month,payroll.inputs(actor.tenantId(),b.id()));
            access.enter(actor,store,company,capture.stores());
            // Keep internal revisions in the digest, never in the public report/CSV projection.
            revisions.add(List.of(b.id(),b.version(),capture.fingerprint()));
            if (c == null) { companies.add(new CompanyRow(company,b.companyName(),b.id(),b.status(),false,capture.lines().size(),null,List.of("NOT_CALCULATED"),null)); continue; }
            var paymentRecords=payroll.payments(actor.tenantId(),b.id());String companyName=c.companyName();
            var paidByEmployee = new HashMap<UUID,BigDecimal>();
            for (var p : paymentRecords) paidByEmployee.merge(p.employeeId(),new BigDecimal(p.amount()).multiply(BigDecimal.valueOf(p.reversesId()==null?1:-1)),BigDecimal::add);
            var paid = paidByEmployee.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
            var totals = new Totals(c.totalGross(),c.totalNet(),c.totalEmployeeCpf(),c.totalEmployerCpf(),c.totalSdl(),c.totalEmployerCost(),PayrollReportValues.money(paid),c.totalNet()==null?null:PayrollReportValues.money(new BigDecimal(c.totalNet()).subtract(paid)));
            companies.add(new CompanyRow(company,companyName,b.id(),b.status(),!c.sourceFingerprint().equals(capture.fingerprint()),c.lines().size(),totals,c.issues(),PayBreakdown.of(c)));
            var people = new HashMap<UUID,PayrollDtos.PayrollLine>();
            for (var line : c.lines()) {
                people.put(line.employeeId(),line); var linePaid = paidByEmployee.getOrDefault(line.employeeId(),BigDecimal.ZERO);
                var cost = PayBreakdown.of(line).employerCost();
                var amounts = new Totals(line.grossPay(),line.netPay(),line.employeeCpf(),line.employerCpf(),line.sdl(),cost,PayrollReportValues.money(linePaid),line.netPay()==null?null:PayrollReportValues.money(new BigDecimal(line.netPay()).subtract(linePaid)));
                employees.add(new EmployeeRow(company,companyName,b.id(),b.status(),line.employeeId(),line.employeeNo(),line.employeeName(),line.workSeconds(),line.overtimeSeconds(),amounts,PayBreakdown.of(line),line.profile()==null||line.profile().payment()==null?"":line.profile().payment().method(),line.profile()==null||line.profile().payment()==null?"":line.profile().payment().bank()));
            }
            for (var p : paymentRecords) {
                var line = people.get(p.employeeId()); if (line == null) throw new IllegalStateException("Payment missing payroll line");
                payments.add(new PaymentRow(company,companyName,b.id(),p.employeeId(),line.employeeNo(),line.employeeName(),p.id(),p.paymentDate(),p.method(),p.reference(),p.amount(),PayrollReportValues.money(new BigDecimal(p.amount()).multiply(BigDecimal.valueOf(p.reversesId()==null?1:-1))),p.reversesId(),p.bank()));
            }
            PayrollReportValues.limit(employees.size(),10000); PayrollReportValues.limit(payments.size(),20000);
        }
        String fingerprint = hash(json.write(List.of(period,ids,companies,employees,payments,revisions)));
        return new Report(period,"SGD",ids,List.copyOf(companies),List.copyOf(employees),List.copyOf(payments),aggregate(companies,employees,true),aggregate(companies,employees,false),fingerprint,clock.instant());
    }
    public String export(CurrentActor actor, UUID store, String month, List<UUID> companies, String kind, String fingerprint) {
        if (kind == null || !Set.of("companies","employees","payments","items").contains(kind)) throw WorkforceException.invalid();
        var report = read(actor,store,month,companies); requireCurrent(report,fingerprint);
        return PayrollCsv.render(report,kind);
    }
    static void requireCurrent(Report report, String fingerprint) {
        if (!report.fingerprint().equals(fingerprint)) throw new WorkforceException("WORKFORCE_REPORT_CHANGED",409);
    }
    private Aggregate aggregate(List<CompanyRow> companies, List<EmployeeRow> people, boolean frozen) {
        var rows = companies.stream().filter(c -> c.totals()!=null && (frozen?PayrollReportValues.frozen(c.status()):c.status().equals("calculated"))).toList();
        var ids = rows.stream().map(CompanyRow::companyId).collect(java.util.stream.Collectors.toSet());
        var lines = people.stream().filter(e -> ids.contains(e.companyId())).toList();
        return new Aggregate(rows.size(),(int)lines.stream().map(EmployeeRow::employeeId).distinct().count(),lines.size(),PayrollReportValues.sum(rows.stream().map(CompanyRow::totals).toList()),PayBreakdown.sum(rows.stream().map(CompanyRow::breakdown).toList()));
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
