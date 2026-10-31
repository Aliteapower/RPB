package com.rpb.reservation.workforce.statutory.settlement;

import static com.rpb.reservation.workforce.statutory.settlement.SettlementDtos.*;
import static com.rpb.reservation.workforce.payroll.supplemental.SupplementalMoney.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.statutory.*;
import com.rpb.reservation.workforce.statutory.annual.*;
import java.time.*;import java.math.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Company SDL floors the whole monthly individual sum. Its ledger has no employee owner. */
public final class CompanySdlProjection {
 private final JdbcTemplate jdbc;private final WorkforceJson json;private final Clock clock;
 public CompanySdlProjection(JdbcTemplate jdbc,WorkforceJson json,Clock clock){this.jdbc=jdbc;this.json=json;this.clock=clock;}
 public record Saved(long version,CompanyEvidence evidence){}
 public Saved saved(UUID tenant,UUID company,YearMonth month){return jdbc.query("select version,snapshot from workforce_statutory_company_evidence where tenant_id=? and company_id=? and period_month=? order by version desc limit 1",(r,n)->new Saved(r.getLong(1),json.read(r.getString(2),CompanyEvidence.class)),tenant,company,month.atDay(1)).stream().findFirst().orElse(new Saved(0,null));}
 public void save(UUID tenant,UUID company,YearMonth month,CompanyEvidence c,UUID actor){jdbc.update("insert into workforce_statutory_company_evidence(tenant_id,company_id,period_month,version,snapshot,created_by) values(?,?,?,?,?::jsonb,?)",tenant,company,month.atDay(1),c.expectedVersion()+1,json.write(c),actor);}
 public CompanySdl read(UUID tenant,UUID company,YearMonth month,UUID proposedEmployee,BigDecimal addedGross){
  var saved=saved(tenant,company,month);var payroll=new PayrollRepository(jdbc,json);var b=payroll.forMonth(tenant,company,month).orElse(null);var ids=new LinkedHashSet<UUID>();String base=null;var issues=new ArrayList<String>();
  if(b!=null&&Set.of("confirmed","published").contains(b.status())&&b.calculation()!=null){b.calculation().lines().forEach(l->ids.add(l.employeeId()));base=b.calculation().totalSdl();}
  var segmentRows=new com.rpb.reservation.workforce.payroll.segment.SegmentRepository(jdbc,json).formal(tenant,company,month);if(!segmentRows.isEmpty()){segmentRows.forEach(v->ids.add(v.employee()));var segmentSdl=segmentRows.stream().map(v->number(v.calculation().companySdlDelta())).reduce(BigDecimal.ZERO,BigDecimal::add);base=money((base==null?BigDecimal.ZERO:number(base)).add(segmentSdl));}
  var normalIds=Set.copyOf(ids);
  if(saved.evidence()!=null){ids.addAll(saved.evidence().employeeIds());if(base==null)base=saved.evidence().recognisedSdl();}
  ids.addAll(jdbc.queryForList("select distinct employee_id from workforce_supplemental_batches where tenant_id=? and company_id=? and origin_month=? and status in ('confirmed','published')",UUID.class,tenant,company,month.atDay(1)));
  var annual=new AnnualWageRepository(jdbc,json,clock);if(proposedEmployee!=null&&!ids.contains(proposedEmployee)){var f=annual.capture(tenant,company,proposedEmployee,month.getYear()).months().get(month.getMonthValue()-1);if(f.state().equals("known-zero")&&f.source().startsWith("employment:")&&f.rawOW()!=null&&number(f.rawOW()).signum()==0)ids.add(proposedEmployee);}
  var external=jdbc.query("select distinct on(employee_id) employee_id,snapshot from workforce_statutory_year_revisions where tenant_id=? and company_id=? and year=? order by employee_id,version desc",(r,n)->Map.entry(r.getObject(1,UUID.class),json.read(r.getString(2),AnnualWageDtos.Write.class)),tenant,company,month.getYear());
  var externalOriginalRecipients=new LinkedHashSet<UUID>();
  for(var entry:external){var original=entry.getValue().months().stream().filter(f->f.month().equals(month.toString())&&f.state().equals("actual")).findFirst().orElse(null);if(original==null)continue;if(!ids.contains(entry.getKey()))issues.add("STATUTORY_SDL_COMPANY_INCOMPLETE");if(!normalIds.contains(entry.getKey())&&number(original.rawOW()).add(AnnualWageRepository.aw(original.events())).signum()>0)externalOriginalRecipients.add(entry.getKey());}
  boolean mixedBaselineMissing=false;
  if(!normalIds.isEmpty()&&!externalOriginalRecipients.isEmpty()){
   // A partial normal-batch floor is not the original whole-company liability.
   // Require one complete cumulative fact; never add separately floored portions.
   var evidence=saved.evidence();var required=new LinkedHashSet<>(normalIds);required.addAll(externalOriginalRecipients);
   base=evidence!=null&&evidence.employeeIds().containsAll(required)?evidence.recognisedSdl():null;
   mixedBaselineMissing=base==null;
  }
  if(ids.isEmpty()||proposedEmployee!=null&&!ids.contains(proposedEmployee))issues.add("STATUTORY_SDL_COMPANY_INCOMPLETE");
  BigDecimal individual=BigDecimal.ZERO;for(var id:ids){var f=annual.capture(tenant,company,id,month.getYear()).months().get(month.getMonthValue()-1);if(!Set.of("actual","known-zero").contains(f.state())||f.rawOW()==null||f.policy()==null){issues.add("STATUTORY_SDL_COMPANY_INCOMPLETE");continue;}var gross=number(f.rawOW()).add(AnnualWageRepository.aw(f.events()));if(id.equals(proposedEmployee))gross=gross.add(addedGross);individual=individual.add(StatutoryCalculator.sdl(gross,f.policy().config()));}
  var ledger=new SettlementRepository(jdbc,json);String recognised=base==null?null:money(number(base).add(ledger.liability(tenant,company,null,month,"sdl")));String remitted=saved.evidence()==null||saved.evidence().remittedSdl()==null?null:money(number(saved.evidence().remittedSdl()).add(ledger.remitted(tenant,company,null,month,"sdl")));String target=issues.isEmpty()?money(individual.setScale(0,RoundingMode.FLOOR)):null;
  if(mixedBaselineMissing)issues.add("STATUTORY_SDL_COMPANY_INCOMPLETE");
  return new CompanySdl(company,month.toString(),saved.version(),saved.evidence(),target,recognised,remitted,difference(target,recognised),difference(target,remitted),issues.isEmpty()?individual.toPlainString():null,issues.isEmpty()?money(new BigDecimal(target).subtract(individual)):null,List.copyOf(issues),false);
 }
}
