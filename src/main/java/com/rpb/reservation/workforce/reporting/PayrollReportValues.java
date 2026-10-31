package com.rpb.reservation.workforce.reporting;

import static com.rpb.reservation.workforce.reporting.PayrollReportDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.*;
import java.time.*;
import java.util.*;

final class PayrollReportValues {
    static final List<String> FIELDS = List.of("gross", "net", "employeeCpf", "employerCpf", "sdl", "employerCost", "paid", "remaining");
    private PayrollReportValues() {}
    static YearMonth month(String value) {
        try {
            if (value == null || !value.matches("[0-9]{4}-[0-9]{2}")) throw WorkforceException.invalid();
            var month = YearMonth.parse(value);
            if (month.getYear() < 2000 || month.getYear() > 2100) throw new WorkforceException("WORKFORCE_PAYROLL_UNSUPPORTED", 400);
            return month;
        } catch (DateTimeException e) { throw WorkforceException.invalid(); }
    }
    static List<UUID> companies(List<UUID> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size()) throw WorkforceException.invalid();
        limit(ids.size(), 50);
        return ids.stream().sorted().toList();
    }
    static void limit(int count, int max) { if (count > max) throw new WorkforceException("WORKFORCE_REPORT_LIMIT", 413); }
    static String money(BigDecimal n) { return n==null?null:n.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    static BigDecimal decimal(String n) { return new BigDecimal(n); }
    static List<String> values(Totals t) { return Arrays.asList(t.gross(), t.net(), t.employeeCpf(), t.employerCpf(), t.sdl(), t.employerCost(), t.paid(), t.remaining()); }
    static Totals totals(List<String> v) { return new Totals(v.get(0), v.get(1), v.get(2), v.get(3), v.get(4), v.get(5), v.get(6), v.get(7)); }
    static Totals sum(Collection<Totals> rows) {
        var sums = new BigDecimal[8]; Arrays.fill(sums, BigDecimal.ZERO);
        for (var row : rows) { var v = values(row); for (int i = 0; i < 8; i++) sums[i] = sums[i]==null||v.get(i)==null?null:sums[i].add(decimal(v.get(i))); }
        return totals(Arrays.stream(sums).map(PayrollReportValues::money).toList());
    }
    static Totals expected(Totals raw) {
        if (raw == null || Arrays.asList(raw.gross(),raw.net(),raw.employeeCpf(),raw.employerCpf(),raw.sdl(),raw.employerCost(),raw.paid(),raw.remaining()).stream().anyMatch(v -> v == null || !v.matches("[0-9]{1,12}(\\.[0-9]{1,2})?"))) throw WorkforceException.invalid();
        return totals(values(raw).stream().map(v -> money(decimal(v))).toList());
    }
    static boolean frozen(String status) { return status.equals("confirmed") || status.equals("published"); }
}
