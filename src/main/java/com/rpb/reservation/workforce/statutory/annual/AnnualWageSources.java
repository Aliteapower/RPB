package com.rpb.reservation.workforce.statutory.annual;

import static com.rpb.reservation.workforce.statutory.annual.AnnualWageDtos.*;
import static com.rpb.reservation.workforce.statutory.annual.AnnualWageCalculator.*;
import static com.rpb.reservation.workforce.statutory.annual.AnnualWageRepository.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.statutory.*;
import java.math.*;import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Versioned annual read model: confirmed facts, explicit openings, and proved fixed-pay forecasts. */
final class AnnualWageSources {
 private final JdbcTemplate jdbc;private final WorkforceJson json;private final Clock clock;private final AnnualWageRepository repository;
 AnnualWageSources(JdbcTemplate jdbc,WorkforceJson json,Clock clock,AnnualWageRepository repository){this.jdbc=jdbc;this.json=json;this.clock=clock;this.repository=repository;}
 public Context capture(UUID tenant,UUID company,UUID employee,int year){return capture(tenant,company,employee,year,null,null);}
 public Context captureForPayroll(UUID tenant,UUID company,UUID employee,YearMonth month){return capture(tenant,company,employee,month.getYear(),month,null);}
 public Context captureForPayrollAssuming(UUID tenant,UUID company,UUID employee,YearMonth month,PayrollSources.Assumptions assumptions){var request=new com.rpb.reservation.workforce.payroll.governance.EffectivePayGovernance(jdbc,json).assumption(tenant,company,month,assumptions);return capture(tenant,company,employee,month.getYear(),month,request);}
 private Context capture(UUID tenant,UUID company,UUID employee,int year,YearMonth excluded,com.rpb.reservation.workforce.payroll.governance.PayChangeDtos.Request hypothesis){
  var saved=repository.saved(tenant,company,employee,year);var openings=new HashMap<String,OpeningMonth>();saved.write().months().forEach(f->openings.put(f.month(),f));
  var payroll=new PayrollRepository(jdbc,json);var configs=new PayrollConfigurationRepository(jdbc,json,clock);var policies=new StatutoryPolicyRepository(jdbc,json);var person=new com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository(jdbc).find(tenant,employee).orElseThrow(WorkforceException::missing);
  var plans=new ArrayList<>(configs.employeeProfiles(tenant,company,employee));if(hypothesis!=null&&hypothesis.employeeId().equals(employee)&&hypothesis.content().profile()!=null){var proposal=hypothesis.content().profile();plans.removeIf(p->p.effectiveFrom().equals(proposal.effectiveFrom()));plans.add(proposal);}
  var facts=new ArrayList<MonthFact>();var tokens=new ArrayList<Object>();tokens.add(saved);tokens.add(person);tokens.add(plans);if(hypothesis!=null)tokens.add(hypothesis);
  var today=YearMonth.now(clock.withZone(ZoneId.of("Asia/Singapore")));
  for(int i=1;i<=12;i++){
   var month=YearMonth.of(year,i);var batch=payroll.forMonth(tenant,company,month).orElse(null);
   var line=month.equals(excluded)||batch==null||batch.calculation()==null||!Set.of("confirmed","published").contains(batch.status())?null:batch.calculation().lines().stream().filter(l->l.employeeId().equals(employee)).findFirst().orElse(null);
   if(line!=null){var events=events(line.input(),"batch:"+batch.id()+"/v"+batch.version());var aw=aw(events);var policy=AnnualWagePolicySelection.target(policies,tenant,month,batch.calculation().statutoryPolicy());var ow=amount(line.grossPay()).subtract(aw);facts.add(fact(month,"actual",ow,line.profile().cpf(),policy,line.employeeCpf(),line.employerCpf(),"batch:"+batch.id()+"/v"+batch.version(),events,List.of()));tokens.add(batch.calculation());continue;}
   var external=openings.get(month.toString());if(external!=null){var originalPolicy=policies.versions(tenant,external.policyId()).stream().filter(p->p.version()==external.policyVersion()).findFirst().orElse(null);var policy=AnnualWagePolicySelection.target(policies,tenant,month,originalPolicy);facts.add(fact(month,external.state(),amount(external.rawOW()),external.cpf(),policy,external.recognisedEmployeeCpf(),external.recognisedEmployerCpf(),"opening:v"+saved.version()+":"+external.evidenceReference(),external.events(),List.of()));continue;}
   var p=plans.stream().filter(f->!f.effectiveFrom().isAfter(month.atDay(1))).max(Comparator.comparing(PayProfile::effectiveFrom)).orElse(null);var policy=policies.resolve(tenant,month);var cpf=p==null?null:p.cpf();
   var input=configs.effective().monthly(tenant,company,employee,month);if(hypothesis!=null&&hypothesis.employeeId().equals(employee)){var proposal=hypothesis.content().months().stream().filter(m->m.month().equals(month.toString())).findFirst();if(proposal.isPresent())input=new com.rpb.reservation.workforce.payroll.governance.EffectivePayGovernance.ResolvedInput(proposal.get().after(),new com.rpb.reservation.workforce.payroll.governance.EffectivePayGovernance.Evidence("assumption",employee,proposal.get().after().version(),hypothesis.id(),hypothesis.revision(),hypothesis.hash()),null);}
   tokens.add(input);var proof=p==null?null:configs.effective().evidence(tenant,company,"profile",p.id(),null,p.version(),p);if(hypothesis!=null&&p!=null&&p.equals(hypothesis.content().profile()))proof=new com.rpb.reservation.workforce.payroll.governance.EffectivePayGovernance.Evidence("assumption",p.id(),p.version(),hypothesis.id(),hypothesis.revision(),hypothesis.hash());if(proof!=null)tokens.add(proof);
   if(month.atEndOfMonth().isBefore(person.hireDate())&&!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_company_history where tenant_id=? and employee_id=? and company_id=? and effective_date<=?)",Boolean.class,tenant,employee,company,month.atEndOfMonth()))||person.leaveDate()!=null&&month.atDay(1).isAfter(person.leaveDate())){var zeroCpf=cpf==null?new StatutoryDtos.CpfProfile("foreign",null,null,"GG",""):cpf;facts.add(fact(month,"known-zero",BigDecimal.ZERO,zeroCpf,policy,"0.00","0.00","employment:"+person.id()+"/v"+person.version(),List.of(),List.of()));continue;}
   var periods=payroll.employmentPeriods(tenant,company,employee,month);tokens.add(periods);
   if(!month.isBefore(today)&&p!=null&&proof!=null&&PayrollGeneration.fixed(p.rule())&&!periods.isEmpty()&&input.issue()==null){
    var eventList=events(input.input(),"approved:"+input.evidence());var ow=amount(p.rule().rate());for(var item:p.rule().items())if(PayItemKinds.INCOME.contains(item.kind()))ow=ow.add(amount(item.amount()));for(var item:input.input().items())if(PayItemKinds.INCOME.contains(item.kind())&&(item.eventEvidence()==null||item.eventEvidence().classification().equals("OW")))ow=ow.add(amount(item.amount()));facts.add(fact(month,"forecast",ow,cpf,policy,null,null,"approved-profile:"+p.id()+"/v"+p.version(),eventList,List.of()));
   }else facts.add(fact(month,"unknown",null,cpf,policy,null,null,"missing",events(input.input(),"approved:"+input.evidence()),List.of("ANNUAL_WAGES_INCOMPLETE")));
  }
  tokens.add(facts);return new AnnualWageAdjustments(jdbc,json).apply(tenant,new Context(company,employee,year,saved.version(),repository.hash(tokens),List.copyOf(facts),List.of(),false),repository);
 }
}
