package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.payroll.PayrollWorkbenchDtos.*;
import java.math.BigDecimal;import java.util.*;
/** Read-only accounting of the existing ledger. No float arithmetic or second payment truth. */
public final class PayrollPaymentProjection {
 private PayrollPaymentProjection(){}
 public static PaymentSummary summarize(PayrollSnapshot snapshot,List<Payment> payments){return summarize(snapshot,payments,false);}
 public static PaymentSummary summarize(PayrollSnapshot snapshot,List<Payment> payments,boolean confirmedAmounts){
  // A blocked preview can contain zero placeholders, even after void. Only confirmed/published wages bypass issues.
  if(snapshot==null||!confirmedAmounts&&((snapshot.issues()!=null&&!snapshot.issues().isEmpty())||snapshot.lines().stream().anyMatch(l->l.issues()!=null&&!l.issues().isEmpty())))return new PaymentSummary(false,null,null,null,0,0,0,0,List.of());
  var lines=new ArrayList<PaymentLine>();BigDecimal due=BigDecimal.ZERO,paid=BigDecimal.ZERO;int unpaid=0,partial=0,settled=0,zero=0;
  for(var line:snapshot.lines()){
   var net=new BigDecimal(line.netPay());var registered=PayrollValues.paid(payments,line.employeeId());var remaining=net.subtract(registered);
   String state;if(net.signum()==0){state="zero";zero++;}else if(registered.signum()==0){state="unpaid";unpaid++;}else if(remaining.signum()==0){state="settled";settled++;}else {state="partial";partial++;}
   due=due.add(net);paid=paid.add(registered);lines.add(new PaymentLine(line.employeeId(),line.employeeName(),PayrollValues.money(net),PayrollValues.money(registered),PayrollValues.money(remaining),state));
  }
  return new PaymentSummary(true,PayrollValues.money(due),PayrollValues.money(paid),PayrollValues.money(due.subtract(paid)),unpaid,partial,settled,zero,List.copyOf(lines));
 }
}
