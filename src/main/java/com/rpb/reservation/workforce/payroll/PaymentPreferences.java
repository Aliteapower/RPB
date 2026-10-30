package com.rpb.reservation.workforce.payroll;
import com.rpb.reservation.workforce.domain.WorkforceException;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import java.util.Set;
public final class PaymentPreferences {
 private PaymentPreferences(){}
 public static boolean method(String method){return method!=null&&Set.of("cash","bank","paynow","other").contains(method);}
 public static PaymentPreference validate(PaymentPreference p){if(p==null)return null;if(!method(p.method()))throw WorkforceException.invalid();String bank=bank(p.bank());if(p.method().equals("cash")&&!bank.isEmpty()||p.method().equals("bank")&&bank.isEmpty())throw WorkforceException.invalid();return new PaymentPreference(p.method(),bank);}
 public static String bank(String bank){return PayrollValues.text(bank,100,false);}
}
