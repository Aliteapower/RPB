package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.leave.*;
import com.rpb.reservation.workforce.attendance.AttendanceAccess;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static com.rpb.reservation.workforce.leave.LeaveDtos.*;
class WorkforceLeaveBatchPostgresTest extends WorkforceLeavePostgresTest {
 LeaveBatchService batch;
 @BeforeEach void batchSetup(){batch=proxy(new LeaveBatchService(access,new AttendanceAccess(access,repository,scheduling),repository,leaves,scheduling,new LeaveGroupRepository(jdbc,new com.rpb.reservation.workforce.application.WorkforceJson(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()),leaveRepository)));}
 LeaveBatchService.Item item(com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule s,UUID type){return new LeaveBatchService.Item(UUID.randomUUID(),s.version(),new LeaveWrite(s.id(),type,s.startAt(),s.endAt(),"Range leave"));}
 LeaveBatchService.Request request(List<LeaveBatchService.Item> items){return new LeaveBatchService.Request(DATE,DATE.plusDays(3),items);}
 @Test void batchCommitsSeparatePendingRequestsAndReplaysWithoutDuplicates(){
  var shift=shift(store,"none");var a=schedule(store,shift,DATE);var b=schedule(store,shift,DATE.plusDays(2));var t=type(true);
  assertThat(batch.options(worker,store,DATE,DATE.plusDays(3)).items()).hasSize(2).allMatch(s->s.employeeId().equals(employee));
  var cmd=request(List.of(item(a,t.id()),item(b,t.id())));var first=batch.submit(worker,store,cmd);
  assertThat(first).hasSize(2).allMatch(l->l.status().equals("pending"));assertThat(batch.submit(worker,store,cmd)).isEqualTo(first);
  assertThat(leaves.list(worker,store,DATE,DATE.plusDays(3))).hasSize(2);
 }
 @Test void batchSecondFailureRollsBackFirstAndItsAudit(){
  var shift=shift(store,"none");var a=schedule(store,shift,DATE);var b=schedule(store,shift,DATE.plusDays(1));var t=type(true);
  leaves.submit(worker,store,UUID.randomUUID(),new LeaveWrite(b.id(),t.id(),b.startAt(),b.endAt(),"Existing"));
  assertThatThrownBy(()->batch.submit(worker,store,request(List.of(item(a,t.id()),item(b,t.id()))))).isInstanceOf(WorkforceException.class);
  assertThat(leaveRepository.forSchedule(tenant,a.id())).isEmpty();assertThat(leaveRepository.forSchedule(tenant,b.id())).hasSize(1);
 }
 @Test void batchRejectsForeignStoreTenantAndOtherEmployeeWithoutWriting(){
  var s=schedule(store,shift(store,"none"),DATE);var t=type(true);var cmd=request(List.of(item(s,t.id())));
  assertThatThrownBy(()->batch.submit(worker,sibling,cmd)).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->batch.submit(admin,store,cmd)).isInstanceOf(WorkforceException.class);
  var foreign=new CurrentActor(UUID.randomUUID(),worker.actorId(),"staff",worker.roles(),worker.permissions(),worker.storeIds());
  assertThatThrownBy(()->batch.submit(foreign,store,cmd)).isInstanceOf(WorkforceException.class);
  assertThat(leaveRepository.forSchedule(tenant,s.id())).isEmpty();
 }
 @Test void batchRejectsStaleCancelledDisabledAndDuplicateInputs(){
  var s=schedule(store,shift(store,"none"),DATE);var t=type(true);var i=item(s,t.id());
  assertThatThrownBy(()->batch.submit(worker,store,request(List.of(i,i)))).isInstanceOf(WorkforceException.class);
  var stale=new LeaveBatchService.Item(i.id(),99L,i.request());assertThatThrownBy(()->batch.submit(worker,store,request(List.of(stale)))).isInstanceOf(WorkforceException.class);
  types.save(admin,store,t.id(),new LeaveTypeWrite(1L,t.name(),true,false));
  assertThatThrownBy(()->batch.submit(worker,store,request(List.of(i)))).isInstanceOf(WorkforceException.class);
  assertThat(leaveRepository.forSchedule(tenant,s.id())).isEmpty();
 }
 @Test void batchRejectsEmptyOverlongAndOutOfRange(){
  var s=schedule(store,shift(store,"none"),DATE);var t=type(true);var i=item(s,t.id());
  for(var cmd:List.of(request(List.of()),new LeaveBatchService.Request(DATE,DATE.plusDays(31),List.of(i)),new LeaveBatchService.Request(DATE.plusDays(1),DATE.plusDays(2),List.of(i))))assertThatThrownBy(()->batch.submit(worker,store,cmd)).isInstanceOf(WorkforceException.class);
 }
 @Test void batchAppGateDenialsDoNotReadOrWrite(){
  for(var reason:List.of(com.rpb.reservation.appgate.domain.AppGateDenyReason.APP_DISABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.TENANT_APP_NOT_ENABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED)){
   org.mockito.Mockito.doReturn(com.rpb.reservation.appgate.domain.AppGateDecision.deny("workforce",tenant,store,"workforce.view",reason)).when(gate).evaluate(org.mockito.ArgumentMatchers.any());
   assertThatThrownBy(()->batch.options(worker,store,DATE,DATE)).isInstanceOf(WorkforceException.class);
   assertThatThrownBy(()->batch.submit(worker,store,request(List.of()))).isInstanceOf(WorkforceException.class);
  }
 }
}
