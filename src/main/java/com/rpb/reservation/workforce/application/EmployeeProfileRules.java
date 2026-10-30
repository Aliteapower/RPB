package com.rpb.reservation.workforce.application;
import static com.rpb.reservation.workforce.application.WorkforceDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;
/** Optional profile fields retain legacy-client compatibility; employment corrections are explicit. */
public final class EmployeeProfileRules {
 private EmployeeProfileRules(){}
 public static EmployeeWrite extend(EmployeeWrite c,Employee old){
  if(c==null)throw WorkforceException.invalid();boolean legacy=c.birthDate()==null&&c.englishName()==null&&c.enabled()==null;
  var birth=legacy&&old!=null?old.birthDate():c.birthDate();String english=legacy&&old!=null?old.englishName():c.englishName()==null?"":c.englishName().trim();boolean enabled=c.enabled()==null?(old==null||old.enabled()):c.enabled();
  if(english.length()>100||birth!=null&&(birth.isBefore(LocalDate.of(1900,1,1))||c.hireDate()==null||birth.isAfter(c.hireDate())))throw WorkforceException.invalid();
  return copy(c,c.companyEffectiveDate(),birth,english,enabled);
 }
 public static EmployeeWrite effective(EmployeeWrite c,LocalDate date){return copy(c,date,c.birthDate(),c.englishName(),c.enabled());}
 private static EmployeeWrite copy(EmployeeWrite c,LocalDate effective,LocalDate birth,String english,Boolean enabled){return new EmployeeWrite(c.expectedVersion(),c.employeeNo(),c.name(),c.phone(),c.email(),c.jobTitle(),c.hireDate(),c.leaveDate(),c.status(),c.companyId(),effective,c.mainStoreId(),c.storeIds(),c.accountId(),c.username(),c.password(),c.positionId(),birth,english,enabled);}
}
