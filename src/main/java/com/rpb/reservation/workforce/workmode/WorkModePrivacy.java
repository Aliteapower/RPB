package com.rpb.reservation.workforce.workmode;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;import org.springframework.stereotype.Service;
/** Response-only projection. Never redact stored evidence, source fingerprints, or audit snapshots. */
@Service
public class WorkModePrivacy {
 private final WorkforceAccess access;private final WorkforceEmployeeRepository employees;
 public WorkModePrivacy(WorkforceAccess access,WorkforceEmployeeRepository employees){this.access=access;this.employees=employees;}
 public boolean visible(CurrentActor actor,UUID employee){var e=employees.find(actor.tenantId(),employee).orElse(null);return e!=null&&(actor.actorId().equals(e.accountId())||!e.storeIds().isEmpty()&&access.managedStores(actor).containsAll(e.storeIds()));}
 private ClockPolicy policy(ClockPolicy p){
  if(p==null)return null;boolean home=p.mode().equals("home");
  var options=p.options()==null?null:p.options().stream().map(o->o.mode().equals("home")?new ClockOption(o.mode(),null,null,0,null):o).toList();
  return new ClockPolicy(p.mode(),p.assignmentId(),p.assignmentVersion(),home?null:p.point(),home?null:p.addressId(),home?0:p.addressVersion(),home?null:p.siteVersion(),options);
 }
 private Session session(Session s){return s==null?s:new Session(s.id(),s.scheduleId(),s.employeeId(),s.storeId(),s.inAt(),s.outAt(),s.breakStartedAt(),s.breaks(),s.overrideBreak(),s.version(),s.breakStartedRuleId(),policy(s.clockPolicy()));}
 public Attendance attendance(CurrentActor actor,Attendance a){return visible(actor,a.schedule().employeeId())?a:new Attendance(a.schedule(),session(a.session()),a.grossSeconds(),a.breakSeconds(),a.workSeconds(),a.anomalies(),a.breakSummary(),a.exempt());}
 public Detail detail(CurrentActor actor,Detail d){if(visible(actor,d.attendance().schedule().employeeId()))return d;return new Detail(attendance(actor,d.attendance()),d.events().stream().map(e->new Event(e.id(),e.action(),e.occurredAt(),e.actorId(),null,e.clockMode())).toList(),d.corrections());}
 private ApprovalSource source(ApprovalSource s){return new ApprovalSource(s.schedule(),session(s.session()),s.corrections(),s.leaves(),s.calendar(),policy(s.clockPolicy()));}
 public Approval approval(CurrentActor actor,Approval a){return a==null||visible(actor,a.snapshot().schedule().employeeId())?a:new Approval(a.scheduleId(),a.version(),a.sourceFingerprint(),a.workSeconds(),a.paidLeaveSeconds(),a.unpaidLeaveSeconds(),source(a.snapshot()),a.reviewerId(),a.note(),a.approvedAt(),a.decisions());}
 public ApprovalRow row(CurrentActor actor,ApprovalRow r){if(r.approval()==null)return r;return new ApprovalRow(r.schedule(),r.storeId(),r.workSeconds(),r.paidLeaveSeconds(),r.unpaidLeaveSeconds(),r.issues(),r.sourceFingerprint(),approval(actor,r.approval()),r.requiresReview(),r.findings(),r.fullAttendanceStatus(),r.breakSummary(),r.exempt());}
}
