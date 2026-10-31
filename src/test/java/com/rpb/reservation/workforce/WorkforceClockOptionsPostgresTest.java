package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import com.rpb.reservation.workforce.positions.*;
import com.rpb.reservation.workforce.application.WorkforceAudit;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkforceClockOptionsPostgresTest extends WorkforceWorkModePostgresTest {
 final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 AssignmentWrite write(long version,LocalDate from,String mode,boolean inherit,String... allowed){
  var map=new LinkedHashMap<String,Object>();map.put("expectedVersion",version);map.put("effectiveFrom",from.toString());map.put("mode",mode);map.put("inheritPosition",inherit);map.put("allowedModes",List.of(allowed));return mapper.convertValue(map,AssignmentWrite.class);
 }
 ClockWrite punch(UUID schedule,String action,String mode,Location location){var map=new LinkedHashMap<String,Object>();map.put("scheduleId",schedule);map.put("action",action);map.put("mode",mode);map.put("location",location);return mapper.convertValue(map,ClockWrite.class);}
 @Test void optionsSwitchPinsOriginalPolicyAndStoresActualModeNameAndServerTime(){
  policies.save(admin,store,employee,UUID.randomUUID(),write(0,date,"onsite",false,"onsite","field"));policies.saveSite(admin,store,new SiteWrite(0L,office));var s=plan();time.at(s.startAt().toString());
  var first=punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"IN","onsite",here()));
  policies.saveSite(admin,store,new SiteWrite(1L,new Point("Moved",1d,1d,100d)));time.at(s.endAt().toString());var id=UUID.randomUUID();var request=punch(s.id(),"OUT","field",new Location(null,null,null,null,"客户 A"));var last=punches.punch(worker,store,id,request);
  assertThat(last.clockPolicy()).isEqualTo(first.clockPolicy());assertThat(last.inAt()).isEqualTo(s.startAt());assertThat(last.outAt()).isEqualTo(s.endAt());assertThat(punches.punch(worker,store,id,request)).isEqualTo(last);
  var events=attendance.events(tenant,s.id());assertThat(events).hasSize(2);assertThat(mapper.valueToTree(events.getLast()).path("clockMode").asText()).isEqualTo("field");assertThat(events.getLast().location().description()).isEqualTo("客户 A");assertThat(events.getLast().occurredAt()).isEqualTo(s.endAt());
  assertThatThrownBy(()->punches.punch(worker,store,id,punch(s.id(),"OUT","onsite",request.location()))).extracting("code").isEqualTo("WORKFORCE_CONFLICT");
 }
 @Test void optionsSingleModeCannotEscalateAndHomeStillRequiresAddress(){mode("onsite");policies.saveSite(admin,store,new SiteWrite(0L,office));var s=plan();time.at(s.startAt().toString());punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"IN","onsite",here()));time.at(s.endAt().toString());assertThatThrownBy(()->punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"OUT","field",null))).extracting("code").isEqualTo("WORKFORCE_CLOCK_MODE_FORBIDDEN");}
 @Test void optionsNoopHasBoundReceiptAndStaleVersionStillConflicts(){
  var first=policies.save(admin,store,employee,UUID.randomUUID(),write(0,date,"field",false,"field","home"));var id=UUID.randomUUID();var same=write(1,date,"field",false,"home","field");assertThat(policies.save(admin,store,employee,id,same)).isEqualTo(first);assertThat(modes.assignments(tenant,employee)).hasSize(1);
  assertThatThrownBy(()->policies.save(admin,store,employee,UUID.randomUUID(),write(0,date,"field",false,"field","home"))).extracting("code").isEqualTo("WORKFORCE_CONFLICT");
  var changed=policies.save(admin,store,employee,UUID.randomUUID(),write(1,date.plusDays(1),"home",false,"field","home"));assertThat(changed.version()).isEqualTo(2);assertThat(policies.save(admin,store,employee,id,same)).isEqualTo(first);
  assertThatThrownBy(()->policies.save(admin,sibling,employee,id,same)).extracting("code").isEqualTo("WORKFORCE_CONFLICT");assertThatThrownBy(()->policies.save(admin,store,employee,id,write(1,date.plusDays(1),"field",false,"field","home"))).extracting("code").isEqualTo("WORKFORCE_CONFLICT");
 }
 @Test void optionsOldClientPreservesMultiAndInvalidCombinationsFail(){
  var first=policies.save(admin,store,employee,UUID.randomUUID(),write(0,date,"field",false,"field","home"));assertThat(policies.save(admin,store,employee,UUID.randomUUID(),new AssignmentWrite(1L,date,"field",false))).isEqualTo(first);
  for(var values:List.of(new String[]{"field","free"},new String[]{"legacy","field"},new String[]{},new String[]{"home"},new String[]{"field","field"}))assertThatThrownBy(()->policies.save(admin,store,employee,UUID.randomUUID(),write(1,date.plusDays(1),"field",false,values))).extracting("code").isEqualTo("WORKFORCE_INVALID");
 }
 @Test void optionsExpiredHomeAndDuplicateApprovedDateHaveSpecificErrors(){
  var first=homes.submit(worker,store,UUID.randomUUID(),new HomeWrite(office,date));var second=homes.submit(worker,store,UUID.randomUUID(),new HomeWrite(office,date));homes.decide(admin,store,first.id(),new HomeDecision(1L,"approved","checked"));assertThatThrownBy(()->homes.decide(admin,store,second.id(),new HomeDecision(1L,"approved","checked"))).extracting("code").isEqualTo("WORKFORCE_HOME_EFFECTIVE_DATE_CONFLICT");
  time.at("2026-10-29T04:00:00Z");assertThatThrownBy(()->homes.decide(admin,store,second.id(),new HomeDecision(1L,"approved","checked"))).extracting("code").isEqualTo("WORKFORCE_HOME_REQUEST_EXPIRED");assertThat(modes.address(tenant,second.id()).orElseThrow().status()).isEqualTo("pending");
 }
 @Test void optionsPositionRenameDoesNotReadoptAndOldClientPreservesCatalogPermissions(){
  var service=proxy(new PositionService(access,new PositionRepository(jdbc),new WorkforceAudit(logs,mapper)));
  var p=service.save(admin,store,UUID.randomUUID(),new PositionDtos.PositionWrite(0L,"Mixed",true,"onsite",List.of("onsite","field")));
  jdbc.update("update workforce_employees set position_id=? where tenant_id=? and id=?",p.id(),tenant,employee);
  var first=policies.save(admin,store,employee,UUID.randomUUID(),write(0,date,"onsite",true,"onsite","field"));
  var renamed=service.save(admin,store,p.id(),new PositionDtos.PositionWrite(1L,"Renamed",true,"onsite"));assertThat(renamed.attendanceModes()).containsExactly("field","onsite");
  assertThat(policies.save(admin,store,employee,UUID.randomUUID(),new AssignmentWrite(1L,date,"legacy",true))).isEqualTo(first);
  assertThatThrownBy(()->policies.save(admin,store,employee,UUID.randomUUID(),write(1,date.plusDays(1),"onsite",true,"onsite","home"))).extracting("code").isEqualTo("WORKFORCE_INVALID");
  var personal=policies.save(admin,store,employee,UUID.randomUUID(),write(1,date.plusDays(1),"onsite",false,"onsite","field"));assertThat(personal.version()).isEqualTo(2);assertThat(personal.origin()).isEqualTo("personal");
 }
 @Test void optionsMissingHomeDoesNotFallBackAndApprovalWithoutPunchOmitsAllGeometry(){
  policies.save(admin,store,employee,UUID.randomUUID(),write(0,date,"field",false,"field","home","onsite"));var s=plan();
  var unpunched=geo.approvalPolicies(tenant,List.of(s),Map.of()).get(s.id());assertThat(unpunched.options()).hasSize(3).allMatch(o->o.point()==null&&o.addressId()==null&&o.siteVersion()==null);
  time.at(s.startAt().toString());assertThatThrownBy(()->punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"IN","home",here()))).extracting("code").isEqualTo("WORKFORCE_HOME_ADDRESS_REQUIRED");assertThatThrownBy(()->punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"IN","onsite",here()))).extracting("code").isEqualTo("WORKFORCE_CLOCK_SITE_REQUIRED");
  var first=punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"IN",null,null));assertThat(first.clockPolicy().mode()).isEqualTo("field");assertThat(first.clockPolicy().options()).hasSize(3);
  assertThatThrownBy(()->punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"OUT","legacy",null))).extracting("code").isEqualTo("WORKFORCE_CLOCK_MODE_FORBIDDEN");
 }
 @Test void optionsNestedHomeIsRedactedEvenWhenActualModeIsFieldAndHistoryStaysIntact(){
  policies.save(admin,store,employee,UUID.randomUUID(),write(0,date,"field",false,"field","home"));var home=homes.submit(worker,store,UUID.randomUUID(),new HomeWrite(office,date));homes.decide(admin,store,home.id(),new HomeDecision(1L,"approved","checked"));var s=plan();time.at(s.startAt().toString());var first=punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"IN","field",new Location(null,null,null,null,"Client")));time.at(s.endAt().toString());punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"OUT","home",here()));
  var managerId=account("store_manager",Set.of(store),Set.of("workforce.view"));admins.save(admin,store,managerId,new com.rpb.reservation.workforce.application.WorkforceDtos.AdministratorWrite(0L,true,null));var manager=new com.rpb.reservation.walkin.api.CurrentActor(tenant,managerId,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store));
  var privacy=new com.rpb.reservation.workforce.workmode.WorkModePrivacy(access,repository);var scope=new com.rpb.reservation.workforce.attendance.AttendanceAccess(access,repository,schedules);var query=new com.rpb.reservation.workforce.attendance.AttendanceQueryService(access,scope,schedules,attendance,time,geo,privacy);var response=query.detail(manager,store,s.id());
  assertThat(response.attendance().session().clockPolicy().mode()).isEqualTo("field");assertThat(response.attendance().session().clockPolicy().options()).filteredOn(o->o.mode().equals("home")).allMatch(o->o.point()==null&&o.addressId()==null&&o.addressVersion()==0);
  assertThat(response.events()).extracting(Event::clockMode).containsExactly("field","home");assertThat(response.events()).allMatch(e->e.location()==null);assertThat(first.clockPolicy().options()).filteredOn(o->o.mode().equals("home")).allMatch(o->o.point().equals(office));
  var json=new com.rpb.reservation.workforce.application.WorkforceJson(mapper);var source=new com.rpb.reservation.workforce.approval.ApprovalSources(attendance,new com.rpb.reservation.workforce.leave.LeaveRepository(jdbc),new com.rpb.reservation.workforce.calendar.CalendarRepository(jdbc),json,time,geo);var ar=new com.rpb.reservation.workforce.approval.ApprovalRepository(jdbc,json);var approvals=proxy(new com.rpb.reservation.workforce.approval.ApprovalService(access,scope,repository,schedules,ar,source,new WorkforceAudit(logs,mapper),time,privacy));
  var detail=approvals.detail(admin,store,s.id());approvals.approve(admin,store,s.id(),new com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalWrite(0L,detail.row().sourceFingerprint(),"checked"));var redacted=approvals.detail(manager,store,s.id()).history().getFirst();assertThat(redacted.snapshot().clockPolicy().options()).filteredOn(o->o.mode().equals("home")).allMatch(o->o.point()==null&&o.addressId()==null);assertThat(redacted.snapshot().session().clockPolicy().options()).filteredOn(o->o.mode().equals("home")).allMatch(o->o.point()==null);assertThat(redacted.sourceFingerprint()).isEqualTo(detail.row().sourceFingerprint());assertThat(ar.history(tenant,s.id()).getFirst().snapshot().clockPolicy().options()).filteredOn(o->o.mode().equals("home")).allMatch(o->o.point().equals(office));
 }
 @Test void optionsCrossMidnightBreakSwitchKeepsOneSessionAndOriginalLocationSnapshot(){
  policies.save(admin,store,employee,UUID.randomUUID(),write(0,date,"onsite",false,"onsite","field"));policies.saveSite(admin,store,new SiteWrite(0L,office));
  var shift=shifts.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ShiftWrite(0L,"Night","22:00","06:00",true,"manual",0,0,0,0,false,true));var s=roster.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ScheduleWrite(0L,employee,shift.id(),date,"scheduled"));time.at(s.startAt().toString());var first=punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"IN","onsite",here()));
  policies.save(admin,store,employee,UUID.randomUUID(),write(1,date.plusDays(1),"field",false,"field"));policies.saveSite(admin,store,new SiteWrite(1L,new Point("Moved",1d,1d,100d)));
  time.at(s.startAt().plusSeconds(3*3600).toString());punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"BREAK_START","field",null));assertThatThrownBy(()->punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"OUT","field",null))).extracting("code").isEqualTo("WORKFORCE_CLOCK_STATE");
  time.at(s.startAt().plusSeconds(4*3600).toString());punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"BREAK_END","onsite",here()));time.at(s.endAt().toString());var last=punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"OUT","field",null));
  assertThat(last.id()).isEqualTo(first.id());assertThat(last.clockPolicy()).isEqualTo(first.clockPolicy());assertThat(new com.rpb.reservation.workforce.attendance.AttendanceCalculator().calculate(s.shift(),s.startAt(),last.inAt(),last.outAt(),last.breaks(),false).workSeconds()).isEqualTo(7*3600);assertThat(attendance.events(tenant,s.id())).hasSize(4);
 }
 @Test void optionsReceiptActorBindingAuditRollbackAndLegacyReplayRemainSafe(){
  var id=UUID.randomUUID();var request=write(0,date,"field",false,"field","home");var original=policies.save(admin,store,employee,id,request);var secondId=account("tenant_admin",Set.of(store,sibling,otherStore),admin.permissions());var other=new com.rpb.reservation.walkin.api.CurrentActor(tenant,secondId,"staff",admin.roles(),admin.permissions(),admin.storeIds());assertThatThrownBy(()->policies.save(other,store,employee,id,request)).extracting("code").isEqualTo("WORKFORCE_CONFLICT");
  var next=UUID.randomUUID();org.mockito.Mockito.doThrow(new IllegalStateException("audit")).when(logs).append(org.mockito.ArgumentMatchers.any(com.rpb.reservation.common.scope.StoreScope.class),org.mockito.ArgumentMatchers.any());assertThatThrownBy(()->policies.save(admin,store,employee,next,write(1,date.plusDays(1),"home",false,"field","home"))).isInstanceOf(IllegalStateException.class);assertThat(modes.assignments(tenant,employee)).containsExactly(original);assertThat(jdbc.queryForObject("select count(*) from workforce_work_mode_commands where tenant_id=? and command_id=?",Integer.class,tenant,next)).isZero();
 }
 @Test void optionsConcurrentChangesHaveOneWinnerAndNoopDoesNotGrowAudit()throws Exception{
  var first=policies.save(admin,store,employee,UUID.randomUUID(),write(0,date,"field",false,"field","home"));org.mockito.Mockito.clearInvocations(logs);policies.save(admin,store,employee,UUID.randomUUID(),write(1,date,"field",false,"field","home"));org.mockito.Mockito.verifyNoInteractions(logs);
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);try{var calls=new ArrayList<java.util.concurrent.Future<Integer>>();for(var mode:List.of("field","home"))calls.add(pool.submit(()->{start.await();try{policies.save(admin,store,employee,UUID.randomUUID(),write(1,date.plusDays(1),mode,false,mode));return 200;}catch(com.rpb.reservation.workforce.domain.WorkforceException e){return e.status();}}));start.countDown();assertThat(List.of(calls.get(0).get(),calls.get(1).get())).containsExactlyInAnyOrder(200,409);assertThat(modes.assignments(tenant,employee)).hasSize(2);assertThat(modes.assignments(tenant,employee).getFirst()).isEqualTo(first);}finally{pool.shutdownNow();}
 }
 @Test void optionsPreMigrationCommandsReplayWithoutAddingModeOrPolicyToOldJson()throws Exception{
  var id=UUID.randomUUID();var old=new Assignment(id,employee,date,"field","personal",null,0,1);modes.insert(tenant,old);assertThat(policies.save(admin,store,employee,id,new AssignmentWrite(0L,date,"field",false))).isEqualTo(old);
  var s=plan();var session=new Session(UUID.randomUUID(),s.id(),employee,store,s.startAt(),null,null,List.of(),false,1);attendance.save(tenant,session);var command=UUID.randomUUID();attendance.event(tenant,command,worker.actorId(),"IN",s.startAt(),session);var before=jdbc.queryForObject("select response_json::text from workforce_clock_events where id=?",String.class,command);
  time.at(s.endAt().toString());var replay=punches.punch(worker,store,command,new ClockWrite(s.id(),"IN"));assertThat(replay).isEqualTo(session);assertThat(mapper.readTree(before)).isEqualTo(mapper.readTree(mapper.writeValueAsString(replay)));assertThat(jdbc.queryForObject("select response_json::text from workforce_clock_events where id=?",String.class,command)).isEqualTo(before);assertThat(attendance.events(tenant,s.id()).getFirst().clockMode()).isNull();
  assertThatThrownBy(()->punches.punch(worker,store,UUID.randomUUID(),punch(s.id(),"OUT","field",null))).extracting("code").isEqualTo("WORKFORCE_CLOCK_MODE_FORBIDDEN");assertThat(punches.punch(worker,store,UUID.randomUUID(),new ClockWrite(s.id(),"OUT")).clockPolicy()).isNull();
 }
 @Test void optionsPositionExplicitSingletonEqualsHistoricNullWithoutVersionOrAudit(){
  var service=proxy(new PositionService(access,new PositionRepository(jdbc),new WorkforceAudit(logs,mapper)));var first=service.save(admin,store,UUID.randomUUID(),new PositionDtos.PositionWrite(0L,"Original",true,"field"));org.mockito.Mockito.clearInvocations(logs);
  assertThat(service.save(admin,store,first.id(),new PositionDtos.PositionWrite(1L,"Original",true,"field",List.of("field")))).isEqualTo(first);assertThat(service.save(admin,store,first.id(),new PositionDtos.PositionWrite(0L,"Original",true,"field",List.of("field")))).isEqualTo(first);org.mockito.Mockito.verifyNoInteractions(logs);
  var unset=service.save(admin,store,UUID.randomUUID(),new PositionDtos.PositionWrite(0L,"Unset",true));assertThat(service.save(admin,store,unset.id(),new PositionDtos.PositionWrite(1L,"Unset",true))).isEqualTo(unset);assertThat(unset.attendanceMode()).isNull();
 }
 @Test void optionsPositionActualPermissionChangeCreatesVersionAndAudit(){
  var service=proxy(new PositionService(access,new PositionRepository(jdbc),new WorkforceAudit(logs,mapper)));var first=service.save(admin,store,UUID.randomUUID(),new PositionDtos.PositionWrite(0L,"Original",true,"field"));org.mockito.Mockito.clearInvocations(logs);
  var changed=service.save(admin,store,first.id(),new PositionDtos.PositionWrite(1L,"Original",true,"field",List.of("field","home")));assertThat(changed.version()).isEqualTo(2);assertThat(changed.attendanceModes()).containsExactly("field","home");org.mockito.Mockito.verify(logs).append(org.mockito.ArgumentMatchers.any(com.rpb.reservation.common.scope.StoreScope.class),org.mockito.ArgumentMatchers.any());
  assertThat(service.save(admin,store,first.id(),new PositionDtos.PositionWrite(1L,"Original",true,"field",List.of("home","field")))).isEqualTo(changed);
 }
}
