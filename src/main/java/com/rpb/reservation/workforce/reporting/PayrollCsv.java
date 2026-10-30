package com.rpb.reservation.workforce.reporting;

import static com.rpb.reservation.workforce.reporting.PayrollReportDtos.*;
import java.util.*;
import com.rpb.reservation.workforce.payroll.PayBreakdown;
import com.rpb.reservation.workforce.payroll.PayItemKinds;

/** Fixed columns, explicit privacy projection, UTF-8 BOM and RFC4180 quoting. */
public final class PayrollCsv {
    private PayrollCsv() {}
    public static String text(String raw) {
        String value=raw==null?"":raw;
        String start=value.stripLeading();
        if (value.chars().anyMatch(c->c<32 && c!='\n') || (!start.isEmpty() && "=+@-".indexOf(start.charAt(0))>=0)) value="'"+value;
        return quote(value);
    }
    private static String quote(Object value) { return "\""+String.valueOf(value==null?"":value).replace("\"","\"\"")+"\""; }
    private static String nature(String status) { return PayrollReportValues.frozen(status)?"frozen":status.equals("calculated")?"forecast":"unavailable"; }
    public static String render(Report report,String kind) {
        var out=new StringBuilder("\uFEFF");
        if(kind.equals("companies")) {
            header(out,"month,currency,company_id,company_name,batch_id,status,nature,source_changed,employee_count,gross,net,employee_cpf,employer_cpf,company_sdl,employer_cost,recorded_paid,remaining,issues,basic,overtime,additional,allowance,cash_accommodation,reimbursements,other_deductions,cdac,employer_charges");
            for(var c:report.companies()) {
                var cells=new ArrayList<>(List.of(quote(report.month()),quote(report.currency()),quote(c.companyId()),text(c.companyName()),quote(c.batchId()),quote(c.status()),quote(nature(c.status())),quote(c.sourceChanged()),quote(c.employeeCount())));
                cells.addAll(amounts(c.totals())); cells.add(text(String.join(";",c.issues()))); cells.addAll(breakdown(c.breakdown())); row(out,cells);
            }
        } else if(kind.equals("employees")) {
            header(out,"month,currency,company_id,company_name,batch_id,status,nature,employee_id,employee_no,employee_name,work_seconds,overtime_seconds,gross,net,employee_cpf,employer_cpf,employee_sdl_unrounded,employee_cost_estimate,recorded_paid,remaining,basic,overtime,additional,allowance,cash_accommodation,reimbursements,other_deductions,cdac,employer_charges,planned_payment_method,planned_bank");
            for(var e:report.employees()) {
                var cells=new ArrayList<>(List.of(quote(report.month()),quote(report.currency()),quote(e.companyId()),text(e.companyName()),quote(e.batchId()),quote(e.status()),quote(nature(e.status())),quote(e.employeeId()),text(e.employeeNo()),text(e.employeeName()),quote(e.workSeconds()),quote(e.overtimeSeconds())));
                cells.addAll(amounts(e.totals())); cells.addAll(breakdown(e.breakdown()));cells.add(text(e.paymentMethod()));cells.add(text(e.bank()));row(out,cells);
            }
        } else if(kind.equals("items")){
            header(out,"month,currency,company_id,company_name,batch_id,status,nature,employee_no,employee_name,group,kind,label,amount");
            for(var e:report.employees())for(var item:allItems(e.breakdown()))row(out,List.of(quote(report.month()),quote(report.currency()),quote(e.companyId()),text(e.companyName()),quote(e.batchId()),quote(e.status()),quote(nature(e.status())),text(e.employeeNo()),text(e.employeeName()),quote(group(item.kind())),quote(item.kind()),text(item.label()),quote(item.amount())));
        } else {
            header(out,"month,currency,company_id,company_name,batch_id,employee_id,employee_no,employee_name,payment_id,payment_date,method,reference,amount,signed_amount,reverses_id,bank");
            for(var p:report.payments()) row(out,List.of(quote(report.month()),quote(report.currency()),quote(p.companyId()),text(p.companyName()),quote(p.batchId()),quote(p.employeeId()),text(p.employeeNo()),text(p.employeeName()),quote(p.paymentId()),quote(p.paymentDate()),quote(p.method()),text(p.reference()),quote(p.amount()),quote(p.signedAmount()),quote(p.reversesId()),text(p.bank())));
        }
        return out.toString();
    }
    private static List<com.rpb.reservation.workforce.payroll.PayrollDtos.Component> allItems(PayBreakdown.Values v){var items=new ArrayList<>(v.items());items.add(new com.rpb.reservation.workforce.payroll.PayrollDtos.Component("Employee CPF","employee_cpf",v.employeeCpf()));items.add(new com.rpb.reservation.workforce.payroll.PayrollDtos.Component("Employer CPF","employer_cpf",v.employerCpf()));items.add(new com.rpb.reservation.workforce.payroll.PayrollDtos.Component("SDL","sdl",v.sdl()));return items;}
    private static String group(String kind){return PayItemKinds.DEDUCTIONS.contains(kind)||kind.equals("employee_cpf")?"deductions":Set.of("employer_charge","employer_cpf","sdl").contains(kind)?"employer_costs":kind.equals("reimbursement")?"reimbursements":"income";}
    private static List<String> breakdown(PayBreakdown.Values v){return v==null?Collections.nCopies(9,quote("")):List.of(v.basic(),v.overtime(),v.additional(),v.allowance(),v.accommodation(),v.reimbursements(),v.otherDeductions(),v.cdac(),v.employerCharges()).stream().map(PayrollCsv::quote).toList();}
    private static List<String> amounts(Totals totals) { return totals==null?Collections.nCopies(8,quote("")):PayrollReportValues.values(totals).stream().map(PayrollCsv::quote).toList(); }
    private static void header(StringBuilder out,String header) { out.append(header).append("\r\n"); }
    private static void row(StringBuilder out,List<String> cells) { out.append(String.join(",",cells)).append("\r\n"); }
}
