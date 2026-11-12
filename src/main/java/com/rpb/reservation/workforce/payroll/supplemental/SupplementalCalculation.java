package com.rpb.reservation.workforce.payroll.supplemental;

import static com.rpb.reservation.workforce.payroll.supplemental.SupplementalDtos.*;
import static com.rpb.reservation.workforce.payroll.supplemental.SupplementalMoney.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.statutory.*;
import com.rpb.reservation.workforce.statutory.annual.*;
import com.rpb.reservation.workforce.statutory.settlement.*;
import java.time.*;import java.math.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Captures and overlays new income; all statutory target calculations use the shared annual/monthly kernel. */
public final class SupplementalCalculation {
 private final JdbcTemplate jdbc;private final WorkforceJson json;private final Clock clock;
 public SupplementalCalculation(JdbcTemplate jdbc,WorkforceJson json,Clock clock){this.jdbc=jdbc;this.json=json;this.clock=clock;}
 public Snapshot calculate(UUID tenant,Batch batch){
  var repo=new SupplementalRepository(jdbc,json);var annual=new AnnualWageRepository(jdbc,json,clock);var month=YearMonth.parse(batch.originMonth());var source=new SupplementalSourceResolver(jdbc,json,clock).validate(tenant,batch.companyId(),batch.employeeId(),batch.proposal());
  var before=annual.capture(tenant,batch.companyId(),batch.employeeId(),month.getYear());var facts=new ArrayList<AnnualWageDtos.MonthFact>();BigDecimal gross=BigDecimal.ZERO,ow=BigDecimal.ZERO;var events=new ArrayList<AnnualWageDtos.WageEvent>();var issues=new LinkedHashSet<String>();
  for(var i:batch.proposal().items()){if(repo.consumed(tenant,batch.companyId(),i.eventEvidence().eventId()))issues.add("WAGE_EVENT_ALREADY_CONSUMED");gross=gross.add(number(i.amount()));if(i.eventEvidence().classification().equals("OW"))ow=ow.add(number(i.amount()));events.add(new AnnualWageDtos.WageEvent(i.eventEvidence(),i.amount(),"supplemental:"+batch.id()));}
  for(var f:before.months())if(f.month().equals(batch.originMonth())){if(!Set.of("actual","known-zero").contains(f.state())||f.rawOW()==null)issues.add("SUPPLEMENTAL_SOURCE_MISSING");var combined=new ArrayList<>(f.events());combined.addAll(events);facts.add(AnnualWageRepository.fact(month,f.state(),f.rawOW()==null?null:number(f.rawOW()).add(ow),f.cpf(),f.policy(),f.recognisedEmployeeCpf(),f.recognisedEmployerCpf(),f.source(),combined,f.issues()));}else facts.add(f);
  var context=new AnnualWageDtos.Context(before.companyId(),before.employeeId(),before.year(),before.version(),before.fingerprint(),List.copyOf(facts),before.issues(),false);
  var assessment=new AnnualWageCalculator().assess(context);var oldAssessment=new AnnualWageCalculator().assess(before);var projection=new SettlementProjection(jdbc,json,clock);var ledger=new SettlementRepository(jdbc,json);var balances=new ArrayList<SettlementDtos.MonthBalance>();
  for(var f:context.months())if(Set.of("actual","known-zero").contains(f.state())){var m=YearMonth.parse(f.month());var balance=projection.read(tenant,batch.companyId(),batch.employeeId(),context,assessment,m);balances.add(balance);if(balance.employeeCpf().target()==null||balance.employerCpf().target()==null)issues.addAll(balance.issues().stream().filter(v->!Set.of("STATUTORY_DEDUCTION_UNKNOWN","STATUTORY_REMITTANCE_UNKNOWN","CDAC_PROFILE_INVALID").contains(v)).toList());}
  var origin=balances.stream().filter(b->b.month().equals(batch.originMonth())).findFirst().orElseThrow(WorkforceException::missing);var evidence=ledger.evidence(tenant,batch.companyId(),batch.employeeId(),month).evidence();
  var company=new CompanySdlProjection(jdbc,json,clock).read(tenant,batch.companyId(),month,batch.employeeId(),gross);issues.addAll(company.issues());if(company.liabilityDelta()==null)issues.add("STATUTORY_SDL_COMPANY_INCOMPLETE");
  String eeDelta=sum(balances,true),erDelta=sum(balances,false);if(eeDelta==null||erDelta==null)issues.add("ORIGINAL_LIABILITY_AMBIGUOUS");
  var originalBalance=projection.read(tenant,batch.companyId(),batch.employeeId(),before,oldAssessment,month);
  String cpfIncrement=difference(origin.employeeCpf().target(),originalBalance.employeeCpf().target()),cdacIncrement=difference(origin.cdac().target(),originalBalance.cdac().target());
  String cpfDeduct=deduction(cpfIncrement,origin.employeeCpf().target(),evidence==null?null:evidence.employeeCpfDeducted(),projection.committedDeductions(tenant,batch.companyId(),batch.employeeId(),month,true));
  String cdacDeduct=deduction(cdacIncrement,origin.cdac().target(),evidence==null?null:evidence.cdacDeducted(),projection.committedDeductions(tenant,batch.companyId(),batch.employeeId(),month,false));String cdacDelta=origin.cdac().liabilityDelta();
  boolean historical=month.isBefore(YearMonth.parse(batch.settlementMonth()));if(historical&&(evidence==null||evidence.discoveryDate()==null)){issues.add("SHG_DISCOVERY_EVIDENCE_MISSING");cdacDelta=null;cdacDeduct=null;}
  if(historical&&evidence!=null&&evidence.discoveryDate()!=null&&month.getYear()<evidence.discoveryDate().getYear()){cdacDelta="0.00";cdacDeduct="0.00";}
  if(cpfDeduct==null||cdacDeduct==null)issues.add("STATUTORY_DEDUCTION_UNKNOWN");if(cdacDelta==null)issues.add("CDAC_ORIGINAL_LIABILITY_MISSING");
  String cash=cash(money(gross),cpfDeduct,cdacDeduct);if(cpfDeduct!=null&&cdacDeduct!=null&&number(cpfDeduct).add(number(cdacDeduct)).compareTo(gross)>0){issues.add("SUPPLEMENTAL_DEDUCTION_EXCEEDS_NEW_WAGE");cash=null;}
  if(!issues.isEmpty())cash=null;
  var tokens=new ArrayList<Object>();tokens.add(before);tokens.add(batch.proposal());tokens.add(batch.approval());tokens.add(source);tokens.add(ledger.tokens(tenant,batch.companyId(),batch.employeeId(),month.getYear()));tokens.add(ledger.remittances(tenant,batch.companyId()));tokens.add(company);if(source.source().batchId()!=null)tokens.add("segment".equals(source.source().kind())?new com.rpb.reservation.workforce.payroll.segment.SegmentRepository(jdbc,json).payments(tenant,source.source().batchId()):new PayrollRepository(jdbc,json).payments(tenant,source.source().batchId()));
  String fp=repo.hash(tokens);String individualOld=originalBalance.sdl().target(),individualNew=origin.sdl().target();String rounding=company.liabilityDelta()==null||individualOld==null||individualNew==null?null:number(company.liabilityDelta()).subtract(number(individualNew).subtract(number(individualOld))).stripTrailingZeros().toPlainString();
  return new Snapshot(fp,money(gross),cash,cpfDeduct,cdacDeduct,eeDelta,erDelta,company.liabilityDelta(),rounding,cdacDelta,source.originalCashRemaining(),List.copyOf(balances),context,assessment,List.copyOf(issues),clock.instant());
 }
 private String sum(List<SettlementDtos.MonthBalance> balances,boolean employee){BigDecimal total=BigDecimal.ZERO;for(var b:balances){String value=employee?b.employeeCpf().liabilityDelta():b.employerCpf().liabilityDelta();if(value==null)return null;total=total.add(number(value));}return money(total);}
 private String deduction(String incremental,String target,String prior,BigDecimal committed){if(incremental==null||target==null||prior==null)return null;return money(number(incremental).max(BigDecimal.ZERO).min(number(target).subtract(number(prior)).subtract(committed).max(BigDecimal.ZERO)));}
}
