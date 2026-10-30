package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.leave.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.attendance.AttendanceAccess;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.DecisionWrite;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
class WorkforceLeaveGroupPostgresTest extends WorkforceLeaveBatchPostgresTest {
 LeaveGroupService groupService;LeaveGroupRepository groupRepository;
 @BeforeEach void groupSetup(){groupRepository=new LeaveGroupRepository(jdbc,new WorkforceJson(new ObjectMapper().findAndRegisterModules()),leaveRepository);groupService=proxy(new LeaveGroupService(access,new AttendanceAccess(access,repository,scheduling),repository,leaves,leaveRepository,groupRepository,scheduling));}
 LeaveBatchService.Request submitGroup(){var shift=shift(store,"none");var a=schedule(store,shift,DATE);var b=schedule(store,shift,DATE.plusDays(1));var t=type(true);var c=request(List.of(item(a,t.id()),item(b,t.id())));batch.submit(worker,store,c);return c;}
 LeaveGroupService.Decision decision(LeaveBatchService.Request c,String status){return new LeaveGroupService.Decision(status,"Checked",c.items().stream().map(i->new LeaveGroupService.DecisionItem(i.id(),1L)).toList());}
 @Test void groupPersistsFullMembershipAcrossFiltersAndAtomicDecisionReplays(){
  var c=submitGroup();var id=c.items().getFirst().id();assertThat(groupService.list(worker,store,DATE,DATE).items()).singleElement().satisfies(g->{assertThat(g.grouped()).isTrue();assertThat(g.items()).hasSize(2);});
  var approved=groupService.decide(admin,store,id,decision(c,"approved"));assertThat(approved).hasSize(2).allMatch(l->l.status().equals("approved"));assertThat(groupService.decide(admin,store,id,decision(c,"approved"))).isEqualTo(approved);
  assertThat(batch.submit(worker,store,c)).hasSize(2);assertThat(jdbc.queryForObject("select count(*) from workforce_leave_groups where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
 }
 @Test void groupPartialReviewThenWholeReviewOnlyAffectsRemainingPendingDays(){
  var c=submitGroup();var first=c.items().getFirst();leaves.decide(admin,store,first.id(),new DecisionWrite(1L,"approved","Single day"));var second=c.items().getLast();
  groupService.decide(admin,store,first.id(),new LeaveGroupService.Decision("rejected","Remaining only",List.of(new LeaveGroupService.DecisionItem(second.id(),1L))));
  assertThat(leaveRepository.find(tenant,first.id()).orElseThrow().status()).isEqualTo("approved");assertThat(leaveRepository.find(tenant,second.id()).orElseThrow().status()).isEqualTo("rejected");
 }
 @Test void groupConflictRollsBackAndRejectsIncompleteMembershipOrStaleVersion(){
  var c=submitGroup();var id=c.items().getFirst().id();var last=c.items().getLast();
  assertThatThrownBy(()->groupService.decide(admin,store,id,new LeaveGroupService.Decision("approved","Missing day",List.of(new LeaveGroupService.DecisionItem(id,1L))))).isInstanceOf(WorkforceException.class);
  var wrong=new LeaveGroupService.Decision("approved","Stale second",List.of(new LeaveGroupService.DecisionItem(id,1L),new LeaveGroupService.DecisionItem(last.id(),99L)));
  assertThatThrownBy(()->groupService.decide(admin,store,id,wrong)).isInstanceOf(WorkforceException.class);assertThat(leaveRepository.find(tenant,id).orElseThrow().status()).isEqualTo("pending");
 }
 @Test void groupRejectsSelfReviewWrongStoreAndOtherEmployeeAndDoesNotMergeHistoricalSingles(){
  var c=submitGroup();var id=c.items().getFirst().id();assertThatThrownBy(()->groupService.decide(worker,store,id,decision(c,"approved"))).isInstanceOf(WorkforceException.class);assertThatThrownBy(()->groupService.decide(admin,sibling,id,decision(c,"approved"))).isInstanceOf(WorkforceException.class);
  var otherId=account("store_staff",Set.of(store),Set.of("workforce.view"));employees.save(admin,store,UUID.randomUUID(),command(0,List.of(store),company,otherId,null));var other=new CurrentActor(tenant,otherId,"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));assertThat(groupService.list(other,store,DATE,DATE.plusDays(1)).items()).isEmpty();
  var s=schedule(store,shift(store,"none"),DATE.plusDays(2));var t=type(false);leaves.submit(worker,store,UUID.randomUUID(),new LeaveDtos.LeaveWrite(s.id(),t.id(),s.startAt(),s.endAt(),"Legacy"));assertThat(groupService.list(worker,store,DATE,DATE.plusDays(3)).items()).hasSize(2).anyMatch(g->!g.grouped()&&g.items().size()==1);
 }
 @Test void groupCannotBeReboundToChangedBatchOrForeignTenant(){
  var c=submitGroup();var altered=new LeaveBatchService.Request(c.from(),c.to(),List.of(c.items().getFirst()));assertThatThrownBy(()->batch.submit(worker,store,altered)).isInstanceOf(WorkforceException.class);
  var foreign=new CurrentActor(UUID.randomUUID(),worker.actorId(),"staff",worker.roles(),worker.permissions(),worker.storeIds());assertThatThrownBy(()->groupService.decide(foreign,store,c.items().getFirst().id(),decision(c,"approved"))).isInstanceOf(WorkforceException.class);
 }
 @Test void groupManagerCanReviewOnlyAuthorizedStoreAndMembershipFkRejectsOtherScope(){
  var c=submitGroup();var managerId=account("store_manager",Set.of(store,sibling),Set.of("workforce.view"));admins.save(admin,store,managerId,new com.rpb.reservation.workforce.application.WorkforceDtos.AdministratorWrite(0L,true,null));
  var manager=new CurrentActor(tenant,managerId,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store,sibling));
  assertThat(groupService.decide(manager,store,c.items().getFirst().id(),decision(c,"approved"))).hasSize(2);
  assertThatThrownBy(()->groupService.decide(manager,sibling,c.items().getFirst().id(),decision(c,"approved"))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->jdbc.update("insert into workforce_leave_group_members(group_id,leave_id,tenant_id,store_id,employee_id,applicant_id) values(?,?,?,?,?,?)",c.items().getFirst().id(),UUID.randomUUID(),tenant,sibling,employee,worker.actorId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
}
