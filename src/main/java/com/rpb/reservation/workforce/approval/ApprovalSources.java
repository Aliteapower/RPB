package com.rpb.reservation.workforce.approval;
import static com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.attendance.AttendanceRepository;
import com.rpb.reservation.workforce.leave.LeaveRepository;
import com.rpb.reservation.workforce.calendar.CalendarRepository;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule;
import java.security.*;import java.nio.charset.StandardCharsets;import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;
/** Reads source projections without mutating raw attendance or historical approval snapshots. */
@Service
public class ApprovalSources {
 private final AttendanceRepository attendance;private final LeaveRepository leaves;private final CalendarRepository calendar;private final WorkforceJson json;private final Clock clock;
 private final com.rpb.reservation.workforce.workmode.WorkModeClock workModes;
 public ApprovalSources(AttendanceRepository attendance,LeaveRepository leaves,CalendarRepository calendar,WorkforceJson json,Clock clock){this(attendance,leaves,calendar,json,clock,null);}
 @org.springframework.beans.factory.annotation.Autowired
 public ApprovalSources(AttendanceRepository attendance,LeaveRepository leaves,CalendarRepository calendar,WorkforceJson json,Clock clock,com.rpb.reservation.workforce.workmode.WorkModeClock workModes){this.attendance=attendance;this.leaves=leaves;this.calendar=calendar;this.json=json;this.clock=clock;this.workModes=workModes;}
 public ApprovalSource read(UUID tenant,UUID store,Schedule s){var session=attendance.bySchedule(tenant,s.id()).orElse(null);return new ApprovalSource(s,session,attendance.corrections(tenant,s.id()),leaves.forSchedule(tenant,s.id()),calendar.list(tenant,store,s.workDate(),s.workDate()),workModes==null?null:workModes.approvalPolicy(tenant,s.employeeId(),store,s.workDate(),session));}
 public Map<UUID,ApprovalSource> readAll(UUID tenant,UUID store,List<Schedule> schedules){
  if(schedules.isEmpty())return Map.of();var ids=schedules.stream().map(Schedule::id).toList();
  var sessions=new HashMap<UUID,com.rpb.reservation.workforce.attendance.AttendanceDtos.Session>();attendance.sessions(tenant,ids).forEach(s->sessions.put(s.scheduleId(),s));
  var corrections=new HashMap<UUID,List<com.rpb.reservation.workforce.attendance.AttendanceDtos.Correction>>();attendance.correctionsFor(tenant,ids).forEach(c->corrections.computeIfAbsent(c.scheduleId(),k->new ArrayList<>()).add(c));
  var requests=new HashMap<UUID,List<com.rpb.reservation.workforce.leave.LeaveDtos.Leave>>();leaves.forSchedules(tenant,ids).forEach(l->requests.computeIfAbsent(l.scheduleId(),k->new ArrayList<>()).add(l));
  var from=schedules.stream().map(Schedule::workDate).min(Comparator.naturalOrder()).orElseThrow();var to=schedules.stream().map(Schedule::workDate).max(Comparator.naturalOrder()).orElseThrow();var events=calendar.list(tenant,store,from,to);
  var policies=workModes==null?null:workModes.approvalPolicies(tenant,schedules,sessions);
  var result=new HashMap<UUID,ApprovalSource>();for(var s:schedules)result.put(s.id(),new ApprovalSource(s,sessions.get(s.id()),corrections.getOrDefault(s.id(),List.of()),requests.getOrDefault(s.id(),List.of()),events.stream().filter(e->e.date().equals(s.workDate())).toList(),policies==null?null:policies.get(s.id())));return result;
 }
 public String fingerprint(ApprovalSource source){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.write(source).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 public ApprovalRow row(UUID store,ApprovalSource source,Approval latest){
  if(source.clockPolicy()!=null&&source.clockPolicy().mode().equals("free"))return new ApprovalRow(source.schedule(),store,0,0,0,List.of(),fingerprint(source),latest,false,null,null,List.of(),true);
  var accepted=source.leaves().stream().filter(l->Set.of("approved","cancel_requested").contains(l.status())).map(l->new ApprovalCalculator.LeaveSlice(l.startAt(),l.endAt(),l.paid())).toList();
  boolean pending=source.corrections().stream().anyMatch(c->c.status().equals("pending"))||source.leaves().stream().anyMatch(l->Set.of("pending","cancel_requested").contains(l.status()));
  var totals=new ApprovalCalculator().calculate(source.schedule(),source.session(),accepted,clock.instant(),pending);String key=fingerprint(source);
  var findings=new AttendanceJudgement().findings(source.schedule(),source.session(),accepted,clock.instant());var reviewed=new ArrayList<Finding>();var issues=new ArrayList<>(totals.issues());boolean current=latest!=null&&latest.sourceFingerprint().equals(key);var decisions=current&&latest.decisions()!=null?latest.decisions():List.<ReviewDecision>of();
  for(var f:findings){var decision=decisions.stream().filter(d->d.code().equals(f.code())).findFirst().orElse(null);if(decision==null){reviewed.add(f);continue;}boolean absence=f.code().equals("MISSING")&&decision.outcome().equals("absence")&&source.session()==null&&source.schedule().shift().rules().rule("ABSENCE")!=null;if(absence){issues.remove("MISSING_IN");var absenceRule=source.schedule().shift().rules().rule("ABSENCE");reviewed.add(new Finding(f.code(),absenceRule.name(),f.seconds(),"absence",absenceRule.affectsBonus()));}else reviewed.add(new Finding(f.code(),f.name(),f.seconds(),decision.outcome(),f.affectsBonus()));}
  boolean unresolved=reviewed.stream().anyMatch(f->f.status().equals("pending"));String qualification=source.schedule().shift().rules()==null?null:!current||!issues.isEmpty()||unresolved?"pending":reviewed.stream().anyMatch(f->f.affectsBonus()&&!f.status().equals("excused"))?"ineligible":"eligible";
  return new ApprovalRow(source.schedule(),store,totals.workSeconds(),totals.paidLeaveSeconds(),totals.unpaidLeaveSeconds(),List.copyOf(issues),key,latest,latest!=null&&(!current||!issues.isEmpty()||unresolved),source.schedule().shift().rules()==null?null:List.copyOf(reviewed),qualification,new com.rpb.reservation.workforce.attendance.BreakCalculator().summary(source.schedule().shift(),source.schedule().startAt(),source.session()));
 }
 public List<ReviewDecision> decisions(ApprovalSource source,List<ReviewDecision> input){var requested=input==null?List.<ReviewDecision>of():input;if(requested.size()>8)throw com.rpb.reservation.workforce.domain.WorkforceException.invalid();var accepted=source.leaves().stream().filter(l->Set.of("approved","cancel_requested").contains(l.status())).map(l->new ApprovalCalculator.LeaveSlice(l.startAt(),l.endAt(),l.paid())).toList();var findings=new AttendanceJudgement().findings(source.schedule(),source.session(),accepted,clock.instant());var result=new ArrayList<ReviewDecision>();var seen=new HashSet<String>();for(var d:requested){if(d==null||d.code()==null||!seen.add(d.code())||d.note()==null||d.note().isBlank()||d.note().trim().length()>500||d.outcome()==null)throw com.rpb.reservation.workforce.domain.WorkforceException.invalid();var finding=findings.stream().filter(f->f.code().equals(d.code())&&f.status().equals("pending")&&!Set.of("BREAK_MISSING","LEAVE").contains(f.code())).findFirst().orElseThrow(com.rpb.reservation.workforce.domain.WorkforceException::invalid);if(finding.code().equals("MISSING")?!(d.outcome().equals("absence")&&source.schedule().shift().rules().rule("ABSENCE")!=null):!Set.of("confirmed","excused").contains(d.outcome()))throw com.rpb.reservation.workforce.domain.WorkforceException.invalid();result.add(new ReviewDecision(d.code(),d.outcome(),d.note().trim()));}if(findings.stream().anyMatch(f->f.status().equals("pending")&&!f.code().equals("BREAK_MISSING")&&!seen.contains(f.code())))throw new com.rpb.reservation.workforce.domain.WorkforceException("WORKFORCE_APPROVAL_BLOCKED",409);return result.stream().sorted(Comparator.comparing(ReviewDecision::code)).toList();}
}

