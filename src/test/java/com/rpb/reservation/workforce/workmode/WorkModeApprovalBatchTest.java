package com.rpb.reservation.workforce.workmode;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.Session;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule;
import java.time.*;import java.util.*;import org.junit.jupiter.api.Test;
class WorkModeApprovalBatchTest {
 final UUID tenant=UUID.randomUUID(),employee=UUID.randomUUID(),store=UUID.randomUUID();
 final LocalDate date=LocalDate.of(2026,10,10);
 Schedule schedule(LocalDate day){return new Schedule(UUID.randomUUID(),employee,"Employee","E1",null,day,Instant.EPOCH,Instant.EPOCH,"Asia/Singapore","scheduled",null,null,1);}
 @Test void unpunchedSchedulesMatchSinglePolicyAndNeverLoadGeometry(){
  var repository=mock(WorkModeRepository.class);var resolver=new WorkModeResolver(repository);var clock=new WorkModeClock(repository,resolver,Clock.systemUTC());
  var home=new Assignment(UUID.randomUUID(),employee,date,"home","personal",null,0,4);
  var future=new Assignment(UUID.randomUUID(),employee,date.plusDays(5),"free","personal",null,0,5);
  var history=List.of(home,future);when(repository.assignmentsFor(tenant,List.of(employee))).thenReturn(Map.of(employee,history));
  var before=schedule(date.minusDays(1));var current=schedule(date);var later=schedule(date.plusDays(5));
  var result=clock.approvalPolicies(tenant,List.of(before,current,later),Map.of());
  assertThat(result).containsEntry(before.id(),null).containsEntry(current.id(),new ClockPolicy("home",home.id(),4,null,null,0)).containsEntry(later.id(),new ClockPolicy("free",future.id(),5,null,null,0));
  verify(repository).assignmentsFor(tenant,List.of(employee));verifyNoMoreInteractions(repository);
  // The legacy single interface remains the oracle, including tie-breaking and JSON shape.
  when(repository.assignments(tenant,employee)).thenReturn(history);when(repository.addresses(tenant,employee)).thenReturn(List.of());
  assertThat(clock.approvalPolicy(tenant,employee,store,date,null)).isEqualTo(result.get(current.id()));
 }
 @Test void punchedRowsKeepTheirHistoricalPolicyIncludingGeometryAndNullLegacySnapshot(){
  var repository=mock(WorkModeRepository.class);var clock=new WorkModeClock(repository,new WorkModeResolver(repository),Clock.systemUTC());
  var first=schedule(date);var second=schedule(date.plusDays(1));var policy=new ClockPolicy("onsite",UUID.randomUUID(),7,new Point("Original",1d,2d,50d),null,0,9L);
  var session=new Session(UUID.randomUUID(),first.id(),employee,store,Instant.EPOCH,null,null,List.of(),false,1,null,policy);
  var legacy=new Session(UUID.randomUUID(),second.id(),employee,store,Instant.EPOCH,null,null,List.of(),false,1);
  when(repository.assignmentsFor(tenant,List.of())).thenReturn(Map.of());
  var result=clock.approvalPolicies(tenant,List.of(first,second),Map.of(first.id(),session,second.id(),legacy));
  assertThat(result.get(first.id())).isSameAs(policy);assertThat(result).containsEntry(second.id(),null);
  verify(repository).assignmentsFor(tenant,List.of());verifyNoMoreInteractions(repository);
 }
 @Test void equalEffectiveDatesPreserveFirstAssignmentAsSingleResolverDoes(){
  var resolver=new WorkModeResolver(mock(WorkModeRepository.class));var first=new Assignment(UUID.randomUUID(),employee,date,"field","personal",null,0,2);var second=new Assignment(UUID.randomUUID(),employee,date,"free","personal",null,0,3);
  assertThat(resolver.approvalSnapshot(List.of(first,second),date)).isEqualTo(new ClockPolicy("field",first.id(),2,null,null,0));
 }
}
