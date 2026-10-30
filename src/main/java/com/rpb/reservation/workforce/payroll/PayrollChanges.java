package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.payroll.PayrollProgressDtos.*;
import java.math.BigDecimal;
import java.util.*;

/** Pure snapshot comparison, never substitutes a missing calculation with a zero salary. */
public final class PayrollChanges {
    private PayrollChanges() {}
    public static Comparison unavailable(String month) {
        return new Comparison(false,month,null,null,null,null,List.of());
    }
    public static Comparison compare(PayrollSnapshot before,Long baselineVersion,PayrollSnapshot after) {
        if(before==null||after==null)return unavailable(before==null?null:before.month());
        var old=index(before);var current=index(after);var ids=new LinkedHashSet<>(current.keySet());ids.addAll(old.keySet());
        var changes=new ArrayList<LineChange>();
        boolean policy=!Objects.equals(before.policyVersion(),after.policyVersion())||!Objects.equals(before.statutoryPolicy(),after.statutoryPolicy());
        for(var id:ids){
            var a=old.get(id);var b=current.get(id);var reasons=new LinkedHashSet<String>();
            String kind=a==null?"added":b==null?"removed":"changed";
            if(a==null||b==null)reasons.add(kind);
            else {
                if(!Objects.equals(a.employeeName(),b.employeeName())||!Objects.equals(a.employeeNo(),b.employeeNo()))reasons.add("employee");
                if(!Objects.equals(a.profile(),b.profile()))reasons.add("profile");
                if(!Objects.equals(a.input(),b.input()))reasons.add("input");
                if(a.workSeconds()!=b.workSeconds()||a.normalSeconds()!=b.normalSeconds()||a.overtimeSeconds()!=b.overtimeSeconds()||a.paidLeaveSeconds()!=b.paidLeaveSeconds()||a.unpaidLeaveSeconds()!=b.unpaidLeaveSeconds())reasons.add("attendance");
                if(!Objects.equals(a.issues(),b.issues()))reasons.add("issues");
                if(policy)reasons.add("policy");
                if(!Objects.equals(a.sources(),b.sources()))reasons.add("source");
                if(!a.equals(b)&&reasons.isEmpty())reasons.add("source");
                if(reasons.isEmpty())continue;
            }
            changes.add(new LineChange(id,b==null?a.employeeName():b.employeeName(),kind,List.copyOf(reasons),
                    delta(a==null?null:a.basicPay(),b==null?null:b.basicPay()),delta(a==null?null:a.grossPay(),b==null?null:b.grossPay()),
                    delta(a==null?null:a.netPay(),b==null?null:b.netPay()),delta(a==null?null:a.employeeCpf(),b==null?null:b.employeeCpf()),cost(b).subtract(cost(a)).toPlainString()));
        }
        return new Comparison(true,before.month(),baselineVersion,delta(before.totalGross(),after.totalGross()),delta(before.totalNet(),after.totalNet()),delta(before.totalEmployerCost(),after.totalEmployerCost()),List.copyOf(changes));
    }
    private static Map<UUID,PayrollLine> index(PayrollSnapshot snapshot){var result=new LinkedHashMap<UUID,PayrollLine>();snapshot.lines().forEach(l->result.put(l.employeeId(),l));return result;}
    private static BigDecimal amount(String value){return value==null?BigDecimal.ZERO:new BigDecimal(value);}
    private static String delta(String before,String after){return amount(after).subtract(amount(before)).toPlainString();}
    private static BigDecimal cost(PayrollLine line){return line==null?BigDecimal.ZERO:amount(line.grossPay()).add(amount(line.employerCpf())).add(amount(line.sdl())).add(amount(line.reimbursements())).add(amount(line.employerCharges()));}
}
