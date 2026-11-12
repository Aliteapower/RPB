package com.rpb.reservation.workforce.payroll.segment;
import com.rpb.reservation.workforce.payroll.*;
import java.math.*;import java.util.*;
/** Decimal differences of cumulative results, never a second wage/statutory calculator. */
public final class SegmentMoney {
 private SegmentMoney(){}
 public static BigDecimal n(String s){return new BigDecimal(s);}
 public static String money(BigDecimal x){return x.setScale(2,RoundingMode.HALF_UP).toPlainString();}
 public static PayBreakdown.Values sum(Collection<PayBreakdown.Values> rows){var v=PayBreakdown.sum(rows);var sdl=rows.stream().map(x->n(x.sdl())).reduce(BigDecimal.ZERO,BigDecimal::add);return new PayBreakdown.Values(v.basic(),v.overtime(),v.additional(),v.allowance(),v.accommodation(),v.reimbursements(),v.otherDeductions(),v.cdac(),v.employeeCpf(),v.employerCpf(),sdl.stripTrailingZeros().toPlainString(),v.employerCharges(),v.gross(),v.net(),v.employerCost(),v.items());}
 public static PayBreakdown.Values zero(){return PayBreakdown.sum(List.of());}
 public static PayBreakdown.Values difference(PayBreakdown.Values a,PayBreakdown.Values b){
  var before=new HashMap<String,BigDecimal>();for(var i:b.items())before.merge(i.kind()+"\n"+i.label(),n(i.amount()),BigDecimal::add);
  var after=new LinkedHashMap<String,BigDecimal>();for(var i:a.items())after.merge(i.kind()+"\n"+i.label(),n(i.amount()),BigDecimal::add);
  before.keySet().forEach(k->after.putIfAbsent(k,BigDecimal.ZERO));
  var items=after.entrySet().stream().map(e->{int i=e.getKey().indexOf('\n');return new PayrollDtos.Component(e.getKey().substring(i+1),e.getKey().substring(0,i),d(e.getValue().toPlainString(),before.getOrDefault(e.getKey(),BigDecimal.ZERO).toPlainString()));}).toList();
  return new PayBreakdown.Values(d(a.basic(),b.basic()),d(a.overtime(),b.overtime()),d(a.additional(),b.additional()),d(a.allowance(),b.allowance()),d(a.accommodation(),b.accommodation()),d(a.reimbursements(),b.reimbursements()),d(a.otherDeductions(),b.otherDeductions()),d(a.cdac(),b.cdac()),d(a.employeeCpf(),b.employeeCpf()),d(a.employerCpf(),b.employerCpf()),d(a.sdl(),b.sdl()),d(a.employerCharges(),b.employerCharges()),d(a.gross(),b.gross()),d(a.net(),b.net()),d(a.employerCost(),b.employerCost()),items);
 }
 private static String d(String a,String b){return a==null||b==null?null:n(a).subtract(n(b)).setScale(Math.max(2,Math.max(n(a).scale(),n(b).scale()))).toPlainString();}
 public static boolean negative(PayBreakdown.Values v){return Arrays.asList(v.basic(),v.overtime(),v.additional(),v.allowance(),v.accommodation(),v.reimbursements(),v.otherDeductions(),v.cdac(),v.employeeCpf(),v.employerCpf(),v.sdl(),v.employerCharges(),v.gross(),v.net()).stream().anyMatch(x->x==null||n(x).signum()<0);}
}
