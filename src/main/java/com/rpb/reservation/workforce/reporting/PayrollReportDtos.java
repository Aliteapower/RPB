package com.rpb.reservation.workforce.reporting;

import com.rpb.reservation.workforce.payroll.PayBreakdown;
import java.time.*;
import java.util.*;

/** Report DTOs deliberately exclude salary rules, CPF identity and management notes. */
public final class PayrollReportDtos {
    private PayrollReportDtos() {}
    public record Totals(String gross, String net, String employeeCpf, String employerCpf,
                         String sdl, String employerCost, String paid, String remaining) {}
    public record Aggregate(int companyCount, int employeeCount, int payrollRows, Totals totals, PayBreakdown.Values breakdown) {}
    public record CompanyRow(UUID companyId, String companyName, UUID batchId, String status,
                             boolean sourceChanged, int employeeCount, Totals totals, List<String> issues, PayBreakdown.Values breakdown) {}
    public record EmployeeRow(UUID companyId, String companyName, UUID batchId, String status,
                              UUID employeeId, String employeeNo, String employeeName,
                              long workSeconds, long overtimeSeconds, Totals totals, PayBreakdown.Values breakdown, String paymentMethod, String bank) {}
    public record PaymentRow(UUID companyId, String companyName, UUID batchId, UUID employeeId,
                             String employeeNo, String employeeName, UUID paymentId, LocalDate paymentDate,
                             String method, String reference, String amount, String signedAmount, UUID reversesId, String bank) {}
    public record Report(String month, String currency, List<UUID> companyIds, List<CompanyRow> companies,
                         List<EmployeeRow> employees, List<PaymentRow> payments, Aggregate frozen,
                         Aggregate forecast, String fingerprint, Instant generatedAt) {}
    public record CompareWrite(UUID companyId, String month, String fingerprint, Totals expected) {}
    public record Difference(String field, String expected, String actual, String difference) {}
    public record Comparison(Report report, boolean matched, List<Difference> items) {}
}
