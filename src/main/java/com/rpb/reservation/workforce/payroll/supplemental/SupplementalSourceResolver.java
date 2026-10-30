package com.rpb.reservation.workforce.payroll.supplemental;

import static com.rpb.reservation.workforce.payroll.supplemental.SupplementalDtos.*;
import static com.rpb.reservation.workforce.payroll.supplemental.SupplementalMoney.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.statutory.annual.*;
import java.time.*;
import java.math.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads originals without acquiring their batch lock: never creates source -> batch inversion. */
public final class SupplementalSourceResolver {
 private final JdbcTemplate jdbc;private final WorkforceJson json;private final Clock clock;private final PayrollRepository payroll;private final AnnualWageRepository annual;private final SupplementalRepository repository;
 public SupplementalSourceResolver(JdbcTemplate jdbc,WorkforceJson json,Clock clock){this.jdbc=jdbc;this.json=json;this.clock=clock;payroll=new PayrollRepository(jdbc,json);annual=new AnnualWageRepository(jdbc,json,clock);repository=new SupplementalRepository(jdbc,json);}
 public SourcePage source(UUID tenant,UUID company,UUID employee,YearMonth month){
  var b=payroll.forMonth(tenant,company,month).orElse(null);
  if(b!=null&&b.status().equals("published")&&b.calculation()!=null){var line=b.calculation().lines().stream().filter(l->l.employeeId().equals(employee)).findFirst().orElse(null);if(line!=null){var payments=payroll.payments(tenant,b.id());var paid=payments.stream().filter(p->p.employeeId().equals(employee)).map(p->number(p.amount()).multiply(BigDecimal.valueOf(p.reversesId()==null?1:-1))).reduce(BigDecimal.ZERO,BigDecimal::add);return new SourcePage(company,employee,month.toString(),new Source("normal",b.id(),null,repository.hash(line)),line.grossPay(),line.netPay()==null?null:money(number(line.netPay()).subtract(paid)),List.of());}}
  var opening=annual.opening(tenant,company,employee,month).orElse(null);if(opening!=null&&Set.of("actual","known-zero").contains(opening.fact().state())){var f=opening.fact();return new SourcePage(company,employee,month.toString(),new Source("opening",null,opening.version(),repository.hash(f)),money(number(f.rawOW()).add(AnnualWageRepository.aw(f.events()))),null,List.of());}
  var person=new com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository(jdbc).find(tenant,employee).orElseThrow(WorkforceException::missing);
  if(person.leaveDate()!=null&&month.atDay(1).isAfter(person.leaveDate())&&payroll.employmentPeriods(tenant,company,employee,YearMonth.from(person.leaveDate())).stream().anyMatch(p->!person.leaveDate().isBefore(p.from())&&!person.leaveDate().isAfter(p.to()))){var f=annual.capture(tenant,company,employee,month.getYear()).months().get(month.getMonthValue()-1);if(approvedIdentity(tenant,company,employee,month)&&f.cpf()!=null&&f.policy()!=null&&f.source().startsWith("employment:")){var hash=repository.hash(Arrays.asList(person,f.cpf(),f.policy(),f.source()));return new SourcePage(company,employee,month.toString(),new Source("employment-zero",null,null,hash),"0.00","0.00",List.of());}}
  return new SourcePage(company,employee,month.toString(),null,null,null,List.of("SUPPLEMENTAL_SOURCE_MISSING"));
 }
 private boolean approvedIdentity(UUID tenant,UUID company,UUID employee,YearMonth month){var configs=new PayrollConfigurationRepository(jdbc,json,clock);var p=configs.employeeProfiles(tenant,company,employee).stream().filter(v->!v.effectiveFrom().isAfter(month.atDay(1))).max(Comparator.comparing(PayrollDtos.PayProfile::effectiveFrom)).orElse(null);return p!=null&&p.cpf()!=null&&configs.effective().evidence(tenant,company,"profile",p.id(),null,p.version(),p)!=null;}
 public SourcePage validate(UUID tenant,UUID company,UUID employee,Proposal p){var result=source(tenant,company,employee,PayrollProfileValidation.month(p.originMonth()));if(result.source()==null)throw new WorkforceException("SUPPLEMENTAL_SOURCE_MISSING",409);if(!result.source().equals(p.source()))throw new WorkforceException("SUPPLEMENTAL_SOURCE_CHANGED",409);if(result.source().kind().equals("employment-zero"))for(var item:p.items()){var e=item.eventEvidence();if(!e.classification().equals("AW"))throw new WorkforceException("WAGE_EVENT_PERIOD_UNSUPPORTED",409);long covered=0;for(var m=YearMonth.from(e.serviceFrom());!m.isAfter(YearMonth.from(e.serviceTo()));m=m.plusMonths(1))for(var period:payroll.employmentPeriods(tenant,company,employee,m)){var from=period.from().isBefore(e.serviceFrom())?e.serviceFrom():period.from();var to=period.to().isAfter(e.serviceTo())?e.serviceTo():period.to();if(!to.isBefore(from))covered+=java.time.temporal.ChronoUnit.DAYS.between(from,to)+1;}if(covered!=java.time.temporal.ChronoUnit.DAYS.between(e.serviceFrom(),e.serviceTo())+1)throw new WorkforceException("SUPPLEMENTAL_EMPLOYMENT_EVIDENCE_MISSING",409);}return result;}
}
