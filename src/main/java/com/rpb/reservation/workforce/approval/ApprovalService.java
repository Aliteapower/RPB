package com.rpb.reservation.workforce.approval;
import static com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.attendance.AttendanceAccess;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class ApprovalService {
 private final WorkforceAccess gate;private final AttendanceAccess access;private final WorkforceEmployeeRepository employees;private final SchedulingRepository schedules;private final ApprovalRepository repository;private final ApprovalSources sources;private final WorkforceAudit audit;private final Clock clock;
 private final com.rpb.reservation.workforce.workmode.WorkModePrivacy privacy;
 public ApprovalService(WorkforceAccess gate,AttendanceAccess access,WorkforceEmployeeRepository employees,SchedulingRepository schedules,ApprovalRepository repository,ApprovalSources sources,WorkforceAudit audit,Clock clock){this(gate,access,employees,schedules,repository,sources,audit,clock,null);}
 @org.springframework.beans.factory.annotation.Autowired
 public ApprovalService(WorkforceAccess gate,AttendanceAccess access,WorkforceEmployeeRepository employees,SchedulingRepository schedules,ApprovalRepository repository,ApprovalSources sources,WorkforceAudit audit,Clock clock,com.rpb.reservation.workforce.workmode.WorkModePrivacy privacy){this.gate=gate;this.access=access;this.employees=employees;this.schedules=schedules;this.repository=repository;this.sources=sources;this.audit=audit;this.clock=clock;this.privacy=privacy;}
 public List<ApprovalRow> list(CurrentActor actor,UUID store,LocalDate from,LocalDate to){
  gate.enter(actor,store);ScheduleService.range(from,to);UUID self=access.manager(actor,store)?null:access.self(actor,store).id();
  return repository.bounded(repository.scheduleIds(actor.tenantId(),store,null,from,to,self)).stream().map(id->row(actor.tenantId(),store,id)).map(r->privacy==null?r:privacy.row(actor,r)).toList();
 }
 public ApprovalDetail detail(CurrentActor actor,UUID store,UUID id){var s=access.read(actor,store,id);var e=employees.find(actor.tenantId(),s.employeeId()).orElseThrow();var block=ApprovalReviewerPolicy.block(gate.isHeadOffice(actor),access.manager(actor,store),actor.actorId(),e.accountId(),repository.managerAccount(actor.tenantId(),e.accountId()));var row=row(actor.tenantId(),store,id);if(Boolean.TRUE.equals(row.exempt()))block="WORKFORCE_CLOCK_EXEMPT";return new ApprovalDetail(privacy==null?row:privacy.row(actor,row),repository.history(actor.tenantId(),id).stream().map(a->privacy==null?a:privacy.approval(actor,a)).toList(),block==null,block);}
 private ApprovalRow row(UUID tenant,UUID store,UUID id){var s=schedules.find(tenant,id).orElseThrow(WorkforceException::missing);var history=repository.history(tenant,id);return sources.row(store,sources.read(tenant,store,s),history.isEmpty()?null:history.getFirst());}
 @Transactional public Approval approve(CurrentActor actor,UUID store,UUID id,ApprovalWrite c){
        gate.sourceWrite(actor);
  gate.enter(actor,store);gate.manage(actor,store);
  if(c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.sourceFingerprint()==null||!c.sourceFingerprint().matches("[a-f0-9]{64}"))throw WorkforceException.invalid();
  String note=ApprovalNotes.normalize(c.note());
  var initial=access.read(actor,store,id);repository.calendarLock(actor.tenantId(),store);employees.lock(initial.employeeId());var s=access.read(actor,store,id);
  var e=employees.find(actor.tenantId(),s.employeeId()).orElseThrow(WorkforceException::missing);var block=ApprovalReviewerPolicy.block(gate.isHeadOffice(actor),true,actor.actorId(),e.accountId(),repository.managerAccount(actor.tenantId(),e.accountId()));if(block!=null)throw new WorkforceException(block,403);
  var source=sources.read(actor.tenantId(),store,s);var history=repository.history(actor.tenantId(),id);var old=history.isEmpty()?null:history.getFirst();var row=sources.row(store,source,old);
  if(Boolean.TRUE.equals(row.exempt()))throw new WorkforceException("WORKFORCE_CLOCK_EXEMPT",409);
  if(!row.sourceFingerprint().equals(c.sourceFingerprint()))throw WorkforceException.conflict();var decisions=sources.decisions(source,c.decisions());var reviewed=new Approval(id,0,row.sourceFingerprint(),0,0,0,source,actor.actorId(),note,clock.instant(),decisions);row=sources.row(store,source,reviewed);if(!row.issues().isEmpty())throw new WorkforceException("WORKFORCE_APPROVAL_BLOCKED",409);
  if(old!=null&&old.version()==c.expectedVersion()+1&&old.sourceFingerprint().equals(c.sourceFingerprint())&&old.reviewerId().equals(actor.actorId())&&old.note().equals(note)&&Objects.equals(old.decisions()==null?List.of():old.decisions(),decisions))return privacy==null?old:privacy.approval(actor,old);
  if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
  var next=new Approval(id,c.expectedVersion()+1,row.sourceFingerprint(),row.workSeconds(),row.paidLeaveSeconds(),row.unpaidLeaveSeconds(),source,actor.actorId(),note,clock.instant(),source.schedule().shift().rules()==null&&decisions.isEmpty()?null:decisions);
  repository.save(actor.tenantId(),store,s.employeeId(),next);audit.changed(actor,store,"hours.approve",id,old,next);var saved=repository.history(actor.tenantId(),id).getFirst();return privacy==null?saved:privacy.approval(actor,saved);
 }
}

