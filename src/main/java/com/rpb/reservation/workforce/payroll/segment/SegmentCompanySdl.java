package com.rpb.reservation.workforce.payroll.segment;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.statutory.StatutoryCalculator;
import com.rpb.reservation.workforce.payroll.supplemental.SupplementalRepository;
import java.util.*;import java.math.*;import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Component;
/** Whole-company monthly floor, less every already recognised formal liability. */
@Component public class SegmentCompanySdl {
 private final JdbcTemplate jdbc;private final WorkforceJson json;
 public SegmentCompanySdl(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
 private List<SegmentRepository.State> formal(UUID t,UUID c,YearMonth m){return new SegmentRepository(jdbc,json).formal(t,c,m);}
 public BigDecimal recognisedSegments(UUID t,UUID c,YearMonth m){var v=formal(t,c,m).stream().map(s->SegmentMoney.n(s.calculation().companySdlDelta())).reduce(BigDecimal.ZERO,BigDecimal::add);var extra=jdbc.queryForObject("select coalesce(sum(amount),0) from workforce_statutory_liability_entries where tenant_id=? and company_id=? and employee_id is null and origin_month=? and component='sdl'",BigDecimal.class,t,c,m.atDay(1));return v.add(extra);}
 public Map<UUID,SegmentRepository.State> latest(UUID t,UUID c,YearMonth m){var map=new LinkedHashMap<UUID,SegmentRepository.State>();for(var s:formal(t,c,m))map.merge(s.employee(),s,(a,b)->a.cutoff().isAfter(b.cutoff())?a:b);return map;}
 public BigDecimal individual(UUID t,UUID c,YearMonth m,UUID proposed,PayrollDtos.PayrollSnapshot proposedSnapshot){
  var map=latest(t,c,m);var supplementGross=new HashMap<UUID,BigDecimal>();new SupplementalRepository(jdbc,json).forMonth(t,c,m).forEach(b->supplementGross.merge(b.employeeId(),SegmentMoney.n(b.calculation().grossDelta()),BigDecimal::add));BigDecimal total=BigDecimal.ZERO;
  for(var s:map.values())if(!s.employee().equals(proposed)){var snap=s.calculation().cumulative();var l=snap.lines().getFirst();var supplements=supplementGross.getOrDefault(s.employee(),BigDecimal.ZERO);total=total.add(StatutoryCalculator.sdl(SegmentMoney.n(l.grossPay()).add(supplements),snap.statutoryPolicy().config()));}
  if(proposedSnapshot!=null)total=total.add(SegmentMoney.n(proposedSnapshot.lines().getFirst().sdl()));return total;
 }
 public String segmentDelta(UUID t,UUID c,YearMonth m,UUID employee,PayrollDtos.PayrollSnapshot cumulative){if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from (select distinct on(employee_id) snapshot from workforce_statutory_year_revisions where tenant_id=? and company_id=? and year=? order by employee_id,version desc) r cross join lateral jsonb_array_elements(r.snapshot->'months') f where f->>'month'=? and f->>'state'='actual' and ((f->>'rawOW')::numeric>0 or jsonb_array_length(coalesce(f->'events','[]'::jsonb))>0))",Boolean.class,t,c,m.getYear(),m.toString()))||new com.rpb.reservation.workforce.statutory.settlement.CompanySdlProjection(jdbc,json,Clock.systemUTC()).saved(t,c,m).evidence()!=null)return null;if(formal(t,c,m).stream().anyMatch(v->!Objects.equals(v.calculation().cumulative().statutoryPolicy(),cumulative.statutoryPolicy())))return null;var normal=new PayrollRepository(jdbc,json).forMonth(t,c,m).filter(b->SegmentRepository.formal(b.status())&&b.calculation()!=null).orElse(null);if(normal!=null&&!Objects.equals(normal.calculation().statutoryPolicy(),cumulative.statutoryPolicy()))return null;BigDecimal individuals=individual(t,c,m,employee,cumulative);BigDecimal recognised=recognisedSegments(t,c,m);if(normal!=null){individuals=individuals.add(normal.calculation().lines().stream().map(l->SegmentMoney.n(l.sdl())).reduce(BigDecimal.ZERO,BigDecimal::add));recognised=recognised.add(SegmentMoney.n(normal.calculation().totalSdl()));}return SegmentMoney.money(individuals.setScale(0,com.rpb.reservation.workforce.statutory.StatutoryPolicyConfig.rounding(cumulative.statutoryPolicy().config().companySdlRounding())).subtract(recognised));}
 public BigDecimal ordinary(UUID t,UUID c,YearMonth m,BigDecimal normalIndividual){return normalIndividual.add(individual(t,c,m,null,null)).setScale(0,RoundingMode.FLOOR).subtract(recognisedSegments(t,c,m));}
}
