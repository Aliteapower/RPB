package com.rpb.reservation.customer.application;

import static org.assertj.core.api.Assertions.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.persistence.adapter.CustomerPrivacyConsentJdbcAdapter;
import com.rpb.reservation.tenant.value.TenantId;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class CustomerPrivacyConsentPostgresTest {
 private static JdbcTemplate jdbc;
 private static DriverManagerDataSource datasource;
 private static CustomerPrivacyConsentJdbcAdapter adapter;
 private static final UUID tenant=UUID.randomUUID(),otherTenant=UUID.randomUUID(),store=UUID.randomUUID(),actor=UUID.randomUUID();
 private static final StoreScope scope=new StoreScope(new TenantId(tenant),store);
 @BeforeAll static void prepare() throws Exception {
  // Exactly the current workspace runtime; never start a different server or fall back to 5432.
  String port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream().filter(s->s.startsWith("port=")).map(s->s.substring(5).trim()).findFirst().orElseThrow();
  if(!port.matches("[0-9]{1,5}"))throw new IllegalStateException("Invalid local PostgreSQL pointer");
  String schema="customer_privacy_test_"+UUID.randomUUID().toString().replace("-","");
  String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";
  var root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));root.execute("create schema "+schema);
  datasource=new DriverManagerDataSource(url+"&currentSchema="+schema,"postgres","");jdbc=new JdbcTemplate(datasource);
  jdbc.execute("create table customers(id uuid primary key,tenant_id uuid not null,display_name text,phone_e164 text,unique(id,tenant_id));create table stores(id uuid primary key,tenant_id uuid not null,unique(id,tenant_id));create table auth_accounts(id uuid primary key,tenant_id uuid not null,username text not null,display_name text)");
  jdbc.update("insert into customers(id,tenant_id) values(?,?)",UUID.randomUUID(),tenant);
  jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V067__shared_customer_privacy_consents.sql")));
  assertThat(jdbc.queryForObject("select count(*) from customer_privacy_consents",Integer.class)).isZero();
  jdbc.update("insert into stores values(?,?)",store,tenant);jdbc.update("insert into auth_accounts values(?,?,?,?)",actor,tenant,"privacy-test","登记管理员");
  adapter=new CustomerPrivacyConsentJdbcAdapter(jdbc);
  Files.writeString(Path.of("target/customer-privacy-postgres-schema.txt"),"port="+port+"\nschema="+schema+"\n");
 }
 private UUID customer(UUID owner){UUID id=UUID.randomUUID();jdbc.update("insert into customers(id,tenant_id) values(?,?)",id,owner);return id;}
 @Test void upgradingNoticeDoesNotRewriteHistoricalVersionOrBody(){
  UUID id=customer(tenant);
  var old=new CustomerPrivacyNotice("customer-profile-v1","历史告知正文","Historical notice","历史确认","Historical confirmation");
  adapter.append(scope,id,actor,"wallet",old);
  var saved=adapter.list(scope.tenantScope(),id).getFirst();
  assertThat(CustomerPrivacyNotice.CURRENT.version()).isEqualTo("customer-profile-v2");
  assertThat(saved.noticeVersion()).isEqualTo("customer-profile-v1");assertThat(saved.noticeTextZh()).isEqualTo("历史告知正文");
  assertThat(CustomerPrivacyNotice.CURRENT.textZh()).contains("包含营销推送同意或钱包扣款授权");
 }
 @Test void persistsAcceptedActorNoticeSnapshotAndServerTime(){
  UUID customer=customer(tenant);OffsetDateTime before=OffsetDateTime.now().minusSeconds(1);adapter.append(scope,customer,actor,"wallet",CustomerPrivacyNotice.CURRENT);
  var saved=adapter.list(scope.tenantScope(),customer).getFirst();assertThat(saved.accepted()).isTrue();assertThat(saved.recordedBy()).isEqualTo(actor);assertThat(saved.recordedByName()).isEqualTo("登记管理员");assertThat(saved.recordedAt()).isAfter(before);assertThat(saved.noticeTextZh()).isEqualTo(CustomerPrivacyNotice.CURRENT.textZh());assertThat(saved.confirmationMethod()).isEqualTo("merchant_attestation");
 }
 @Test void excludesAnotherTenantAndPreventsDuplicateRegistration(){
  UUID customer=customer(tenant);adapter.append(scope,customer,actor,"tenant_admin",CustomerPrivacyNotice.CURRENT);
  assertThat(adapter.list(new com.rpb.reservation.common.scope.TenantScope(new TenantId(otherTenant)),customer)).isEmpty();assertThatThrownBy(()->adapter.append(scope,customer,actor,"wallet",CustomerPrivacyNotice.CURRENT)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(adapter.list(scope.tenantScope(),customer)).hasSize(1);
 }
 @Test void rejectsCrossTenantCustomerAndStoreReferences(){
  UUID foreign=customer(otherTenant);assertThatThrownBy(()->adapter.append(scope,foreign,actor,"wallet",CustomerPrivacyNotice.CURRENT)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  UUID otherActor=UUID.randomUUID();jdbc.update("insert into auth_accounts values(?,?,?,?)",otherActor,otherTenant,"foreign","foreign");
  assertThatThrownBy(()->adapter.append(new StoreScope(new TenantId(otherTenant),store),foreign,otherActor,"wallet",CustomerPrivacyNotice.CURRENT)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void wrongActorScopeCannotBeStoredAsAValidMerchantAttestation(){
  UUID customer=customer(tenant),otherActor=UUID.randomUUID();jdbc.update("insert into auth_accounts values(?,?,?,?)",otherActor,otherTenant,"foreign2","foreign2");
  assertThatThrownBy(()->adapter.append(scope,customer,otherActor,"wallet",CustomerPrivacyNotice.CURRENT)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(adapter.list(scope.tenantScope(),customer)).isEmpty();
 }
 @Test void failedConsentInsertRollsBackCustomerInTheSameTransaction(){
  UUID customer=UUID.randomUUID();var transaction=new TransactionTemplate(new DataSourceTransactionManager(datasource));
  assertThatThrownBy(()->transaction.execute(status->{jdbc.update("insert into customers(id,tenant_id) values(?,?)",customer,tenant);adapter.append(scope,customer,UUID.randomUUID(),"wallet",CustomerPrivacyNotice.CURRENT);return null;})).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(jdbc.queryForObject("select count(*) from customers where id=?",Integer.class,customer)).isZero();
 }
 @Test void selfRegistrationIsRecordedAsCustomerSubmissionWithoutAMerchantActor(){
  UUID customer=customer(tenant);jdbc.update("update customers set display_name=?,phone_e164=? where id=?","自行注册顾客","+6581112472",customer);
  adapter.appendSelfRegistration(scope,customer,CustomerPrivacyNotice.CURRENT);
  var record=adapter.list(scope.tenantScope(),customer).getFirst();assertThat(record.recordedBy()).isNull();assertThat(record.recordedByCustomerId()).isEqualTo(customer);assertThat(record.confirmationMethod()).isEqualTo("customer_registration_submission");assertThat(record.entrySource()).isEqualTo("customer_registration");
 }
}
