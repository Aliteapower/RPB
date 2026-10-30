package com.rpb.reservation.workforce.leave;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class LeaveGroupRepository {
 public record Owner(UUID employeeId,UUID applicantId) {}
 private final JdbcTemplate jdbc;private final WorkforceJson json;private final LeaveRepository leaves;
 public LeaveGroupRepository(JdbcTemplate jdbc,WorkforceJson json,LeaveRepository leaves){this.jdbc=jdbc;this.json=json;this.leaves=leaves;}
 public record Range(java.time.LocalDate from,java.time.LocalDate to) {}
 public Range range(UUID tenant,UUID store,UUID id){return jdbc.queryForObject("select (request->>'from')::date,(request->>'to')::date from workforce_leave_groups where tenant_id=? and store_id=? and id=?",(r,n)->new Range(r.getDate(1).toLocalDate(),r.getDate(2).toLocalDate()),tenant,store,id);}
 public Optional<Owner> owner(UUID tenant,UUID store,UUID id){return jdbc.query("select employee_id,applicant_id from workforce_leave_groups where tenant_id=? and store_id=? and id=?",(r,n)->new Owner(r.getObject(1,UUID.class),r.getObject(2,UUID.class)),tenant,store,id).stream().findFirst();}
 public Optional<List<LeaveDtos.Leave>> replay(CurrentActor actor,UUID store,UUID employee,LeaveBatchService.Request request){
  var id=request.items().getFirst().id();var owner=owner(actor.tenantId(),store,id);if(owner.isEmpty())return Optional.empty();
  if(!owner.get().employeeId().equals(employee)||!owner.get().applicantId().equals(actor.actorId())||!Boolean.TRUE.equals(jdbc.queryForObject("select request=?::jsonb from workforce_leave_groups where tenant_id=? and store_id=? and id=?",Boolean.class,json.write(request),actor.tenantId(),store,id)))throw WorkforceException.conflict();
  return Optional.of(leaves.forGroup(actor.tenantId(),store,id));
 }
 public void save(CurrentActor actor,UUID store,UUID employee,LeaveBatchService.Request request,List<LeaveDtos.Leave> items){
  UUID id=request.items().getFirst().id();jdbc.update("insert into workforce_leave_groups(id,tenant_id,store_id,employee_id,applicant_id,request) values(?,?,?,?,?,?::jsonb)",id,actor.tenantId(),store,employee,actor.actorId(),json.write(request));
  for(var l:items)jdbc.update("insert into workforce_leave_group_members(group_id,leave_id,tenant_id,store_id,employee_id,applicant_id) values(?,?,?,?,?,?)",id,l.id(),actor.tenantId(),store,employee,actor.actorId());
 }
 public Map<UUID,UUID> memberships(UUID tenant,UUID store,List<LeaveDtos.Leave> items){
  if(items.isEmpty())return Map.of();var args=new ArrayList<Object>(List.of(tenant,store));items.forEach(l->args.add(l.id()));
  var result=new HashMap<UUID,UUID>();jdbc.query("select leave_id,group_id from workforce_leave_group_members where tenant_id=? and store_id=? and leave_id in ("+String.join(",",Collections.nCopies(items.size(),"?"))+")",r->{result.put(r.getObject(1,UUID.class),r.getObject(2,UUID.class));},args.toArray());return result;
 }
}
