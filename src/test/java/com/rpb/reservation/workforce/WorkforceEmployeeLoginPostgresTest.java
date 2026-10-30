package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.auth.application.*;
import com.rpb.reservation.auth.api.*;
import com.rpb.reservation.auth.persistence.AuthRepository;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkforceEmployeeLoginPostgresTest extends WorkforcePostgresTest {
 WorkforceEmployeeLoginService login;AuthApplicationService auth;AuthRepository authRepository;
 @BeforeEach void credentials(){var encoder=new BCryptPasswordEncoder(4);authRepository=new AuthRepository(jdbc);auth=proxy(new AuthApplicationService(authRepository,encoder));login=proxy(new WorkforceEmployeeLoginService(access,employees,repository,directory,new WorkforceLoginCredentials(jdbc,encoder),new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules())));}
 com.rpb.reservation.workforce.application.WorkforceDtos.Employee employee(){return employees.save(admin,store,UUID.randomUUID(),command(0,List.of(store),company,null,"clock-"+UUID.randomUUID().toString().substring(0,8)));}
 AuthLoginResult signIn(String username,String password){var challenge=auth.createSliderCaptcha("127.0.0.1","local-test").challenge();int x=authRepository.findChallenge(UUID.fromString(challenge.challengeId())).orElseThrow().targetX();return auth.login(new LoginRequest(username,password,challenge.challengeId(),x,"staff","t-"+tenant),null,"127.0.0.1","local-test");}
 @Test void createdAccountLogsInWithOnlyWorkforceAndResetRevokesSessionsAndPreservesGrants(){
  var e=employee();var first=login.get(admin,store,e.id());assertThat(first.username()).isEqualTo(e.employeeNo());var session=signIn(first.username(),"123456");
  assertThat(session.principal().permissions()).containsExactly("workforce.view");
  assertThat(session.principal().storeIds()).containsExactly(store);
  var actor=new CurrentActor(tenant,e.accountId(),"staff",Set.of("store_staff"),session.principal().permissions(),Set.of(store));access.enter(actor,store);assertThat(employees.myself(actor).id()).isEqualTo(e.id());
  var before=login.get(admin,store,e.id());var result=login.reset(admin,store,e.id(),new WorkforceEmployeeLoginService.Write(before.version(),"Abc987"));
  assertThat(jdbc.queryForObject("select status from auth_user_sessions where session_hash=?",String.class,AuthApplicationService.hashSessionToken(session.sessionToken()))).isEqualTo("revoked");
  assertThat(login.reset(admin,store,e.id(),new WorkforceEmployeeLoginService.Write(before.version(),"Abc987"))).isEqualTo(result);
  assertThatThrownBy(()->login.reset(admin,store,e.id(),new WorkforceEmployeeLoginService.Write(before.version(),"Def987"))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_CONFLICT");
  assertThatThrownBy(()->signIn(first.username(),"123456")).isInstanceOf(AuthApiException.class);
  assertThat(signIn(first.username(),"ABC987").principal().permissions()).containsExactly("workforce.view");
  assertThat(jdbc.queryForList("select role_code from auth_account_roles where account_id=?",String.class,e.accountId())).containsExactly("store_staff");
  assertThat(jdbc.queryForObject("select password_hash from auth_accounts where id=?",String.class,e.accountId())).doesNotContain("Abc987");
 }
 @Test void resetRejectsForeignEmployeePartialScopeSelfDisabledAndPrivilegedAccounts(){
  var e=employee();var info=login.get(admin,store,e.id());
  var worker=new CurrentActor(tenant,e.accountId(),"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));
  assertThatThrownBy(()->login.get(worker,store,e.id())).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->login.get(admin,sibling,e.id())).isInstanceOf(WorkforceException.class);
  var foreign=new CurrentActor(UUID.randomUUID(),admin.actorId(),"staff",admin.roles(),admin.permissions(),Set.of(store));assertThatThrownBy(()->login.reset(foreign,store,e.id(),new WorkforceEmployeeLoginService.Write(info.version(),"abc987"))).isInstanceOf(WorkforceException.class);
  jdbc.update("insert into auth_account_store_access(account_id,tenant_id,store_id) values(?,?,?)",e.accountId(),tenant,otherStore);
  var partial=new CurrentActor(tenant,admin.actorId(),"staff",admin.roles(),admin.permissions(),Set.of(store));assertThat(login.get(partial,store,e.id()).canReset()).isFalse();assertThatThrownBy(()->login.reset(partial,store,e.id(),new WorkforceEmployeeLoginService.Write(info.version(),"abc987"))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_FORBIDDEN");
  jdbc.update("update auth_accounts set status='disabled' where id=?",e.accountId());assertThat(login.get(admin,store,e.id()).canReset()).isTrue();login.reset(admin,store,e.id(),new WorkforceEmployeeLoginService.Write(info.version(),"abc987"));assertThat(login.get(admin,store,e.id()).status()).isEqualTo("disabled");assertThatThrownBy(()->signIn(info.username(),"abc987")).isInstanceOf(AuthApiException.class);
  jdbc.update("update auth_accounts set status='active' where id=?",e.accountId());jdbc.update("insert into auth_account_roles(account_id,role_code) values(?,'tenant_admin')",e.accountId());assertThat(login.get(admin,store,e.id()).canReset()).isFalse();
 }
 @Test void invalidPasswordAndAuditFailureLeaveCredentialsUnchanged(){
  var e=employee();var info=login.get(admin,store,e.id());
  assertThatThrownBy(()->login.reset(admin,store,e.id(),new WorkforceEmployeeLoginService.Write(info.version(),"bad"))).isInstanceOf(WorkforceException.class);
  doThrow(new IllegalStateException("audit unavailable")).when(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
  assertThatThrownBy(()->login.reset(admin,store,e.id(),new WorkforceEmployeeLoginService.Write(info.version(),"abc987"))).isInstanceOf(IllegalStateException.class);
  assertThat(login.get(admin,store,e.id()).version()).isEqualTo(info.version());assertThat(signIn(info.username(),"123456").principal().permissions()).containsExactly("workforce.view");
 }
 @Test void managerCanResetOrdinaryEmployeeButNotManagerOrSelfAndAppGateStillApplies(){
  var e=employee();var managerId=account("store_manager",Set.of(store),Set.of("workforce.view"));admins.save(admin,store,managerId,new com.rpb.reservation.workforce.application.WorkforceDtos.AdministratorWrite(0L,true,null));
  var manager=new CurrentActor(tenant,managerId,"staff",Set.of("store_manager"),Set.of("workforce.view"),Set.of(store));var before=login.get(manager,store,e.id());
  login.reset(manager,store,e.id(),new WorkforceEmployeeLoginService.Write(before.version(),"abc987"));
  var self=new CurrentActor(tenant,e.accountId(),"staff",admin.roles(),admin.permissions(),Set.of(store));assertThat(login.get(self,store,e.id()).canReset()).isFalse();
  jdbc.update("insert into auth_account_roles(account_id,role_code) values(?,'store_manager')",e.accountId());assertThat(login.get(manager,store,e.id()).canReset()).isFalse();assertThat(login.get(admin,store,e.id()).canReset()).isTrue();
  doReturn(com.rpb.reservation.appgate.domain.AppGateDecision.deny("workforce",tenant,store,"workforce.view",com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED)).when(gate).evaluate(any());
  assertThatThrownBy(()->login.get(admin,store,e.id())).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
  assertThatThrownBy(()->login.reset(admin,store,e.id(),new WorkforceEmployeeLoginService.Write(before.version(),"abc987"))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
 }
 @Test void resetOfLinkedExistingAccountPreservesOtherPermissionsAndAuditContainsNoSecrets(){
  UUID account=account("store_staff",Set.of(store),Set.of("wallet.view"));var e=employees.save(admin,store,UUID.randomUUID(),command(0,List.of(store),company,account,null));var info=login.get(admin,store,e.id());
  clearInvocations(logs);login.reset(admin,store,e.id(),new WorkforceEmployeeLoginService.Write(info.version(),"abc987"));
  assertThat(jdbc.queryForList("select permission_code from auth_account_permissions where account_id=?",String.class,account)).containsExactlyInAnyOrder("wallet.view","workforce.view");
  var evidence=org.mockito.ArgumentCaptor.forClass(com.rpb.reservation.audit.domain.AuditLog.class);verify(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),evidence.capture());assertThat(evidence.getValue().toString()).doesNotContain("abc987","password_hash","$2a$");
 }
 @Test void legacyDedicatedAccountCanUseEmployeeNumberWithReplayAndSessionRevocation(){
  var e=employee();jdbc.update("update auth_accounts set username='legacy-clock' where id=?",e.accountId());var session=signIn("legacy-clock","123456");var info=login.get(admin,store,e.id());assertThat(info.canUseEmployeeNumber()).isTrue();
  String hash=jdbc.queryForObject("select password_hash from auth_accounts where id=?",String.class,e.accountId());
  var command=new WorkforceEmployeeLoginService.NumberWrite(info.version());var renamed=login.useEmployeeNumber(admin,store,e.id(),command);assertThat(renamed.username()).isEqualTo(e.employeeNo());assertThat(login.useEmployeeNumber(admin,store,e.id(),command)).isEqualTo(renamed);
  assertThat(jdbc.queryForObject("select password_hash from auth_accounts where id=?",String.class,e.accountId())).isEqualTo(hash);assertThat(jdbc.queryForObject("select status from auth_user_sessions where session_hash=?",String.class,AuthApplicationService.hashSessionToken(session.sessionToken()))).isEqualTo("revoked");
  assertThatThrownBy(()->signIn("legacy-clock","123456")).isInstanceOf(AuthApiException.class);assertThat(signIn(e.employeeNo(),"123456").principal().permissions()).containsExactly("workforce.view");
 }
 @Test void employeeNumberSyncRejectsSharedAccountAndUsernameCollision(){
  var e=employee();jdbc.update("insert into auth_account_permissions(account_id,permission_code) values(?,'wallet.view')",e.accountId());var info=login.get(admin,store,e.id());assertThat(info.canUseEmployeeNumber()).isFalse();assertThatThrownBy(()->login.useEmployeeNumber(admin,store,e.id(),new WorkforceEmployeeLoginService.NumberWrite(info.version()))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
  jdbc.update("delete from auth_account_permissions where account_id=? and permission_code='wallet.view'",e.accountId());jdbc.update("update auth_accounts set username='legacy-clock' where id=?",e.accountId());UUID other=account("store_staff",Set.of(store),Set.of("workforce.view"));jdbc.update("update auth_accounts set username=? where id=?",e.employeeNo(),other);
  assertThatThrownBy(()->login.useEmployeeNumber(admin,store,e.id(),new WorkforceEmployeeLoginService.NumberWrite(info.version()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(login.get(admin,store,e.id()).username()).isEqualTo("legacy-clock");
 }
 @Test void dedicatedAccountStatusIsExplicitIdempotentAndDoesNotChangeEmployeeOrPassword(){
  var e=employee();var session=signIn(e.employeeNo(),"123456");var before=login.get(admin,store,e.id());
  String password=jdbc.queryForObject("select password_hash from auth_accounts where id=?",String.class,e.accountId());
  clearInvocations(logs);var disable=new WorkforceEmployeeLoginService.StatusWrite(before.version(),"disabled");var disabled=login.changeStatus(admin,store,e.id(),disable);
  assertThat(disabled.status()).isEqualTo("disabled");assertThat(login.changeStatus(admin,store,e.id(),disable)).isEqualTo(disabled);
  assertThat(disabled.version()).isEqualTo(before.version()+1);verify(logs,times(1)).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());
  assertThat(jdbc.queryForObject("select status from auth_user_sessions where session_hash=?",String.class,AuthApplicationService.hashSessionToken(session.sessionToken()))).isEqualTo("revoked");
  assertThatThrownBy(()->signIn(e.employeeNo(),"123456")).isInstanceOf(AuthApiException.class);
  assertThatThrownBy(()->login.changeStatus(admin,store,e.id(),new WorkforceEmployeeLoginService.StatusWrite(before.version(),"active"))).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(409);
  var current=login.get(admin,store,e.id());var enabled=login.changeStatus(admin,store,e.id(),new WorkforceEmployeeLoginService.StatusWrite(current.version(),"active"));assertThat(enabled.status()).isEqualTo("active");
  assertThat(jdbc.queryForObject("select password_hash from auth_accounts where id=?",String.class,e.accountId())).isEqualTo(password);
  assertThat(employees.get(admin,store,e.id())).isEqualTo(e);
  assertThat(signIn(e.employeeNo(),"123456").principal().permissions()).containsExactly("workforce.view");
 }
 @Test void statusRejectsSharedSelfPartialScopePrivilegedAndForeignAndAuditFailureRollsBack(){
  var e=employee();var info=login.get(admin,store,e.id());var command=new WorkforceEmployeeLoginService.StatusWrite(info.version(),"disabled");
  var self=new CurrentActor(tenant,e.accountId(),"staff",admin.roles(),admin.permissions(),Set.of(store));assertThatThrownBy(()->login.changeStatus(self,store,e.id(),command)).isInstanceOf(WorkforceException.class);
  var foreign=new CurrentActor(UUID.randomUUID(),admin.actorId(),"staff",admin.roles(),admin.permissions(),Set.of(store));assertThatThrownBy(()->login.changeStatus(foreign,store,e.id(),command)).isInstanceOf(WorkforceException.class);
  jdbc.update("insert into auth_account_permissions(account_id,permission_code) values(?,'wallet.view')",e.accountId());assertThat(login.get(admin,store,e.id()).canChangeStatus()).isFalse();assertThatThrownBy(()->login.changeStatus(admin,store,e.id(),command)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
  jdbc.update("delete from auth_account_permissions where account_id=? and permission_code='wallet.view'",e.accountId());
  var partial=new CurrentActor(tenant,admin.actorId(),"staff",admin.roles(),admin.permissions(),Set.of(store));jdbc.update("insert into auth_account_store_access(account_id,tenant_id,store_id) values(?,?,?)",e.accountId(),tenant,otherStore);assertThatThrownBy(()->login.changeStatus(partial,store,e.id(),command)).isInstanceOf(WorkforceException.class);jdbc.update("delete from auth_account_store_access where account_id=? and store_id=?",e.accountId(),otherStore);
  jdbc.update("insert into auth_account_roles(account_id,role_code) values(?,'tenant_admin')",e.accountId());assertThatThrownBy(()->login.changeStatus(admin,store,e.id(),command)).isInstanceOf(WorkforceException.class);jdbc.update("delete from auth_account_roles where account_id=? and role_code='tenant_admin'",e.accountId());
  var session=signIn(e.employeeNo(),"123456");var current=login.get(admin,store,e.id());var rollbackCommand=new WorkforceEmployeeLoginService.StatusWrite(current.version(),"disabled");
  doThrow(new IllegalStateException("audit unavailable")).when(logs).append(any(com.rpb.reservation.common.scope.StoreScope.class),any());assertThatThrownBy(()->login.changeStatus(admin,store,e.id(),rollbackCommand)).isInstanceOf(IllegalStateException.class);assertThat(login.get(admin,store,e.id())).isEqualTo(current);
  assertThat(jdbc.queryForObject("select status from auth_user_sessions where session_hash=?",String.class,AuthApplicationService.hashSessionToken(session.sessionToken()))).isEqualTo("active");
  reset(logs);doReturn(com.rpb.reservation.appgate.domain.AppGateDecision.deny("workforce",tenant,store,"workforce.view",com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED)).when(gate).evaluate(any());assertThatThrownBy(()->login.changeStatus(admin,store,e.id(),command)).isInstanceOf(WorkforceException.class).extracting("status").isEqualTo(403);
 }
}
