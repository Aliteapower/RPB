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
    /** Old saved results retain deltas, but cannot prove factual causes without their before evidence. */
    public static Comparison readOnly(Comparison saved) {
        if(saved==null||!saved.available()||"snapshot_evidence_v1".equals(saved.reasonBasis()))return saved;
        var lines=saved.lines().stream().map(l->new LineChange(l.employeeId(),l.employeeName(),l.kind(),List.of("legacy_comparison"),l.basicPayDelta(),l.grossPayDelta(),l.netPayDelta(),l.employeeCpfDelta(),l.employerCostDelta())).toList();
        return new Comparison(saved.available(),saved.baselineMonth(),saved.baselineVersion(),saved.totalGrossDelta(),saved.totalNetDelta(),saved.totalEmployerCostDelta(),lines,"legacy_unverified");
    }
    public static Comparison compare(PayrollSnapshot before,Long baselineVersion,PayrollSnapshot after) {
        if(before==null||after==null)return unavailable(before==null?null:before.month());
        var old=index(before);var current=index(after);var ids=new LinkedHashSet<>(current.keySet());ids.addAll(old.keySet());
        var changes=new ArrayList<LineChange>();
        boolean policy=PayrollSourceReasons.policyChanged(before,after);
        for(var id:ids){
            var a=old.get(id);var b=current.get(id);var reasons=new LinkedHashSet<String>();
            String kind=a==null?"added":b==null?"removed":"changed";
            if(a==null||b==null)reasons.add(kind);
            else {
                if(!Objects.equals(a.employeeName(),b.employeeName())||!Objects.equals(a.employeeNo(),b.employeeNo()))reasons.add("employee");
                if(!Objects.equals(a.profile(),b.profile()))reasons.add("profile");
                if(PayrollSourceReasons.inputChanged(a.input(),b.input()))reasons.add("input");
                var refs=new HashMap<UUID,SourceRef>();a.sources().forEach(r->refs.put(r.scheduleId(),r));
                for(var ref:b.sources()){
                    var previous=refs.remove(ref.scheduleId());
                    if(previous==null)reasons.add("source");else reasons.addAll(PayrollSourceReasons.sourceKinds(previous,ref));
                }
                if(!refs.isEmpty())reasons.add("source");
                if(!Objects.equals(a.issues(),b.issues()))reasons.add("issues");
                if(policy)reasons.add("policy");
                if(!a.equals(b)&&reasons.isEmpty())reasons.add("source");
                if(reasons.isEmpty())continue;
            }
            changes.add(new LineChange(id,b==null?a.employeeName():b.employeeName(),kind,List.copyOf(reasons),
                    delta(a==null?"0":a.basicPay(),b==null?"0":b.basicPay()),delta(a==null?"0":a.grossPay(),b==null?"0":b.grossPay()),
                    delta(a==null?"0":a.netPay(),b==null?"0":b.netPay()),delta(a==null?"0":a.employeeCpf(),b==null?"0":b.employeeCpf()),cost(a)==null||cost(b)==null?null:cost(b).subtract(cost(a)).toPlainString()));
        }
        return new Comparison(true,before.month(),baselineVersion,delta(before.totalGross(),after.totalGross()),delta(before.totalNet(),after.totalNet()),delta(before.totalEmployerCost(),after.totalEmployerCost()),List.copyOf(changes),"snapshot_evidence_v1");
    }
    private static Map<UUID,PayrollLine> index(PayrollSnapshot snapshot){var result=new LinkedHashMap<UUID,PayrollLine>();snapshot.lines().forEach(l->result.put(l.employeeId(),l));return result;}
    private static BigDecimal amount(String value){return value==null?BigDecimal.ZERO:new BigDecimal(value);}
    private static String delta(String before,String after){return before==null||after==null?null:amount(after).subtract(amount(before)).toPlainString();}
    private static BigDecimal cost(PayrollLine line){return line==null?BigDecimal.ZERO:line.grossPay()==null||line.employerCpf()==null||line.sdl()==null?null:amount(line.grossPay()).add(amount(line.employerCpf())).add(amount(line.sdl())).add(amount(line.reimbursements())).add(amount(line.employerCharges()));}
}
