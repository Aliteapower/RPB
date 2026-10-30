package com.rpb.reservation.workforce.approval;
import static com.rpb.reservation.workforce.approval.ApprovalBoardDtos.*;
import static com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
import com.rpb.reservation.workforce.attendance.AttendanceAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class ApprovalBoardService {
 private final WorkforceAccess gate;private final AttendanceAccess access;private final SchedulingRepository schedules;private final ApprovalRepository approvals;private final ApprovalSources sources;private final ApprovalBoardRepository repository;
 private final com.rpb.reservation.workforce.workmode.WorkModePrivacy privacy;
 public ApprovalBoardService(WorkforceAccess gate,AttendanceAccess access,SchedulingRepository schedules,ApprovalRepository approvals,ApprovalSources sources,ApprovalBoardRepository repository){this(gate,access,schedules,approvals,sources,repository,null);}
 @org.springframework.beans.factory.annotation.Autowired
 public ApprovalBoardService(WorkforceAccess gate,AttendanceAccess access,SchedulingRepository schedules,ApprovalRepository approvals,ApprovalSources sources,ApprovalBoardRepository repository,com.rpb.reservation.workforce.workmode.WorkModePrivacy privacy){this.gate=gate;this.access=access;this.schedules=schedules;this.approvals=approvals;this.sources=sources;this.repository=repository;this.privacy=privacy;}
 public static void filter(Filter f){if(f==null)throw WorkforceException.invalid();ScheduleService.range(f.from(),f.to());if(f.search()!=null&&f.search().length()>100)throw WorkforceException.invalid();if(f.category()!=null&&!Set.of("all","normal","manager","exception","waiting","stale","current","exempt").contains(f.category()))throw WorkforceException.invalid();}
 public static boolean normal(ApprovalRow row){return !Boolean.TRUE.equals(row.exempt())&&row.approval()==null&&row.issues().isEmpty()&&(row.findings()==null||row.findings().stream().allMatch(f->f.code().equals("LEAVE")&&f.status().equals("confirmed")));}
 public static String category(ApprovalRow row,boolean manager){if(Boolean.TRUE.equals(row.exempt()))return "exempt";if(row.approval()!=null)return row.requiresReview()||!row.issues().isEmpty()?"stale":"current";if(row.issues().stream().anyMatch(i->Set.of("PENDING_REQUEST","SHIFT_NOT_ENDED","SCHEDULE_NOT_ENDED").contains(i)))return "waiting";return normal(row)?manager?"manager":"normal":"exception";}
 public List<Entry> scan(CurrentActor actor,UUID store,Filter f){
  gate.enter(actor,store);if(!repository.ownsStore(actor.tenantId(),store))throw WorkforceException.forbidden();filter(f);boolean manager=access.manager(actor,store);UUID employee=manager?null:access.self(actor,store).id();
  var raw=schedules.list(actor.tenantId(),store,f.from(),f.to(),employee,50001,0);if(raw.size()>50000)throw WorkforceException.invalid();var rows=raw.stream().filter(s->s.status().equals("scheduled")).toList();
  var subjects=new HashMap<UUID,ApprovalBoardRepository.Subject>();var employeeIds=rows.stream().map(Schedule::employeeId).distinct().toList();for(int i=0;i<employeeIds.size();i+=500)subjects.putAll(repository.subjects(actor.tenantId(),employeeIds.subList(i,Math.min(i+500,employeeIds.size()))));
  String search=f.search()==null?"":f.search().trim().toLowerCase(Locale.ROOT);rows=rows.stream().filter(s->f.positionId()==null||f.positionId().equals(subjects.get(s.employeeId()).positionId())).filter(s->search.isEmpty()||(s.employeeName()+" "+s.employeeNo()).toLowerCase(Locale.ROOT).contains(search)).toList();
  var result=new ArrayList<Entry>();for(int i=0;i<rows.size();i+=500){var chunk=rows.subList(i,Math.min(i+500,rows.size()));var data=sources.readAll(actor.tenantId(),store,chunk);var latest=approvals.latest(actor.tenantId(),chunk.stream().map(Schedule::id).toList());
   for(var s:chunk){var source=data.get(s.id());var row=sources.row(store,source,latest.get(s.id()));var subject=subjects.get(s.employeeId());String block=Boolean.TRUE.equals(row.exempt())?"WORKFORCE_CLOCK_EXEMPT":ApprovalReviewerPolicy.block(gate.isHeadOffice(actor),manager,actor.actorId(),subject.accountId(),subject.manager());result.add(new Entry(privacy==null?row:privacy.row(actor,row),source.session()==null?null:source.session().inAt(),source.session()==null?null:source.session().outAt(),category(row,subject.manager()),block==null,block,subject.manager()));}
  }return result;
 }
 public Board board(CurrentActor actor,UUID store,Filter f,int limit,int offset){
  if(limit<1||limit>100||offset<0||offset>50000)throw WorkforceException.invalid();var all=scan(actor,store,f);var counts=new LinkedHashMap<String,Integer>();for(String c:List.of("all","normal","manager","exception","waiting","stale","current"))counts.put(c,0);counts.put("all",all.size());all.forEach(e->counts.merge(e.category(),1,Integer::sum));
  var days=new TreeMap<java.time.LocalDate,List<Entry>>();all.forEach(e->days.computeIfAbsent(e.row().schedule().workDate(),d->new ArrayList<>()).add(e));var progress=new ArrayList<Day>();days.forEach((date,rows)->progress.add(new Day(date,rows.size(),count(rows,"current"),count(rows,"normal"),count(rows,"waiting"),count(rows,"exception"),count(rows,"stale"),count(rows,"manager"))));
  var filtered=all.stream().filter(e->f.category()==null||f.category().equals("all")||e.category().equals(f.category())).toList();var positions=new TreeMap<String,Option>();var ids=all.stream().map(e->e.row().schedule().employeeId()).distinct().toList();for(int i=0;i<ids.size();i+=500)repository.subjects(actor.tenantId(),ids.subList(i,Math.min(i+500,ids.size()))).values().stream().filter(s->s.positionId()!=null&&s.positionName()!=null).forEach(s->positions.put(s.positionName()+s.positionId(),new Option(s.positionId(),s.positionName())));
  return new Board(filtered.subList(Math.min(offset,filtered.size()),Math.min(offset+limit,filtered.size())),filtered.size(),limit,offset,counts,progress,List.copyOf(positions.values()),schedules.timezone(actor.tenantId(),store),(int)filtered.stream().filter(e->e.canReview()&&normal(e.row())).count());
 }
 private int count(List<Entry> rows,String status){return (int)rows.stream().filter(e->e.category().equals(status)).count();}
 public Preview preview(CurrentActor actor,UUID store,Selection c){
  gate.enter(actor,store);gate.manage(actor,store);if(c==null||c.all()==null||c.ids()==null||c.excluded()==null||c.ids().size()>50000||c.excluded().size()>50000)throw WorkforceException.invalid();var all=scan(actor,store,c.filter());var ids=new HashSet<>(c.ids());var excluded=new HashSet<>(c.excluded());if(ids.size()!=c.ids().size()||ids.contains(null)||excluded.contains(null))throw WorkforceException.invalid();
  var visible=all.stream().filter(e->c.filter().category()==null||c.filter().category().equals("all")||e.category().equals(c.filter().category())).toList();var visibleIds=new HashSet<UUID>();visible.forEach(e->visibleIds.add(e.row().schedule().id()));if(!c.all()&&ids.stream().anyMatch(id->!visibleIds.contains(id)))throw WorkforceException.invalid();
  var selected=visible.stream().filter(e->e.canReview()&&normal(e.row())&&(c.all()?!excluded.contains(e.row().schedule().id()):ids.contains(e.row().schedule().id()))).map(e->new Candidate(e.row().schedule().id(),e.row().schedule().employeeName(),e.row().schedule().employeeNo(),e.row().schedule().workDate(),0L,e.row().sourceFingerprint(),e.row().workSeconds(),e.row().paidLeaveSeconds(),e.row().unpaidLeaveSeconds())).toList();if(selected.isEmpty())throw WorkforceException.invalid();
  return new Preview(selected,selected.stream().mapToLong(Candidate::workSeconds).sum(),selected.stream().mapToLong(Candidate::paidLeaveSeconds).sum(),selected.stream().mapToLong(Candidate::unpaidLeaveSeconds).sum());
 }
}
