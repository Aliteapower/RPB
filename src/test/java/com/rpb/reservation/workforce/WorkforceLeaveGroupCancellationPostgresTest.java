package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.leave.*;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.DecisionWrite;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WorkforceLeaveGroupCancellationPostgresTest extends WorkforceLeaveGroupPostgresTest {
 LeaveGroupService.Decision command(List<LeaveDtos.Leave> rows,String status,String note){return new LeaveGroupService.Decision(status,note,rows.stream().map(l->new LeaveGroupService.DecisionItem(l.id(),l.version())).toList());}
 List<LeaveDtos.Leave> rows(LeaveBatchService.Request c){return leaveRepository.forGroup(tenant,store,c.items().getFirst().id());}
 @Test void cancelWithdrawAllPendingIsAtomicReplayableAndApplicantOnly(){
  var c=submitGroup();var id=c.items().getFirst().id();var cmd=command(rows(c),"withdrawn","");
  assertThatThrownBy(()->groupService.decide(admin,store,id,cmd)).isInstanceOf(WorkforceException.class);
  var result=groupService.decide(worker,store,id,cmd);assertThat(result).hasSize(2).allMatch(l->l.status().equals("withdrawn"));assertThat(groupService.decide(worker,store,id,cmd)).isEqualTo(result);
  assertThatThrownBy(()->groupService.decide(admin,store,id,decision(c,"approved"))).isInstanceOf(WorkforceException.class);assertThat(rows(c)).allMatch(l->l.status().equals("withdrawn"));
 }
 @Test void cancelMixedGroupWithdrawsPendingAndOnlyRequestsApprovedDates(){
  var c=submitGroup();var id=c.items().getFirst().id();var a=leaves.decide(admin,store,id,new DecisionWrite(1L,"approved","Single approval"));
  var pending=rows(c).stream().filter(l->l.status().equals("pending")).toList();groupService.decide(worker,store,id,command(pending,"withdrawn",""));
  assertThat(leaveRepository.find(tenant,id).orElseThrow().status()).isEqualTo("approved");
  var request=command(List.of(a),"cancel_requested","Return to work");var waiting=groupService.decide(worker,store,id,request);assertThat(waiting).singleElement().extracting(LeaveDtos.Leave::status).isEqualTo("cancel_requested");assertThat(groupService.decide(worker,store,id,request)).isEqualTo(waiting);
  assertThatThrownBy(()->groupService.decide(worker,store,id,command(waiting,"cancelled","Self review"))).isInstanceOf(WorkforceException.class);
  groupService.decide(admin,store,id,command(waiting,"cancelled","Checked return"));assertThat(rows(c)).extracting(LeaveDtos.Leave::status).containsExactlyInAnyOrder("cancelled","withdrawn");
 }
 @Test void cancelManagerCanReviewRemainingCancellationAfterSingleDayRejection(){
  var c=submitGroup();var id=c.items().getFirst().id();var approved=groupService.decide(admin,store,id,decision(c,"approved"));var waiting=groupService.decide(worker,store,id,command(approved,"cancel_requested","Return"));
  leaves.decide(admin,store,waiting.getFirst().id(),new DecisionWrite(waiting.getFirst().version(),"cancel_rejected","Keep this day"));
  var managerId=account("store_manager",Set.of(store),Set.of("workforce.view"));admins.save(admin,store,managerId,new com.rpb.reservation.workforce.application.WorkforceDtos.AdministratorWrite(0L,true,null));var manager=new CurrentActor(tenant,managerId,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store));
  var remaining=rows(c).stream().filter(l->l.status().equals("cancel_requested")).toList();var cmd=command(remaining,"cancelled","Approve remaining return");var result=groupService.decide(manager,store,id,cmd);assertThat(groupService.decide(manager,store,id,cmd)).isEqualTo(result);
  assertThat(rows(c)).extracting(LeaveDtos.Leave::status).containsExactlyInAnyOrder("approved","cancelled");assertThat(leaveRepository.find(tenant,waiting.getFirst().id()).orElseThrow().reviewNote()).isEqualTo("Keep this day");
 }
 @Test void cancelVersionConflictOrMissingEligibleDayRollsBackEverything(){
  var c=submitGroup();var id=c.items().getFirst().id();var approved=groupService.decide(admin,store,id,decision(c,"approved"));
  assertThatThrownBy(()->groupService.decide(worker,store,id,command(List.of(approved.getFirst()),"cancel_requested","Missing one"))).isInstanceOf(WorkforceException.class);
  var stale=new LeaveGroupService.Decision("cancel_requested","Stale second",List.of(new LeaveGroupService.DecisionItem(approved.getFirst().id(),approved.getFirst().version()),new LeaveGroupService.DecisionItem(approved.getLast().id(),99L)));
  assertThatThrownBy(()->groupService.decide(worker,store,id,stale)).isInstanceOf(WorkforceException.class);assertThat(rows(c)).allMatch(l->l.status().equals("approved"));
 }
 @Test void cancelWholeRejectionKeepsOriginalApprovalAndSupportsLaterRequest(){
  var c=submitGroup();var id=c.items().getFirst().id();var approved=groupService.decide(admin,store,id,decision(c,"approved"));var waiting=groupService.decide(worker,store,id,command(approved,"cancel_requested","Return"));
  var cmd=command(waiting,"cancel_rejected","Leave still needed");var result=groupService.decide(admin,store,id,cmd);assertThat(result).allMatch(l->l.status().equals("approved"));assertThat(groupService.decide(admin,store,id,cmd)).isEqualTo(result);
  assertThat(groupService.decide(worker,store,id,command(result,"cancel_requested","New return request"))).allMatch(l->l.status().equals("cancel_requested"));
 }
 @Test void cancelCannotCrossStoreTenantOrBypassSelfReviewAsEmployeeManager(){
  var c=submitGroup();var id=c.items().getFirst().id();var approved=groupService.decide(admin,store,id,decision(c,"approved"));var request=command(approved,"cancel_requested","Return");
  assertThatThrownBy(()->groupService.decide(worker,sibling,id,request)).isInstanceOf(WorkforceException.class);
  var foreign=new CurrentActor(UUID.randomUUID(),worker.actorId(),"staff",worker.roles(),worker.permissions(),worker.storeIds());assertThatThrownBy(()->groupService.decide(foreign,store,id,request)).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->groupService.decide(admin,store,id,request)).isInstanceOf(WorkforceException.class);
  admins.save(admin,store,worker.actorId(),new com.rpb.reservation.workforce.application.WorkforceDtos.AdministratorWrite(0L,true,null));var waiting=groupService.decide(worker,store,id,request);assertThatThrownBy(()->groupService.decide(worker,store,id,command(waiting,"cancelled","Self"))).isInstanceOf(WorkforceException.class);
 }
}
