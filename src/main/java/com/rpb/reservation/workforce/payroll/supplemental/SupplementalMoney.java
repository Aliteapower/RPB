package com.rpb.reservation.workforce.payroll.supplemental;

import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.*;
import java.util.*;

/** Ledger arithmetic only. CPF and SDL target rules remain in StatutoryCalculator. */
public final class SupplementalMoney {
 private SupplementalMoney(){}
 public record Balance(String target,String recognised,String deducted,String remitted,String liabilityDelta,String deductionDelta,String remittanceBalance){}
 public static BigDecimal number(String s){if(s==null)return null;if(!s.matches("-?[0-9]{1,12}(\\.[0-9]{1,8})?"))throw WorkforceException.invalid();return new BigDecimal(s);}
 public static String money(BigDecimal n){return n==null?null:n.setScale(2,RoundingMode.HALF_UP).toPlainString();}
 public static String difference(String a,String b){return a==null||b==null?null:money(number(a).subtract(number(b)));}
 public static String add(String a,String b){return a==null||b==null?null:money(number(a).add(number(b)));}
 public static Balance balance(String target,String recognised,String deducted,String remitted){return new Balance(target,recognised,deducted,remitted,difference(target,recognised),difference(target,deducted),difference(target,remitted));}
 public static BigDecimal companySdlDelta(List<BigDecimal> people,BigDecimal recognised){return people.stream().reduce(BigDecimal.ZERO,BigDecimal::add).setScale(0,RoundingMode.FLOOR).subtract(recognised);}
 public static String cash(String gross,String cpf,String cdac){if(gross==null||cpf==null||cdac==null)return null;var value=number(gross).subtract(number(cpf).max(BigDecimal.ZERO)).subtract(number(cdac).max(BigDecimal.ZERO));return money(value.max(BigDecimal.ZERO));}
}
