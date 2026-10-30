package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import java.math.BigDecimal;import java.util.*;
/** Read-only accounting projection. Never recalculates or rewrites a saved payroll snapshot. */
public final class PayBreakdown {
 private PayBreakdown(){}
 public record Values(String basic,String overtime,String additional,String allowance,String accommodation,String reimbursements,String otherDeductions,String cdac,String employeeCpf,String employerCpf,String sdl,String employerCharges,String gross,String net,String employerCost,List<Component> items){}
 public static Values of(PayrollLine l){
  var items=l.components()==null?List.<Component>of():l.components();
  return new Values(l.basicPay(),l.overtimePay(),amount(items,"ow_additional"),amount(items,"ow_allowance"),amount(items,"ow_accommodation"),l.reimbursements(),amount(items,"deduction"),amount(items,"cdac"),l.employeeCpf(),l.employerCpf(),l.sdl(),l.employerCharges(),l.grossPay(),l.netPay(),money(n(l.grossPay()).add(n(l.reimbursements())).add(n(l.employerCpf())).add(n(l.sdl())).add(n(l.employerCharges()))),List.copyOf(items));
 }
 public static Values of(PayrollSnapshot snapshot){var v=sum(snapshot.lines().stream().map(PayBreakdown::of).toList());return new Values(v.basic(),v.overtime(),v.additional(),v.allowance(),v.accommodation(),v.reimbursements(),v.otherDeductions(),v.cdac(),v.employeeCpf(),v.employerCpf(),snapshot.totalSdl(),v.employerCharges(),snapshot.totalGross(),snapshot.totalNet(),snapshot.totalEmployerCost(),v.items());}
 public static Values sum(Collection<Values> rows){
  BigDecimal[] sums=new BigDecimal[15];Arrays.fill(sums,BigDecimal.ZERO);var items=new LinkedHashMap<List<String>,BigDecimal>();
  for(var v:rows){var values=List.of(v.basic(),v.overtime(),v.additional(),v.allowance(),v.accommodation(),v.reimbursements(),v.otherDeductions(),v.cdac(),v.employeeCpf(),v.employerCpf(),v.sdl(),v.employerCharges(),v.gross(),v.net(),v.employerCost());for(int i=0;i<sums.length;i++)sums[i]=sums[i].add(n(values.get(i)));for(var item:v.items())items.merge(List.of(item.kind(),item.label()),n(item.amount()),BigDecimal::add);}
  var s=Arrays.stream(sums).map(PayBreakdown::money).toList();return new Values(s.get(0),s.get(1),s.get(2),s.get(3),s.get(4),s.get(5),s.get(6),s.get(7),s.get(8),s.get(9),s.get(10),s.get(11),s.get(12),s.get(13),s.get(14),items.entrySet().stream().map(e->new Component(e.getKey().get(1),e.getKey().get(0),money(e.getValue()))).toList());
 }
 private static String amount(List<Component> rows,String kind){return money(rows.stream().filter(i->i.kind().equals(kind)).map(i->n(i.amount())).reduce(BigDecimal.ZERO,BigDecimal::add));}
 private static BigDecimal n(String s){return new BigDecimal(s);}
 private static String money(BigDecimal n){return n.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();}
}
