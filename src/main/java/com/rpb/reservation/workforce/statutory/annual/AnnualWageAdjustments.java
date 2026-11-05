package com.rpb.reservation.workforce.statutory.annual;

import static com.rpb.reservation.workforce.statutory.annual.AnnualWageDtos.*;
import static com.rpb.reservation.workforce.statutory.annual.AnnualWageCalculator.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.payroll.supplemental.SupplementalDtos;
import java.math.*;import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Additive source chain over originals; never replaces an opening or frozen normal month. */
final class AnnualWageAdjustments {
 private final JdbcTemplate jdbc;private final WorkforceJson json;
 AnnualWageAdjustments(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
 Context apply(UUID tenant,Context source,AnnualWageRepository repository){
  var batches=jdbc.query("select snapshot from workforce_supplemental_batches where tenant_id=? and company_id=? and employee_id=? and extract(year from origin_month)=? and status in ('confirmed','published') order by created_at,id",(r,n)->json.read(r.getString(1),SupplementalDtos.Batch.class),tenant,source.companyId(),source.employeeId(),source.year());
  record Delta(String month,String component,BigDecimal amount){}
  var deltas=jdbc.query("select origin_month,component,sum(amount) from workforce_statutory_liability_entries where tenant_id=? and company_id=? and employee_id=? and extract(year from origin_month)=? and component in ('cpf_employee','cpf_employer') group by origin_month,component order by origin_month,component",(r,n)->new Delta(YearMonth.from(r.getObject(1,LocalDate.class)).toString(),r.getString(2),r.getBigDecimal(3)),tenant,source.companyId(),source.employeeId(),source.year());
  if(batches.isEmpty()&&deltas.isEmpty())return source;var facts=new ArrayList<MonthFact>();
  for(var f:source.months()){var ow=f.rawOW()==null?null:amount(f.rawOW());var events=new ArrayList<>(f.events());for(var b:batches)if(b.originMonth().equals(f.month()))for(var i:b.proposal().items()){events.add(new WageEvent(i.eventEvidence(),i.amount(),"supplemental:"+b.id()));if(ow!=null&&i.eventEvidence().classification().equals("OW"))ow=ow.add(amount(i.amount()));}
   String ee=f.recognisedEmployeeCpf(),er=f.recognisedEmployerCpf();for(var d:deltas)if(d.month().equals(f.month())){if(d.component().equals("cpf_employee")&&ee!=null)ee=money(new BigDecimal(ee).add(d.amount()));if(d.component().equals("cpf_employer")&&er!=null)er=money(new BigDecimal(er).add(d.amount()));}
   facts.add(AnnualWageRepository.fact(YearMonth.parse(f.month()),events.size()>f.events().size()?"actual":f.state(),ow,f.cpf(),f.policy(),ee,er,f.source(),events,f.issues()));
  }return new Context(source.companyId(),source.employeeId(),source.year(),source.version(),repository.hash(List.of(source.fingerprint(),batches,deltas)),List.copyOf(facts),source.issues(),source.writable(),source.openingMonths());
 }
}
