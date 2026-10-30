package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Employee;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule;
import com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalSource;
import com.rpb.reservation.workforce.approval.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.security.*;import java.nio.charset.StandardCharsets;import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;
/** Captures the complete company/month, including unapproved rows; never silently drops a liability. */
@Service
public class PayrollSources {
 public record CapturedLine(Employee employee,PayProfile profile,LineInput input,List<SourceRef> sources,List<String> issues,boolean specialDays){}
 public record Capture(String fingerprint,PayrollRepository.CompanyInfo company,List<CapturedLine> lines,Set<UUID> stores,com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy statutoryPolicy){}
 private final PayrollRepository repository;private final PayrollConfigurationRepository configs;private final WorkforceEmployeeRepository employees;private final SchedulingRepository schedules;private final ApprovalRepository approvals;private final ApprovalSources sources;private final WorkforceJson json;
 private final com.rpb.reservation.workforce.workmode.WorkModeResolver workModes;
 public PayrollSources(PayrollRepository repository,PayrollConfigurationRepository configs,WorkforceEmployeeRepository employees,SchedulingRepository schedules,ApprovalRepository approvals,ApprovalSources sources,WorkforceJson json){this(repository,configs,employees,schedules,approvals,sources,json,null);}
 @org.springframework.beans.factory.annotation.Autowired
 public PayrollSources(PayrollRepository repository,PayrollConfigurationRepository configs,WorkforceEmployeeRepository employees,SchedulingRepository schedules,ApprovalRepository approvals,ApprovalSources sources,WorkforceJson json,com.rpb.reservation.workforce.workmode.WorkModeResolver workModes){this.repository=repository;this.configs=configs;this.employees=employees;this.schedules=schedules;this.approvals=approvals;this.sources=sources;this.json=json;this.workModes=workModes;}
 public Capture capture(UUID tenant,UUID company,YearMonth month,List<LineInput> inputs){
  var statutoryPolicy=repository.statutoryPolicy(tenant,month);
  LocalDate from=month.atDay(1),context=from.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),to=month.atEndOfMonth();
  var ids=repository.employeeIds(tenant,company,month);if(ids.size()>500)throw new WorkforceException("WORKFORCE_PAYROLL_UNSUPPORTED",400);
  var records=repository.scheduleIds(tenant,company,context,to);if(records.size()>10000)throw new WorkforceException("WORKFORCE_PAYROLL_UNSUPPORTED",400);
  var plans=configs.profiles(tenant,company).stream().filter(p->!p.effectiveFrom().isAfter(to)).toList();
  var plansByEmployee=new HashMap<UUID,List<PayProfile>>();for(var p:plans)plansByEmployee.computeIfAbsent(p.employeeId(),k->new ArrayList<>()).add(p);
  var employeeIds=new HashSet<>(ids);
  var currentPlans=new HashMap<UUID,PayProfile>();for(var p:plans)currentPlans.merge(p.employeeId(),p,(a,b)->a.effectiveFrom().isAfter(b.effectiveFrom())?a:b);
  var stores=new TreeSet<UUID>(repository.companyStores(tenant,company));var tokens=new ArrayList<Object>();var refs=new HashMap<UUID,List<SourceRef>>();var flags=new HashMap<UUID,Set<String>>();var special=new HashSet<UUID>();
  // Load projections once, then retain the original records/employee iteration order for hashing.
  var scoped=new HashMap<UUID,SchedulingRepository.ScopedSchedule>();schedules.scoped(tenant,records).forEach(s->scoped.put(s.schedule().id(),s));
  var selectedRecords=new ArrayList<UUID>();var byStore=new LinkedHashMap<UUID,List<Schedule>>();
  for(var id:records){var entry=Objects.requireNonNull(scoped.get(id));var s=entry.schedule();var selected=currentPlans.get(s.employeeId());if(selected!=null&&PayrollGeneration.fixed(selected.rule()))continue;
   if(s.workDate().isBefore(from)){var plan=plansByEmployee.getOrDefault(s.employeeId(),List.of()).stream().max(Comparator.comparing(PayProfile::effectiveFrom)).orElse(null);if(!employeeIds.contains(s.employeeId())||plan==null||!plan.rule().overtimeEnabled())continue;}
   selectedRecords.add(id);stores.add(entry.store());byStore.computeIfAbsent(entry.store(),k->new ArrayList<>()).add(s);
  }
  var latestApprovals=approvals.latest(tenant,selectedRecords);var loadedSources=new HashMap<UUID,ApprovalSource>();byStore.forEach((shop,list)->loadedSources.putAll(sources.readAll(tenant,shop,list)));
  var employeeRecords=employees.findAll(tenant,ids);var employment=repository.employmentSources(tenant,company,ids,month);var documents=configs.prDocumentsFor(tenant,ids);var singapore=repository.singaporeStores(tenant,stores);var modeAssignments=workModes==null?Map.<UUID,List<com.rpb.reservation.workforce.workmode.WorkModeDtos.Assignment>>of():workModes.assignmentsFor(tenant,ids);
  for(var id:selectedRecords){var s=scoped.get(id).schedule();
   UUID shop=scoped.get(id).store();var latest=latestApprovals.get(id);var source=Objects.requireNonNull(loadedSources.get(id));var row=sources.row(shop,source,latest);boolean payable=!s.workDate().isBefore(from);
   tokens.add(List.of(id,s.employeeId(),shop,row.sourceFingerprint(),latest==null?0L:latest.version(),row.issues(),row.requiresReview()));
   if(!s.status().equals("scheduled"))continue;
   var employeeFlags=flags.computeIfAbsent(s.employeeId(),x->new LinkedHashSet<>());
   if(!singapore.contains(shop)||!s.timezone().equals("Asia/Singapore"))employeeFlags.add("UNSUPPORTED_STORE");
   if(latest==null)employeeFlags.add("APPROVAL_MISSING");else if(row.requiresReview()||!row.issues().isEmpty())employeeFlags.add("APPROVAL_STALE");
   if(row.issues().contains("PENDING_REQUEST"))employeeFlags.add("SOURCE_PENDING");
   if(payable&&row.workSeconds()>0&&source.calendar().stream().anyMatch(c->c.enabled()))special.add(s.employeeId());
   refs.computeIfAbsent(s.employeeId(),x->new ArrayList<>()).add(new SourceRef(id,shop,s.workDate(),latest==null?0:latest.version(),row.sourceFingerprint(),row.workSeconds(),row.paidLeaveSeconds(),row.unpaidLeaveSeconds(),payable));
  }
  var result=new ArrayList<CapturedLine>();var employeeTokens=new ArrayList<Employee>();var evidenceTokens=new ArrayList<Object>();
  for(UUID id:ids){var e=Objects.requireNonNull(employeeRecords.get(id));var issues=flags.computeIfAbsent(id,x->new LinkedHashSet<>());var relevant=plansByEmployee.getOrDefault(id,List.of());var profile=currentPlans.get(id);
   var periods=profile!=null&&PayrollGeneration.automatic(profile.rule())?employment.periods().getOrDefault(id,List.of()):List.<PayrollRepository.EmploymentPeriod>of();
   if(profile!=null&&PayrollGeneration.fixed(profile.rule())&&periods.isEmpty())continue;
   employeeTokens.add(e);
   if(profile==null)issues.add("PROFILE_MISSING");else{
    var start=periods.isEmpty()?employment.starts().getOrDefault(id,from):periods.getFirst().from();if(profile.effectiveFrom().isAfter(start))issues.add("PROFILE_NOT_EFFECTIVE");
    if(relevant.stream().filter(p->!p.effectiveFrom().isBefore(start)).count()>1)issues.add("PROFILE_MULTIPLE");
    if(profile.rule().overtimeEnabled())for(var ref:refs.getOrDefault(id,List.of()))if(!ref.payable()){
      var before=relevant.stream().filter(p->!p.effectiveFrom().isAfter(ref.workDate())).max(Comparator.comparing(PayProfile::effectiveFrom)).orElse(null);
      if(before==null)issues.add("PROFILE_NOT_EFFECTIVE");else if(!before.rule().dayHours().equals(profile.rule().dayHours())||!before.rule().weekHours().equals(profile.rule().weekHours())||before.rule().overtimeEnabled()!=profile.rule().overtimeEnabled())issues.add("PROFILE_MULTIPLE");
    }
   }
   if(profile!=null&&e.birthDate()!=null&&!"foreign".equals(profile.cpf().citizenship())){var old=profile.cpf();var cpf=new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile(old.citizenship(),e.birthDate(),old.prDate(),old.prMode(),old.electionReference(),old.prDocumentId());profile=new PayProfile(profile.id(),profile.companyId(),profile.employeeId(),profile.employeeName(),profile.effectiveFrom(),profile.templateId(),profile.templateVersion(),profile.rule(),cpf,profile.version(),profile.payment(),profile.cdac());}
   if(profile!=null&&profile.cpf().prDocumentId()!=null){var cpf=profile.cpf();var evidence=documents.getOrDefault(id,Map.of()).get(cpf.prDocumentId());Object evidenceToken=evidence==null?"MISSING":evidence.prEffectiveOn().toString();evidenceTokens.add(List.of(id,cpf.prDocumentId(),evidenceToken));if(!"spr".equals(cpf.citizenship())||evidence==null||!evidence.prEffectiveOn().equals(cpf.prDate()))issues.add("CPF_DOCUMENT_REVIEW_REQUIRED");}
   if(stores.stream().anyMatch(shop->!singapore.contains(shop)))issues.add("UNSUPPORTED_STORE");
   var input=inputs.stream().filter(i->i.employeeId().equals(id)).findFirst().orElse(defaultInput(id));var list=refs.getOrDefault(id,List.of());
   if(profile!=null&&PayrollGeneration.automatic(profile.rule())){
    evidenceTokens.add(List.of(id,periods));if(periods.size()>1)issues.add("PROFILE_MULTIPLE");
    var generated=PayrollGeneration.generate(profile.rule(),input,list,month,periods.isEmpty()?null:periods.getFirst().from(),periods.isEmpty()?null:periods.getLast().to());input=generated.input();issues.addAll(generated.issues());
   }
   if(workModes!=null&&profile!=null){var history=modeAssignments.getOrDefault(id,List.of()).stream().filter(a->!a.effectiveFrom().isAfter(to)).toList();if(!history.isEmpty()){
    evidenceTokens.add(List.of("work-mode",id,history));var companyPeriods=periods.isEmpty()?employment.periods().getOrDefault(id,List.of()):periods;
    for(var period:companyPeriods)issues.addAll(com.rpb.reservation.workforce.workmode.WorkModePayroll.issues(history,period.from(),period.to(),PayrollGeneration.fixed(profile.rule())));
   }}
   result.add(new CapturedLine(e,profile,input,List.copyOf(list),List.copyOf(issues),special.contains(id)));
  }
  var companyInfo=repository.company(tenant,company);Object policyToken=statutoryPolicy==null?"STATUTORY_POLICY_MISSING":statutoryPolicy;var raw=new ArrayList<Object>(List.of(policyToken,companyInfo,month.toString(),employeeTokens,plans,tokens,inputs,result));if(!evidenceTokens.isEmpty())raw.add(evidenceTokens);
  return new Capture(hash(json.write(raw)),companyInfo,List.copyOf(result),Collections.unmodifiableSet(stores),statutoryPolicy);
 }
 public static LineInput defaultInput(UUID employee){return new LineInput(employee,0,null,null,false,false,List.of(),"");}
 private String hash(String data){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
