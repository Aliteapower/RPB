package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.statutory.*;
import com.rpb.reservation.workforce.statutory.StatutoryPolicies.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StatutoryPolicyPostgresTest extends WorkforcePayrollPostgresTest {
 StatutoryPolicyRepository policies;StatutoryPolicyService settings;
 @BeforeEach void policySetup(){var mapper=new ObjectMapper().findAndRegisterModules();policies=new StatutoryPolicyRepository(jdbc,new WorkforceJson(mapper));settings=proxy(new StatutoryPolicyService(access,policies,new WorkforceAudit(logs,mapper),directory));}
 Write write(long version,int year,boolean enabled,StatutoryPolicyConfig config){return new Write(version,"Policy "+year,year,"SGD",LocalDate.of(year,1,1),LocalDate.of(year,12,31),enabled,config);}
 @Test void savedRevisionsAreImmutableAndRetriesAreIdempotent(){
  var c=OfficialStatutoryTemplates.forYear(2027).config();var id=UUID.randomUUID();var command=write(0,2027,true,c);var first=settings.save(admin,store,id,command);
  assertThat(settings.save(admin,store,id,command)).isEqualTo(first);assertThat(settings.versions(admin,store,id)).hasSize(1);
  var next=settings.save(admin,store,id,write(1,2027,true,c.withOwCeiling("9000")));assertThat(next.version()).isEqualTo(2);assertThat(policies.resolve(tenant,YearMonth.of(2027,5))).isEqualTo(next);
  assertThat(settings.versions(admin,store,id)).extracting(p->p.config().owCeiling()).containsExactly("9000","8000");
  assertThatThrownBy(()->settings.save(admin,store,id,write(1,2027,true,c.withOwCeiling("8500")))).isInstanceOf(WorkforceException.class);
 }
 @Test void enabledOverlapAndPartialMonthDatesAreRejectedButDraftsCanOverlap(){
  var c=OfficialStatutoryTemplates.forYear(2026).config();var id=UUID.randomUUID();settings.save(admin,store,id,write(0,2026,false,c));
  assertThatThrownBy(()->settings.save(admin,store,id,write(1,2026,true,c))).isInstanceOfSatisfying(WorkforceException.class,e->assertThat(e.code()).isEqualTo("WORKFORCE_STATUTORY_OVERLAP"));
  assertThat(policies.list(tenant).stream().filter(p->p.id().equals(id)).findFirst().orElseThrow().version()).isEqualTo(1);
  assertThatThrownBy(()->settings.save(admin,store,UUID.randomUUID(),new Write(0L,"Invalid",2027,"SGD",LocalDate.of(2027,1,2),LocalDate.of(2027,12,31),false,c))).isInstanceOf(WorkforceException.class);
 }
 @Test void onlyTenantHeadOfficeCanReadAndWriteAndAuditFailureRollsBack(){
  var managerId=account("store_manager",Set.of(store),Set.of("workforce.view"));var manager=new CurrentActor(tenant,managerId,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store));
  assertThatThrownBy(()->settings.list(manager,store)).isInstanceOf(WorkforceException.class);assertThatThrownBy(()->settings.template(worker,store,2026)).isInstanceOf(WorkforceException.class);
  var partial=new CurrentActor(tenant,adminId,"staff",admin.roles(),admin.permissions(),Set.of(store));assertThatThrownBy(()->settings.list(partial,store)).isInstanceOf(WorkforceException.class);
  var alien=new CurrentActor(UUID.randomUUID(),adminId,"staff",admin.roles(),admin.permissions(),Set.of(store));assertThatThrownBy(()->settings.list(alien,store)).isInstanceOf(WorkforceException.class);
  var id=UUID.randomUUID();when(logs.append(any(com.rpb.reservation.common.scope.StoreScope.class),any())).thenThrow(new IllegalStateException("audit"));
  assertThatThrownBy(()->settings.save(admin,store,id,write(0,2027,true,OfficialStatutoryTemplates.forYear(2027).config()))).isInstanceOf(IllegalStateException.class);assertThat(policies.list(tenant)).noneMatch(p->p.id().equals(id));assertThat(policies.versions(tenant,id)).isEmpty();
 }
 @Test void changedPolicyInvalidatesDraftButNeverRewritesFrozenPayroll(){
  completed();time.at("2026-11-02T01:00:00Z");var b=action(create(),"calculate");var p=policies.resolve(tenant,YearMonth.of(2026,10));assertThat(b.calculation().statutoryPolicy().version()).isEqualTo(1);
  settings.save(admin,store,p.id(),write(1,2026,true,p.config().withOwCeiling("9000")));var draft=b;
  assertThatThrownBy(()->action(draft,"confirm")).isInstanceOf(WorkforceException.class);b=action(b,"calculate");assertThat(b.calculation().statutoryPolicy().version()).isEqualTo(2);b=action(b,"confirm");var frozen=b.calculation();
  settings.save(admin,store,p.id(),write(2,2026,true,p.config().withOwCeiling("10000")));var detail=batches.detail(admin,store,b.batch().id());assertThat(detail.sourceChanged()).isTrue();assertThat(detail.calculation()).isEqualTo(frozen);
 }
 @Test void missingPolicyBlocksCalculationConfirmationInsteadOfUsingHardcodedFallback(){
  completed();var p=policies.resolve(tenant,YearMonth.of(2026,10));settings.save(admin,store,p.id(),write(p.version(),2026,false,p.config()));var b=action(create(),"calculate");assertThat(b.calculation().issues()).contains("STATUTORY_POLICY_MISSING");assertThat(b.calculation().statutoryPolicy()).isNull();time.at("2026-11-02T01:00:00Z");var missing=b;assertThatThrownBy(()->action(missing,"confirm")).isInstanceOf(WorkforceException.class);
 }
 @Test void replacingPolicyWithIdenticalRatesStillInvalidatesCalculatedSource(){
  completed();var b=action(create(),"calculate");var p=policies.resolve(tenant,YearMonth.of(2026,10));settings.save(admin,store,p.id(),write(1,2026,false,p.config()));settings.save(admin,store,UUID.randomUUID(),write(0,2026,true,p.config()));assertThat(batches.detail(admin,store,b.batch().id()).sourceChanged()).isTrue();time.at("2026-11-02T01:00:00Z");assertThatThrownBy(()->action(b,"confirm")).isInstanceOf(WorkforceException.class);
 }
 @Test void appGateDenialInAnyRequiredStoreBlocksGlobalPolicyWrite(){
  doAnswer(call->{com.rpb.reservation.appgate.application.AppGateAccessRequest r=call.getArgument(0);return r.storeId().equals(otherStore)?com.rpb.reservation.appgate.domain.AppGateDecision.deny(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission(),com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED):com.rpb.reservation.appgate.domain.AppGateDecision.allow(r.appKey(),r.tenantId(),r.storeId(),r.requiredPermission());}).when(gate).evaluate(any());
  var id=UUID.randomUUID();assertThatThrownBy(()->settings.save(admin,store,id,write(0,2027,true,OfficialStatutoryTemplates.forYear(2027).config()))).isInstanceOfSatisfying(WorkforceException.class,e->assertThat(e.code()).isEqualTo("STORE_APP_NOT_ENABLED"));assertThat(policies.list(tenant)).noneMatch(p->p.id().equals(id));
 }
 @Test void employeeBirthdayOverridesLegacyProfileWithoutRewritingIt(){
  completed();jdbc.update("update workforce_employees set birth_date=date '1966-10-15' where tenant_id=? and id=?",tenant,employee);
  var captured=sources.capture(tenant,company,YearMonth.of(2026,10),List.of());assertThat(captured.lines().getFirst().profile().cpf().birthDate()).isEqualTo(LocalDate.of(1966,10,15));assertThat(configs.profiles(tenant,company).getFirst().cpf().birthDate()).isEqualTo(LocalDate.of(1990,1,1));
  assertThat(new com.rpb.reservation.workforce.payroll.PayrollCalculationService(time).calculate(captured,YearMonth.of(2026,10)).lines().getFirst().employeeCpf()).isEqualTo("144.00");
 }
}

