package com.rpb.reservation.workforce.payroll.governance;
import java.time.YearMonth;
/** Retrieval range only. A later effective plan can supersede an earlier proposal. */
public final class PayChangeMonthFilter {
 private PayChangeMonthFilter(){}
 public static boolean matches(PayChangeDtos.Request r,YearMonth month){return month==null||r.content().supplemental()!=null&&r.content().supplemental().stream().anyMatch(p->p.settlementMonth().equals(month.toString()))||r.content().months().stream().anyMatch(m->m.month().equals(month.toString()))||((r.content().profile()!=null||r.content().adoption()!=null)&&!r.effectiveDate().isAfter(month.atEndOfMonth()));}
}
