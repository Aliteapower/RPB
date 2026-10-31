package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.*;import java.util.*;
/** The existing configuration validation, shared by publication without changing formulas. */
public final class PayrollRules {
 private PayrollRules(){}
    public static Rule normalize(Rule r){
        if(r==null||!Set.of("monthly","daily","hourly").contains(r.basis()==null?"":r.basis())||r.items()==null||r.items().size()>20)throw WorkforceException.invalid();
        BigDecimal rate=decimal(r.rate()),day=decimal(r.dayHours()),week=decimal(r.weekHours()),multiplier=decimal(r.overtimeMultiplier());
        if(day.signum()<=0||day.compareTo(BigDecimal.valueOf(r.overtimeEnabled()?12:24))>0||week.signum()<=0||week.compareTo(BigDecimal.valueOf(r.overtimeEnabled()?44:168))>0||multiplier.signum()<=0||multiplier.compareTo(BigDecimal.TEN)>0||(r.overtimeEnabled()&&multiplier.compareTo(new BigDecimal("1.50"))<0))throw WorkforceException.invalid();
        var generation=PayrollGeneration.validate(r);
        var items=r.items().stream().map(i->{if(i==null||i.eventEvidence()!=null||"wage_event".equals(i.kind())||!PayItemKinds.supported(i.kind()))throw WorkforceException.invalid();return new Item(text(i.label(),100,true),i.kind(),decimal(i.amount()).toPlainString(),PayrollGeneration.proration(i),i.itemId(),PayrollValues.source(i.source()));}).toList();
        return new Rule(r.basis(),rate.toPlainString(),day.toPlainString(),week.toPlainString(),r.overtimeEnabled(),multiplier.toPlainString(),items,generation);
    }
    private static BigDecimal decimal(String value){
        if(value==null||!value.matches("[0-9]{1,8}(\\.[0-9]{1,2})?"))throw WorkforceException.invalid();
        return new BigDecimal(value).setScale(2,RoundingMode.UNNECESSARY);
    }
 private static String text(String s,int max,boolean required){return PayrollValues.text(s,max,required);}
}
