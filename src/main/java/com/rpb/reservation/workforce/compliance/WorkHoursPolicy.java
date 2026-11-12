package com.rpb.reservation.workforce.compliance;
import static com.rpb.reservation.workforce.compliance.ComplianceDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
/** Daily and weekly premium allocation without counting daily overtime twice. */
public final class WorkHoursPolicy {
 private WorkHoursPolicy(){}
 public record Split(long normalSeconds,long additionalSeconds,long overtimeSeconds){}
 public static Split split(Rules r,long actual,long contractedDaily,long comparableDaily,long priorWeeklyRegular,long weeklyLimit,boolean premium){
  ComplianceRules.validate(r);if(actual<0||contractedDaily<1||comparableDaily<contractedDaily||priorWeeklyRegular<0||weeklyLimit<1)throw WorkforceException.invalid();
  if(!premium)return new Split(Math.min(actual,contractedDaily),Math.max(0,actual-contractedDaily),0);
  long regular=Math.min(Math.min(actual,comparableDaily),Math.max(0,weeklyLimit-priorWeeklyRegular)),normal=Math.min(regular,contractedDaily);
  return new Split(normal,regular-normal,actual-regular);
 }
}
