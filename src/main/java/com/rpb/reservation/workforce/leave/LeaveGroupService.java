package com.rpb.reservation.workforce.leave;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.attendance.AttendanceAccess;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.DecisionWrite;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.scheduling.SchedulingRepository;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.workforce.leave.LeaveDtos.Leave;
@Service
@Transactional(readOnly=true)
public class LeaveGroupService {
 public record Group(UUID id,boolean grouped,List<Leave> items,LocalDate from,LocalDate to) {}
 public record Page(String timezone,List<Group> items) {}
 public record DecisionItem(UUID id,Long expectedVersion) {}
 public record Decision(String status,String note,List<DecisionItem> items) {}
 private final WorkforceAccess gate;private final AttendanceAccess access;private final WorkforceEmployeeRepository employees;private final LeaveService leaves;private final LeaveRepository records;private final LeaveGroupRepository groups;private final SchedulingRepository schedules;
 public LeaveGroupService(WorkforceAccess gate,AttendanceAccess access,WorkforceEmployeeRepository employees,LeaveService leaves,LeaveRepository records,LeaveGroupRepository groups,SchedulingRepository schedules){this.gate=gate;this.access=access;this.employees=employees;this.leaves=leaves;this.records=records;this.groups=groups;this.schedules=schedules;}
 private LeaveGroupRepository.Owner visible(CurrentActor actor,UUID store,UUID id){
  gate.enter(actor,store);var owner=groups.owner(actor.tenantId(),store,id).orElseThrow(WorkforceException::missing);
  if(!access.manager(actor,store)&&!access.self(actor,store).id().equals(owner.employeeId()))throw WorkforceException.missing();return owner;
 }
 public Page list(CurrentActor actor,UUID store,LocalDate from,LocalDate to){
  var matching=leaves.list(actor,store,from,to);var links=groups.memberships(actor.tenantId(),store,matching);var seen=new HashSet<UUID>();var result=new ArrayList<Group>();int count=0;
  for(var l:matching){var id=links.get(l.id());if(id==null){result.add(new Group(l.id(),false,List.of(l),from,to));count++;}else if(seen.add(id)){visible(actor,store,id);var all=records.forGroup(actor.tenantId(),store,id);count+=all.size();var range=groups.range(actor.tenantId(),store,id);result.add(new Group(id,true,all,range.from(),range.to()));}if(count>5000)throw WorkforceException.invalid();}
  return new Page(schedules.timezone(actor.tenantId(),store),result);
 }
 @Transactional public List<Leave> decide(CurrentActor actor,UUID store,UUID id,Decision c){
  gate.sourceWrite(actor);var owner=visible(actor,store,id);
  if(c==null||c.status()==null||!Set.of("approved","rejected","withdrawn","cancel_requested","cancelled","cancel_rejected").contains(c.status())||c.note()==null||(!c.status().equals("withdrawn")&&c.note().isBlank())||c.note().trim().length()>500||c.items()==null||c.items().isEmpty()||c.items().size()>1000)throw WorkforceException.invalid();
  boolean personal=Set.of("withdrawn","cancel_requested").contains(c.status());
  if(personal){if(!actor.actorId().equals(owner.applicantId()))throw WorkforceException.forbidden();}
  else if(!access.manager(actor,store)||actor.actorId().equals(owner.applicantId())||employees.byAccount(actor.tenantId(),actor.actorId()).map(e->e.id().equals(owner.employeeId())).orElse(false))throw WorkforceException.forbidden();
  String source=switch(c.status()){
   case "cancel_requested" -> "approved";
   case "cancelled","cancel_rejected" -> "cancel_requested";
   default -> "pending";
  };
  employees.lock(owner.employeeId());var all=records.forGroup(actor.tenantId(),store,id);var members=new HashMap<UUID,Leave>();all.forEach(l->members.put(l.id(),l));var selected=new HashSet<UUID>();
  for(var item:c.items())if(item==null||item.id()==null||item.expectedVersion()==null||item.expectedVersion()<1||!members.containsKey(item.id())||!selected.add(item.id()))throw WorkforceException.conflict();
  if(all.stream().anyMatch(l->l.status().equals(source)&&!selected.contains(l.id())))throw WorkforceException.conflict();
  var result=new ArrayList<Leave>();for(var item:c.items())result.add(leaves.decide(actor,store,item.id(),new DecisionWrite(item.expectedVersion(),c.status(),c.note())));return result;
 }
}
