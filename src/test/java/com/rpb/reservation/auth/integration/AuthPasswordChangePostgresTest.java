package com.rpb.reservation.auth.integration;

import com.rpb.reservation.auth.application.*;
import com.rpb.reservation.auth.persistence.*;
import com.rpb.reservation.auth.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;

class AuthPasswordChangePostgresTest {
 static final String SCHEMA="auth_password_test_"+UUID.randomUUID().toString().replace("-","");
 static JdbcTemplate root,jdbc;static DataSourceTransactionManager manager;
 final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
 UUID tenant,account;String token,otherToken;AuthPasswordService service;
 @BeforeAll static void database()throws Exception{
  String port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream().filter(l->l.startsWith("port=")).findFirst().orElseThrow().substring(5);
  String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";
  root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));root.execute("create schema "+SCHEMA);
  var ds=new DriverManagerDataSource(url+"&currentSchema="+SCHEMA+",public","postgres","");jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
  try(var files=Files.list(Path.of("src/main/resources/db/migration"))){for(var f:files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList())jdbc.execute(Files.readString(f));}
 }
 @AfterAll static void cleanup(){if(root!=null)root.execute("drop schema if exists "+SCHEMA+" cascade");}
 @BeforeEach void setup(){
  tenant=UUID.randomUUID();account=UUID.randomUUID();token=UUID.randomUUID().toString();otherToken=UUID.randomUUID().toString();
  jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"Test");
  insertAccount(account,tenant);session(account,tenant,token);session(account,tenant,otherToken);
  var factory=new ProxyFactory(new AuthPasswordService(new AuthRepository(jdbc),new AuthPasswordRepository(jdbc),encoder));
  factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));service=(AuthPasswordService)factory.getProxy();
 }
 void insertAccount(UUID id,UUID tenantId){jdbc.update("insert into auth_accounts(id,tenant_id,username,display_name,actor_type,status,password_hash) values(?,?,?,?,'staff','active',?)",id,tenantId,id.toString(),"Cashier",encoder.encode("old123"));}
 void session(UUID id,UUID tenantId,String value){jdbc.update("insert into auth_user_sessions(id,account_id,tenant_id,session_hash,status,expires_at) values(?,?,?,?,'active',now()+interval '1 hour')",UUID.randomUUID(),id,tenantId,AuthApplicationService.hashSessionToken(value));}
 String state(String value){return jdbc.queryForObject("select status from auth_user_sessions where session_hash=?",String.class,AuthApplicationService.hashSessionToken(value));}
 @Test void changesOnlySessionOwnerRevokesOtherSessionsAndAuditsWithoutSecrets(){
  var foreignTenant=UUID.randomUUID();jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",foreignTenant,foreignTenant.toString(),"Other");
  var foreign=UUID.randomUUID();insertAccount(foreign,foreignTenant);String foreignToken=UUID.randomUUID().toString();session(foreign,foreignTenant,foreignToken);
  service.change(token,"OLD123","New456");
  assertThat(encoder.matches("new456",jdbc.queryForObject("select password_hash from auth_accounts where id=?",String.class,account))).isTrue();
  assertThat(encoder.matches("old123",jdbc.queryForObject("select password_hash from auth_accounts where id=?",String.class,foreign))).isTrue();
  assertThat(state(token)).isEqualTo("active");assertThat(state(otherToken)).isEqualTo("revoked");assertThat(state(foreignToken)).isEqualTo("active");
  String audit=jdbc.queryForObject("select row_to_json(a)::text from audit_logs a where target_id=?",String.class,account);
  assertThat(audit).contains("auth.password.change").doesNotContain("old123","new456","password_hash",token,otherToken);
 }
 @Test void invalidOldPasswordPersistsThrottleAndDoesNotRevokeSessions(){
  for(int n=0;n<5;n++)assertThatThrownBy(()->service.change(token,"bad123","new456")).isInstanceOf(AuthApiException.class);
  assertThat(jdbc.queryForObject("select failed_login_count from auth_accounts where id=?",Integer.class,account)).isEqualTo(5);
  assertThatThrownBy(()->service.change(token,"old123","new456")).isInstanceOfSatisfying(AuthApiException.class,e->assertThat(e.code()).isEqualTo(AuthApiErrorCode.RATE_LIMITED));
  assertThat(state(otherToken)).isEqualTo("active");
 }
 @Test void deniesAnonymousExpiredDisabledAndInvalidPasswords(){
  assertThatThrownBy(()->service.change(null,"old123","new456")).isInstanceOf(AuthApiException.class);
  for(String invalid:List.of("short","toolong123","中文密码12","OLD123"))assertThatThrownBy(()->service.change(token,"old123",invalid)).isInstanceOf(AuthApiException.class);
  jdbc.update("update auth_accounts set status='disabled' where id=?",account);
  assertThatThrownBy(()->service.change(token,"old123","new456")).isInstanceOf(AuthApiException.class);
  jdbc.update("update auth_accounts set status='active' where id=?",account);jdbc.update("update auth_user_sessions set status='expired' where account_id=?",account);
  assertThatThrownBy(()->service.change(token,"old123","new456")).isInstanceOf(AuthApiException.class);
 }
 @Test void auditFailureRollsBackPasswordAndSessionChanges(){
  jdbc.execute("alter table audit_logs add constraint reject_password_audit check(operation_code <> 'auth.password.change') not valid");
  try{assertThatThrownBy(()->service.change(token,"old123","new456")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
   assertThat(encoder.matches("old123",jdbc.queryForObject("select password_hash from auth_accounts where id=?",String.class,account))).isTrue();assertThat(state(otherToken)).isEqualTo("active");
  }finally{jdbc.execute("alter table audit_logs drop constraint reject_password_audit");}
 }
 @Test void concurrentChangesUsingOldPasswordCanOnlyCommitOnce()throws Exception{
  var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try{Callable<Boolean> change=()->{start.await();try{service.change(token,"old123","new456");return true;}catch(AuthApiException e){return false;}};
   var a=pool.submit(change);var b=pool.submit(change);start.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
   assertThat(jdbc.queryForObject("select count(*) from audit_logs where target_id=?",Integer.class,account)).isEqualTo(1);
  }finally{pool.shutdownNow();}
 }
}
