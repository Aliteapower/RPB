package com.rpb.reservation.workforce.payroll.governance;

import com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.payroll.PayItemKinds;
import java.util.*;

/** Structural review hints only; no amount thresholds or calculation rules. */
final class PayChangeRiskHints {
 private PayChangeRiskHints(){}
 static boolean newDeduction(PayChangeDtos.Content c){
  Rule before=c.beforeProfile()!=null?c.beforeProfile().rule():c.adoption()!=null?c.adoption().rule():null;
  Rule after=c.profile()!=null?c.profile().rule():c.derivedRule();
  if(added(before==null?List.of():before.items(),after==null?List.of():after.items()))return true;
  return c.months().stream().anyMatch(m->added(m.before().items(),m.after().items()));
 }
 private static boolean added(List<Item> before,List<Item> after){var counts=new HashMap<String,Integer>();for(var item:before)if(PayItemKinds.DEDUCTIONS.contains(item.kind()))counts.merge(item.kind(),1,Integer::sum);for(var item:after)if(PayItemKinds.DEDUCTIONS.contains(item.kind())&&counts.merge(item.kind(),-1,Integer::sum)<0)return true;return false;}
}
