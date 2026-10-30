package com.rpb.reservation.workforce.statutory.settlement;

import static com.rpb.reservation.workforce.statutory.settlement.SettlementDtos.*;
import static com.rpb.reservation.workforce.payroll.supplemental.SupplementalMoney.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.payroll.supplemental.*;
import com.rpb.reservation.workforce.statutory.*;
import com.rpb.reservation.workforce.statutory.annual.*;
import java.time.*;import java.math.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Liability, deductions, actual remittances and legal recoverability are independent facts. */
public final class SettlementProjection {
 private final JdbcTemplate jdbc;private final WorkforceJson json;private final Clock clock;private final SettlementRepository repository;
 public SettlementProjection(JdbcTemplate jdbc,WorkforceJson json,Clock clock){this.jdbc=jdbc;this.json=json;this.clock=clock;repository=new SettlementRepository(jdbc,json);}
 public MonthBalance read(UUID tenant,UUID company,UUID employee,YearMonth month){var annual=new AnnualWageRepository(jdbc,json,clock);var context=annual.capture(tenant,company,employee,month.getYear());return read(tenant,company,employee,context,new AnnualWageCalculator().assess(context),month);}
 public MonthBalance read(UUID tenant,UUID company,UUID employee,AnnualWageDtos.Context context,AnnualWageDtos.Assessment assessment,YearMonth month){
  var f=context.months().get(month.getMonthValue()-1);var target=assessment.months().stream().filter(m->m.month().equals(month.toString())).findFirst().orElse(null);var e=repository.evidence(tenant,company,employee,month).evidence();var issues=new LinkedHashSet<String>();
  if(target==null)issues.addAll(assessment.issues());else issues.addAll(target.issues());
  String eeTarget=target==null?null:target.targetEmployeeCpf(),erTarget=target==null?null:target.targetEmployerCpf();
  if(f.cpf()!=null&&"foreign".equals(f.cpf().citizenship())){eeTarget="0.00";erTarget="0.00";}
  String eeRemit=e==null||e.remitted()==null?null:net(e.remitted().employeeCpf(),tenant,company,employee,month,"cpf_employee"),erRemit=e==null||e.remitted()==null?null:net(e.remitted().employerCpf(),tenant,company,employee,month,"cpf_employer"),cdacRemit=e==null||e.remitted()==null?null:net(e.remitted().cdac(),tenant,company,employee,month,"cdac");
  var cdac=cdac(tenant,company,employee,month,f,e,issues);String originalCdac=originalCdac(tenant,company,employee,month,e);String recognisedCdac=originalCdac==null?null:money(number(originalCdac).add(repository.liability(tenant,company,employee,month,"cdac")));
  String eeDeducted=e==null?null:e.employeeCpfDeducted(),cdacDeducted=e==null?null:e.cdacDeducted();
  if(eeDeducted!=null){var added=paidDeductions(tenant,company,employee,month,true);eeDeducted=added==null?null:money(number(eeDeducted).add(added));}
  if(cdacDeducted!=null){var added=paidDeductions(tenant,company,employee,month,false);cdacDeducted=added==null?null:money(number(cdacDeducted).add(added));}
  var ee=balance(eeTarget,f.recognisedEmployeeCpf(),eeDeducted,eeRemit);var er=balance(erTarget,f.recognisedEmployerCpf(),null,erRemit);var shg=balance(cdac,recognisedCdac,cdacDeducted,cdacRemit);
  if(e!=null&&e.discoveryDate()!=null&&month.getYear()<e.discoveryDate().getYear()&&recognisedCdac!=null&&cdac!=null&&number(cdac).compareTo(number(recognisedCdac))>0){shg=new SupplementalMoney.Balance(cdac,recognisedCdac,cdacDeducted,cdacRemit,"0.00","0.00",difference(recognisedCdac,cdacRemit));issues.add("CDAC_PRIOR_DISCOVERY_YEAR_NO_BACKPAY");}
  String sdlTarget=f.rawOW()==null||f.policy()==null?null:StatutoryCalculator.sdl(number(f.rawOW()).add(AnnualWageRepository.aw(f.events())),f.policy().config()).stripTrailingZeros().toPlainString();
  var recovery=e==null?null:e.recovery();String recoverable=recoverable(tenant,company,employee,month,recovery,ee);var interest=e==null||e.interest()==null?new Interest("unknown",null,null):e.interest();
  if(eeRemit==null||erRemit==null||cdacRemit==null)issues.add("STATUTORY_REMITTANCE_UNKNOWN");if(eeDeducted==null||cdacDeducted==null)issues.add("STATUTORY_DEDUCTION_UNKNOWN");
  boolean refund=List.of(ee,er,shg).stream().anyMatch(v->negative(v.liabilityDelta())||negative(v.remittanceBalance()));
  return new MonthBalance(month.toString(),ee,er,balance(sdlTarget,null,null,null),shg,recovery,recoverable,interest,refund,List.copyOf(issues));
 }
 public BigDecimal committedDeductions(UUID tenant,UUID company,UUID employee,YearMonth month,boolean cpf){return new SupplementalRepository(jdbc,json).recognised(tenant,company,employee,month.getYear()).stream().filter(b->b.originMonth().equals(month.toString())).map(b->number(cpf?b.calculation().employeeCpfDeduction():b.calculation().cdacDeduction())).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
 private BigDecimal paidDeductions(UUID tenant,UUID company,UUID employee,YearMonth month,boolean cpf){var repo=new SupplementalRepository(jdbc,json);BigDecimal result=BigDecimal.ZERO;for(var b:repo.recognised(tenant,company,employee,month.getYear()))if(b.originMonth().equals(month.toString())){var payments=repo.payments(tenant,b.id());var paid=payments.stream().map(p->number(p.amount()).multiply(BigDecimal.valueOf(p.reversesId()==null?1:-1))).reduce(BigDecimal.ZERO,BigDecimal::add);var deduction=number(cpf?b.calculation().employeeCpfDeduction():b.calculation().cdacDeduction());var cash=number(b.calculation().employeeCashPayable());if(!payments.isEmpty()&&paid.compareTo(cash)<0&&deduction.signum()>0)return null;if(paid.compareTo(cash)>=0)result=result.add(deduction);}return result;}
 private String net(String opening,UUID tenant,UUID company,UUID employee,YearMonth month,String component){return opening==null?null:money(number(opening).add(repository.remitted(tenant,company,employee,month,component)));}
 private boolean negative(String s){return s!=null&&number(s).signum()<0;}
 private PayrollDtos.PayrollLine normal(UUID tenant,UUID company,UUID employee,YearMonth month){var b=new PayrollRepository(jdbc,json).forMonth(tenant,company,month).orElse(null);return b==null||b.calculation()==null||!Set.of("confirmed","published").contains(b.status())?null:b.calculation().lines().stream().filter(l->l.employeeId().equals(employee)).findFirst().orElse(null);}
 public String originalCdac(UUID tenant,UUID company,UUID employee,YearMonth month,Evidence e){var l=normal(tenant,company,employee,month);return l==null?e==null?null:e.recognisedCdac():frozenCdac(l,new LinkedHashSet<>());}
 private boolean manualCdac(PayrollDtos.PayrollLine l){return l.profile()!=null&&(l.profile().cdac()==null||"manual".equals(l.profile().cdac().status()));}
 /** Legacy Decision.deduction=0 prevents an extra deduction; it does not erase fixed CDAC items. */
 private String frozenCdac(PayrollDtos.PayrollLine l,Set<String> issues){
  if(manualCdac(l)&&l.input()!=null&&l.input().cdacReview()!=null){issues.add("CDAC_REVIEW_INVALID");return null;}
  try{
   if(l.cdacCalculation()!=null)return money(frozenAmount(l.cdacCalculation().amount()));
   if(!manualCdac(l)||l.profile().rule()==null||l.profile().rule().items()==null||l.input()==null||l.input().items()==null||l.components()==null)throw new IllegalArgumentException();
   var items=new ArrayList<>(l.profile().rule().items());items.addAll(l.input().items());var components=new ArrayList<PayrollDtos.Component>();
   for(var c:l.components()){
    if(c==null||c.kind()==null)throw new IllegalArgumentException();frozenAmount(c.amount());
    if(!Set.of("employee_cpf","employer_cpf","sdl").contains(c.kind()))components.add(c);
   }
   // PayCalculator freezes basic, overtime, then every recurring and monthly item in order.
   // Check the complete captured list, so an absent CDAC component cannot silently prove zero.
   if(components.size()!=items.size()+2||!"basic".equals(components.get(0).kind())||!"overtime".equals(components.get(1).kind())||frozenAmount(components.get(0).amount()).compareTo(frozenAmount(l.basicPay()))!=0||frozenAmount(components.get(1).amount()).compareTo(frozenAmount(l.overtimePay()))!=0)throw new IllegalArgumentException();
   BigDecimal total=BigDecimal.ZERO,deductions=BigDecimal.ZERO;
   for(int i=0;i<items.size();i++){
    var item=items.get(i);var c=components.get(i+2);
    if(item==null||!PayItemKinds.supported(item.kind())||item.label()==null||!item.label().equals(c.label())||!item.kind().equals(c.kind()))throw new IllegalArgumentException();
    var raw=frozenAmount(item.amount());var amount=frozenAmount(c.amount());
    if(PayItemKinds.DEDUCTIONS.contains(c.kind())){if(raw.setScale(2,RoundingMode.HALF_UP).compareTo(amount)!=0)throw new IllegalArgumentException();deductions=deductions.add(amount);}
    if("cdac".equals(c.kind()))total=total.add(amount);
   }
   if(deductions.compareTo(frozenAmount(l.deductions()))!=0)throw new IllegalArgumentException();
   return money(total);
  }catch(IllegalArgumentException ex){issues.add("CDAC_ORIGINAL_LIABILITY_MISSING");return null;}
 }
 private BigDecimal frozenAmount(String value){if(value==null)throw new IllegalArgumentException();var amount=new BigDecimal(value);if(amount.signum()<0)throw new IllegalArgumentException();return amount;}
 private String cdac(UUID tenant,UUID company,UUID employee,YearMonth month,AnnualWageDtos.MonthFact f,Evidence e,Set<String> issues){
  if(f.rawOW()==null||f.policy()==null){issues.add("ORIGINAL_PERIOD_POLICY_MISSING");return null;}
  var l=normal(tenant,company,employee,month);var profile=l==null?null:l.profile();var input=l==null?null:l.input();
  if(l!=null&&manualCdac(l))return frozenCdac(l,issues);
  if(profile==null){if(e==null||e.cdacProfile()==null){issues.add("CDAC_PROFILE_INVALID");return null;}var items=e.cdacProfile().status().equals("manual")?e.manualCdac()==null?null:List.of(new PayrollDtos.Item("Approved manual CDAC","cdac",e.manualCdac())):List.<PayrollDtos.Item>of();if(items==null){issues.add("CDAC_PROFILE_INVALID");return null;}profile=new PayrollDtos.PayProfile(employee,company,employee,"",month.atDay(1),employee,1,new PayrollDtos.Rule("monthly","0","8","44",false,"1.5",items),f.cpf(),1,null,e.cdacProfile());input=new PayrollDtos.LineInput(employee,1,null,null,true,false,List.of(),"",e.cdacReview());}
  try{return CdacPayroll.evaluate(month,profile,input,f.policy(),number(f.rawOW()).add(AnnualWageRepository.aw(f.events()))).calculation().amount();}catch(com.rpb.reservation.workforce.domain.WorkforceException ex){issues.add(ex.code());return null;}
 }
 private String recoverable(UUID tenant,UUID company,UUID employee,YearMonth month,Recovery r,SupplementalMoney.Balance ee){
  if(r==null||r.cause()==null||r.cause().equals("unclassified"))return null;
  if(Set.of("ordinary_employer_omission","already_deducted_unremitted").contains(r.cause()))return "0.00";
  if(r.legalBasis()==null||r.legalBasis().isBlank()||r.evidenceReference()==null||r.evidenceReference().isBlank()||r.maximum()==null||r.deadline()==null||ee.remitted()==null||ee.deducted()==null||ee.target()==null)return null;
  BigDecimal maximum=number(r.maximum()),paid=number(ee.remitted()).subtract(number(ee.deducted())).max(BigDecimal.ZERO);
  if(r.cause().equals("aw_statutory_reassessment")){
   if(r.reconciliationId()==null)return null;
   var reconciliation=repository.reconciliation(tenant,r.reconciliationId()).orElse(null);
   if(reconciliation==null||!reconciliation.companyId().equals(company)||!reconciliation.employeeId().equals(employee)||reconciliation.year()!=month.getYear()||!reconciliation.assessment().status().equals("ready")||r.dueYear()==null||r.dueYear()!=reconciliation.year()||!r.deadline().equals(LocalDate.of(r.dueYear()+1,6,30)))return null;
   var original=reconciliation.assessment().months().stream().filter(m->m.month().equals(month.toString())).findFirst().orElse(null);
   if(original==null||original.employeeDelta()==null||original.targetEmployeeCpf()==null||original.rawAW()==null||number(original.rawAW()).signum()<=0)return null;
   var additional=number(original.employeeDelta()).max(BigDecimal.ZERO);maximum=maximum.min(additional);
   var previouslyRecognised=number(original.targetEmployeeCpf()).subtract(number(original.employeeDelta()));
   paid=paid.min(number(ee.remitted()).subtract(previouslyRecognised).max(BigDecimal.ZERO));
  }
  if(LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore"))).isAfter(r.deadline()))return "0.00";
  return money(maximum.min(number(ee.target()).subtract(number(ee.deducted())).max(BigDecimal.ZERO)).min(paid));
 }
}
