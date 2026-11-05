package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.positions.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.rpb.reservation.workforce.positions.PositionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;

class WorkforcePositionPostgresTest extends WorkforcePostgresTest {
 PositionRepository positions;PositionService service;
 @BeforeEach void positions(){positions=new PositionRepository(jdbc);service=proxy(new PositionService(access,positions,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules())));}
 @Test void createRenameDisableReplayAndSoftDelete(){
  var id=UUID.randomUUID();var p=service.save(admin,store,id,new PositionWrite(0L,"  厨师  ",true));assertThat(p.name()).isEqualTo("厨师");
  assertThat(service.save(admin,store,id,new PositionWrite(0L,"厨师",true))).isEqualTo(p);
  var renamed=service.save(admin,store,id,new PositionWrite(1L,"主厨",false));assertThat(renamed.version()).isEqualTo(2);
  assertThat(service.save(admin,store,id,new PositionWrite(1L,"主厨",false))).isEqualTo(renamed);
  assertThatThrownBy(()->service.save(admin,store,id,new PositionWrite(1L,"陈旧修改",true))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(409);
  var deleted=service.delete(admin,store,id,new DeleteWrite(2L));assertThat(deleted.deleted()).isTrue();assertThat(service.delete(admin,store,id,new DeleteWrite(2L))).isEqualTo(deleted);
  assertThat(service.list(admin,store)).noneMatch(x->x.id().equals(id));
  assertThatThrownBy(()->service.save(admin,store,id,new PositionWrite(0L,"复活",true))).isInstanceOf(WorkforceException.class);
 }
 @Test void employeeUsesCatalogDisabledCanBeRetainedButNotAssignedAndReferencedCannotDelete(){
  var p=service.save(admin,store,UUID.randomUUID(),new PositionWrite(0L,"Kitchen",true));
  var id=UUID.randomUUID();var c=command(0,List.of(store),company,null,null);var e=employees.save(admin,store,id,choose(c,p.id()));
  assertThat(e.positionId()).isEqualTo(p.id());assertThat(e.jobTitle()).isEqualTo("Kitchen");
  service.save(admin,store,p.id(),new PositionWrite(1L,"Chef",false));assertThat(employees.get(admin,store,id).jobTitle()).isEqualTo("Chef");
  assertThatThrownBy(()->service.delete(admin,store,p.id(),new DeleteWrite(2L))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_POSITION_IN_USE");
  assertThat(employees.save(admin,store,id,choose(command(1,List.of(store),company,null,null),p.id())).positionId()).isEqualTo(p.id());
  assertThatThrownBy(()->employees.save(admin,store,UUID.randomUUID(),choose(c,p.id()))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_POSITION_UNAVAILABLE");
  assertThatThrownBy(()->employees.save(admin,store,UUID.randomUUID(),choose(c,UUID.randomUUID()))).isInstanceOf(WorkforceException.class);
 }
 @Test void managerCanChooseButCannotMaintainAndOtherTenantIdsAreInvisible(){
  var id=account("store_manager",Set.of(store),Set.of("workforce.view"));admins.save(admin,store,id,new WorkforceDtos.AdministratorWrite(0L,true,null));
  var manager=new CurrentActor(tenant,id,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store));
  assertThat(service.list(manager,store)).isNotEmpty();assertThatThrownBy(()->service.save(manager,store,UUID.randomUUID(),new PositionWrite(0L,"Hack",true))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
  var other=UUID.randomUUID();jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Other','active')",other,"t-"+other);
  var foreign=UUID.randomUUID();jdbc.update("insert into workforce_positions(id,tenant_id,name,enabled,version) values(?,?,'Foreign',true,1)",foreign,other);
  assertThat(service.list(admin,store)).noneMatch(p->p.id().equals(foreign));assertThatThrownBy(()->employees.save(admin,store,UUID.randomUUID(),choose(command(0,List.of(store),company,null,null),foreign))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(404);
  var employee=UUID.randomUUID();employees.save(admin,store,employee,command(0,List.of(store),company,null,null));
  assertThatThrownBy(()->jdbc.update("update workforce_employees set position_id=? where tenant_id=? and id=?",foreign,tenant,employee)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void duplicateNameAndAuditFailureDoNotWrite(){
  service.save(admin,store,UUID.randomUUID(),new PositionWrite(0L,"Cashier",true));
  assertThatThrownBy(()->service.save(admin,store,UUID.randomUUID(),new PositionWrite(0L," cashier ",true))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(409);
  var id=UUID.randomUUID();doThrow(new IllegalStateException("audit unavailable")).when(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
  assertThatThrownBy(()->service.save(admin,store,id,new PositionWrite(0L,"Rollback",true))).isInstanceOf(IllegalStateException.class);assertThat(positions.find(tenant,id)).isEmpty();
 }
 @Test void appGateDenialAndUnknownFreeTextCannotBypassCatalog(){
  var c=command(0,List.of(store),company,null,null);
  var unknown=new WorkforceDtos.EmployeeWrite(0L,c.employeeNo(),c.name(),c.phone(),c.email(),"UNMANAGED",c.hireDate(),null,c.status(),c.companyId(),c.companyEffectiveDate(),c.mainStoreId(),c.storeIds(),null,null,null);
  assertThatThrownBy(()->employees.save(admin,store,UUID.randomUUID(),unknown)).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_POSITION_UNAVAILABLE");
  for(var reason:List.of(com.rpb.reservation.appgate.domain.AppGateDenyReason.APP_DISABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.TENANT_APP_NOT_ENABLED)){
   doAnswer(call->{var r=(com.rpb.reservation.appgate.application.AppGateAccessRequest)call.getArgument(0);return com.rpb.reservation.appgate.domain.AppGateDecision.deny(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission(),reason);}).when(gate).evaluate(any());
   assertThatThrownBy(()->service.list(admin,store)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
  }
 }
 @Test void concurrentCatalogEditsHaveOneWinner()throws Exception{
  var p=service.save(admin,store,UUID.randomUUID(),new PositionWrite(0L,"Concurrent",true));
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
  try{var results=new ArrayList<java.util.concurrent.Future<Integer>>();for(String name:List.of("Winner A","Winner B"))results.add(pool.submit(()->{start.await();try{service.save(admin,store,p.id(),new PositionWrite(1L,name,true));return 200;}catch(WorkforceException e){return e.status();}}));start.countDown();assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,409);assertThat(positions.find(tenant,p.id()).orElseThrow().version()).isEqualTo(2);}finally{pool.shutdownNow();}
 }
 private WorkforceDtos.EmployeeWrite choose(WorkforceDtos.EmployeeWrite c,UUID position){return new WorkforceDtos.EmployeeWrite(c.expectedVersion(),c.employeeNo(),c.name(),c.phone(),c.email(),"client text ignored",c.hireDate(),c.leaveDate(),c.status(),c.companyId(),c.hireDate(),c.mainStoreId(),c.storeIds(),c.accountId(),c.username(),c.password(),position);}
}
