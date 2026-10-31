package com.rpb.reservation.workforce.payroll.segment;
import static com.rpb.reservation.workforce.payroll.segment.SegmentDtos.*;
import static com.rpb.reservation.workforce.payroll.segment.SegmentMoney.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
/** Cumulative earned facts minus confirmed settlements, keeping each monthly threshold single. */
public final class SegmentCalculation {
 private final SegmentRepository repo;private final PayrollSources sources;private final PayrollCalculationService calculator;private final SegmentCompanySdl companySdl;private final JdbcTemplate jdbc;private final WorkforceJson json;
 public SegmentCalculation(SegmentRepository repo,PayrollSources sources,PayrollCalculationService calculator,JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;this.repo=repo;this.sources=sources;this.calculator=calculator;companySdl=new SegmentCompanySdl(jdbc,json);}
 public PayrollSources.Capture capture(SegmentRepository.State s){var capture=sources.captureSegment(s.tenant(),s.company(),s.employee(),s.month(),s.cutoff());if(!capture.company().enabled())throw new WorkforceException("WORKFORCE_COMPANY_DISABLED",409);return capture;}
 public Calculation calculate(SegmentRepository.State s){
  var capture=capture(s);var issues=new LinkedHashSet<String>();if(capture.lines().size()!=1)throw new WorkforceException("SEGMENT_EMPLOYMENT_MISSING",409);
  var source=capture.lines().getFirst();var p=source.profile();var raw=source.input();var notices=new ArrayList<String>();
  var prior=repo.formal(s.tenant(),s.company(),s.month()).stream().filter(b->b.employee().equals(s.employee())).toList();var previous=prior.stream().max(Comparator.comparing(SegmentRepository.State::cutoff)).orElse(null);
  var sourceRefs=source.sources();var sourceIssues=new ArrayList<>(source.issues());
  if(previous!=null){var frozen=previous.calculation().cumulative().lines().getFirst();var prefix=sourceRefs.stream().filter(r->!r.workDate().isAfter(previous.cutoff())).toList();if(!frozen.sources().equals(prefix))notices.add("SEGMENT_PRIOR_SOURCE_CHANGED_USE_SUPPLEMENT");var refs=new ArrayList<>(frozen.sources());refs.addAll(sourceRefs.stream().filter(r->r.workDate().isAfter(previous.cutoff())).toList());sourceRefs=List.copyOf(refs);if(sourceRefs.stream().allMatch(r->r.evidence()!=null&&"current".equals(r.evidence().approvalState())))sourceIssues.removeAll(List.of("APPROVAL_STALE","APPROVAL_MISSING","SOURCE_PENDING"));if(p==null||!frozen.profile().id().equals(p.id())||frozen.profile().version()!=p.version()||!Objects.equals(frozen.profile().cpf(),p.cpf())||!frozen.input().items().equals(raw.items())||!Objects.equals(previous.calculation().cumulative().statutoryPolicy(),capture.statutoryPolicy()))issues.add("SEGMENT_PRIOR_TERMS_CHANGED");}
  if(p!=null&&PayrollGeneration.automatic(p.rule())&&!PayrollGeneration.fixed(p.rule())){var periods=new PayrollRepository(jdbc,json).employmentPeriods(s.tenant(),s.company(),s.employee(),s.month());if(!periods.isEmpty()){var end=periods.getLast().to().isAfter(s.cutoff())?s.cutoff():periods.getLast().to();var generated=PayrollGeneration.generate(p.rule(),raw,sourceRefs,s.month(),periods.getFirst().from(),end);raw=generated.input();sourceIssues.removeAll(List.of("AUTO_SCHEDULE_MISSING","AUTO_UNEXPECTED_DAY","AUTO_UNITS_INVALID"));sourceIssues.addAll(generated.issues());}}

  if(p!=null&&(p.rule().items().stream().anyMatch(i->i.eventEvidence()!=null)||raw.items().stream().anyMatch(i->i.eventEvidence()!=null)))issues.add("SEGMENT_WAGE_EVENT_REQUIRES_SEPARATE_REVIEW");
  var input=raw;
  // Fixed/manual monthly units must be reviewed explicitly for cumulative coverage; never invent proration.
  if(p!=null&&(!PayrollGeneration.automatic(p.rule())||PayrollGeneration.fixed(p.rule()))&&!p.rule().basis().equals("hourly")){
   var w=s.request();input=new LineInput(s.employee(),raw.version(),w.workingDays(),w.payableDays(),w.unitsReviewed(),w.specialDaysReviewed(),raw.items(),w.note(),raw.cdacReview());
   var r=p.rule();p=new PayProfile(p.id(),p.companyId(),p.employeeId(),p.employeeName(),p.effectiveFrom(),p.templateId(),p.templateVersion(),new Rule(r.basis(),r.rate(),r.dayHours(),r.weekHours(),r.overtimeEnabled(),r.overtimeMultiplier(),r.items()),p.cpf(),p.version(),p.payment(),p.cdac(),p.origin());
  }else input=new LineInput(raw.employeeId(),raw.version(),raw.workingDays(),raw.payableDays(),raw.unitsReviewed()||s.request().unitsReviewed(),raw.specialDaysReviewed()||s.request().specialDaysReviewed(),raw.items(),s.request().note(),raw.cdacReview(),raw.generation());
  if(p!=null&&!p.rule().basis().equals("hourly")&&input.payableDays()!=null){long eligibleDays=0;for(var period:new PayrollRepository(jdbc,json).employmentPeriods(s.tenant(),s.company(),s.employee(),s.month())){var end=period.to().isAfter(s.cutoff())?s.cutoff():period.to();if(!end.isBefore(period.from()))eligibleDays+=java.time.temporal.ChronoUnit.DAYS.between(period.from(),end)+1;}if(n(input.payableDays()).compareTo(java.math.BigDecimal.valueOf(eligibleDays))>0)issues.add("SEGMENT_UNITS_OUTSIDE_RANGE");}
  if(previous!=null&&p!=null&&"monthly".equals(p.rule().basis())){var before=previous.calculation().cumulative().lines().getFirst().input().workingDays();if(before!=null&&input.workingDays()!=null&&n(before).compareTo(n(input.workingDays()))!=0)issues.add("SEGMENT_PRIOR_TERMS_CHANGED");}
  var adjusted=new PayrollSources.CapturedLine(source.employee(),p,input,sourceRefs,List.copyOf(sourceIssues),source.specialDays(),source.governance(),source.annual(),FullAttendanceBonus.configured(p)?FullAttendanceBonus.status(sourceRefs,s.month(),s.cutoff()):null);
  var actual=new PayrollSources.Capture(capture.fingerprint(),capture.company(),List.of(adjusted),capture.stores(),capture.statutoryPolicy());var snapshot=calculator.calculate(actual,s.month());issues.addAll(snapshot.issues());
  var expected=previous==null?s.month().atDay(1):previous.cutoff().plusDays(1);if(!expected.equals(s.from())||s.cutoff().isBefore(expected))issues.add("SEGMENT_RANGE_CHANGED");
  var settledRows=new ArrayList<PayBreakdown.Values>(prior.stream().map(b->b.calculation().delta()).toList());
  var supplements=new com.rpb.reservation.workforce.payroll.supplemental.SupplementalRepository(jdbc,json).recognised(s.tenant(),s.company(),s.employee(),s.month().getYear()).stream().filter(b->b.originMonth().equals(s.month().toString())).toList();
  var overlay=new ArrayList<Item>(input.items());
  for(var b:supplements){if(b.proposal().items().stream().anyMatch(i->!i.eventEvidence().classification().equals("OW")))issues.add("SEGMENT_AW_REVIEW_REQUIRED");var v=b.calculation();if(!Objects.equals(v.employeeCpfDeduction(),v.employeeCpfLiabilityDelta())||!Objects.equals(v.cdacDeduction(),v.cdacDelta()))issues.add("SEGMENT_SUPPLEMENT_DEDUCTION_REVIEW");var individualSdl=n(v.sdlDelta()).subtract(n(v.sdlRoundingDelta())).toPlainString();var comps=b.proposal().items().stream().map(i->new Component(i.label(),"ow_additional",i.amount())).toList();overlay.addAll(b.proposal().items().stream().map(i->new Item(i.label(),"ow_additional",i.amount())).toList());settledRows.add(new PayBreakdown.Values("0.00","0.00",v.grossDelta(),"0.00","0.00","0.00","0.00",v.cdacDeduction(),v.employeeCpfDeduction(),v.employerCpfDelta(),individualSdl,"0.00",v.grossDelta(),v.employeeCashPayable(),money(n(v.grossDelta()).add(n(v.employerCpfDelta())).add(n(individualSdl))),comps));}
  PayrollSnapshot target=snapshot;
  if(!supplements.isEmpty()){var targetInput=new LineInput(input.employeeId(),input.version(),input.workingDays(),input.payableDays(),input.unitsReviewed(),input.specialDaysReviewed(),List.copyOf(overlay),input.note(),input.cdacReview(),input.generation());var targetSource=new PayrollSources.CapturedLine(adjusted.employee(),adjusted.profile(),targetInput,adjusted.sources(),adjusted.issues(),adjusted.specialDays(),adjusted.governance(),null,adjusted.attendanceBonusStatus());target=calculator.calculate(new PayrollSources.Capture(actual.fingerprint(),actual.company(),List.of(targetSource),actual.stores(),actual.statutoryPolicy()),s.month());issues.addAll(target.issues());}
  var settled=sum(settledRows);var delta=difference(PayBreakdown.of(target.lines().getFirst()),settled);
  if(negative(delta))issues.add("SEGMENT_NEGATIVE_DIFFERENCE_REQUIRES_CORRECTION");
  String sdl=issues.isEmpty()?companySdl.segmentDelta(s.tenant(),s.company(),s.month(),s.employee(),target):null;
  if(issues.isEmpty()&&sdl==null||sdl!=null&&n(sdl).signum()<0)issues.add("SEGMENT_COMPANY_SDL_REVIEW");
  String fingerprint=repo.hash(Arrays.asList(capture,s.request(),prior,supplements,snapshot.lines(),snapshot.statutoryPolicy(),sdl));
  return new Calculation(fingerprint,previous==null?null:previous.id(),snapshot,settled,delta,sdl,target.totalGross(),target.totalNet(),List.copyOf(issues),List.copyOf(notices));
 }
}
