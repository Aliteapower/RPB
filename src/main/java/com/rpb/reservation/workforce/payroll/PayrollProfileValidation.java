package com.rpb.reservation.workforce.payroll;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;import java.util.*;
/** Shared pure normalization. Does not write, grant access, or calculate wages. */
public final class PayrollProfileValidation {
 private PayrollProfileValidation(){}
 public static CpfProfile cpf(CpfProfile p,LocalDate employeeBirthDate,Clock clock){
  if(p!=null&&!"foreign".equals(p.citizenship())&&employeeBirthDate!=null)p=new CpfProfile(p.citizenship(),employeeBirthDate,p.prDate(),p.prMode(),p.electionReference(),p.prDocumentId());
  if(p==null||!Set.of("citizen","spr","foreign").contains(p.citizenship()==null?"":p.citizenship())||!Set.of("GG","FG","FF").contains(p.prMode()==null?"":p.prMode()))throw WorkforceException.invalid();
  var today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore")));if((!p.citizenship().equals("foreign")&&p.birthDate()==null)||(p.birthDate()!=null&&p.birthDate().isAfter(today)))throw WorkforceException.invalid();
  var note=PayrollValues.text(p.electionReference(),200,false);if(p.citizenship().equals("spr")){if(p.prDate()==null||p.prDate().isAfter(today)||p.prDate().isBefore(p.birthDate())||(!p.prMode().equals("GG")&&note.isBlank()))throw WorkforceException.invalid();}else if(p.prDate()!=null||p.prDocumentId()!=null)throw WorkforceException.invalid();
  return new CpfProfile(p.citizenship(),p.birthDate(),p.prDate(),p.prMode(),note,p.prDocumentId());
 }
 public static PayrollDtos.InputWrite input(PayrollDtos.InputWrite input){return PayrollValues.input(input);}
 public static YearMonth month(String value){return PayrollValues.month(value);}
 public static String text(String value,int max,boolean required){return PayrollValues.text(value,max,required);}
}
