package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.*;import java.time.*;import java.util.*;
/** Canonical validation/formatting at the payroll boundary; never accepts binary floating amounts. */
final class PayrollValues {
 private PayrollValues(){}
 static BigDecimal decimal(String s){if(s==null||!s.matches("[0-9]{1,8}(\\.[0-9]{1,2})?"))throw WorkforceException.invalid();return new BigDecimal(s);}
 static String money(BigDecimal n){return n.setScale(2,RoundingMode.HALF_UP).toPlainString();}
 static String text(String s,int max,boolean required){String v=s==null?"":s.trim();if(v.length()>max||(required&&v.isBlank()))throw WorkforceException.invalid();return v;}
 static YearMonth month(String s){try{if(s==null||!s.matches("[0-9]{4}-[0-9]{2}"))throw WorkforceException.invalid();var m=YearMonth.parse(s);if(m.getYear()<2000||m.getYear()>2100)throw new WorkforceException("WORKFORCE_PAYROLL_UNSUPPORTED",400);return m;}catch(DateTimeException e){throw WorkforceException.invalid();}}
 static List<Item> items(List<Item> items){if(items==null||items.size()>20)throw WorkforceException.invalid();return items.stream().map(i->{if(i==null||!PayItemKinds.supported(i.kind()))throw WorkforceException.invalid();return new Item(text(i.label(),100,true),i.kind(),money(decimal(i.amount())));}).toList();}
 static InputWrite input(InputWrite c){if(c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.unitsReviewed()==null||c.specialDaysReviewed()==null)throw WorkforceException.invalid();if(Boolean.TRUE.equals(c.cdacAutomatic())&&c.cdacReview()!=null)throw WorkforceException.invalid();String working=days(c.workingDays(),true),payable=days(c.payableDays(),false);if(working!=null&&payable!=null&&decimal(payable).compareTo(decimal(working))>0)throw WorkforceException.invalid();return new InputWrite(c.expectedVersion(),working,payable,c.unitsReviewed(),c.specialDaysReviewed(),items(c.items()),text(c.note(),500,false),CdacPayroll.review(c.cdacReview()),c.cdacAutomatic());}
 private static String days(String s,boolean whole){if(s==null||s.isBlank())return null;var n=decimal(s);if(n.compareTo(BigDecimal.valueOf(31))>0||(whole&&(n.signum()==0||n.stripTrailingZeros().scale()>0))||(!whole&&n.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale()>0))throw WorkforceException.invalid();return money(n);}
 static BigDecimal paid(List<Payment> payments,UUID employee){return payments.stream().filter(p->p.employeeId().equals(employee)).map(p->new BigDecimal(p.amount()).multiply(BigDecimal.valueOf(p.reversesId()==null?1:-1))).reduce(BigDecimal.ZERO,BigDecimal::add);}
}
