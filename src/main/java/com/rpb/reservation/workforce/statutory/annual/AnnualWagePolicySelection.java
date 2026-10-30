package com.rpb.reservation.workforce.statutory.annual;

import com.rpb.reservation.workforce.statutory.*;
import java.time.YearMonth;
import java.util.UUID;

/** A deliberately enabled original-period V2 can supply AW capability to legacy V1 targets.
 * The immutable original payroll/opening retains its own policy and recognised liability. */
final class AnnualWagePolicySelection {
 private AnnualWagePolicySelection(){}
 static StatutoryPolicies.Policy target(StatutoryPolicyRepository policies,UUID tenant,YearMonth month,StatutoryPolicies.Policy original){
  if(original==null||original.config().schemaVersion()!=1||original.config().aw()!=null)return original;
  var selected=policies.resolve(tenant,month);
  if(selected==null||selected.year()!=month.getYear()||original.year()!=month.getYear()||!selected.currency().equals(original.currency())||selected.config().schemaVersion()!=2||selected.config().aw()==null)return original;
  return selected;
 }
}
