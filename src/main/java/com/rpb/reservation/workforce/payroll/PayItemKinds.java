package com.rpb.reservation.workforce.payroll;
import java.util.Set;
/** Supported accounting semantics; labels and amounts remain configurable in existing plans/inputs. */
public final class PayItemKinds {
 private PayItemKinds(){}
 public static final Set<String> INCOME=Set.of("ow_allowance","ow_additional","ow_accommodation","wage_event");
 public static final Set<String> DEDUCTIONS=Set.of("deduction","cdac");
 public static boolean supported(String kind){return kind!=null&&(INCOME.contains(kind)||DEDUCTIONS.contains(kind)||Set.of("reimbursement","employer_charge").contains(kind));}
}
