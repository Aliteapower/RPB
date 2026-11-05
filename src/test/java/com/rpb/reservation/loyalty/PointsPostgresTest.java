package com.rpb.reservation.loyalty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.loyalty.application.*;
import com.rpb.reservation.loyalty.application.PointsViews.*;
import com.rpb.reservation.loyalty.domain.*;
import com.rpb.reservation.loyalty.persistence.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
/** Real pointer runtime, private disposable schema; never writes public or validation schemas. */
class PointsPostgresTest {
 static final String SCHEMA="points_test_"+UUID.randomUUID().toString().replace("-","");
 static JdbcTemplate jdbc,root;static TransactionTemplate tx;
 PointsService service;PointsRepository repo;Binding b;UUID customer,actor;
 @BeforeAll static void database()throws Exception{
  String port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream().filter(s->s.startsWith("port=")).findFirst().orElseThrow().substring(5);
  String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";
  root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));root.execute("create schema "+SCHEMA);
  var ds=new DriverManagerDataSource(url+"&currentSchema="+SCHEMA+",public","postgres","");jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  try(var files=Files.list(Path.of("src/main/resources/db/migration"))){for(var f:files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList())jdbc.execute(Files.readString(f));}
 }
 @AfterAll static void close(){if(root!=null)root.execute("drop schema if exists "+SCHEMA+" cascade");}
 @BeforeEach void setup(){
  UUID tenant=UUID.randomUUID(),issuer=UUID.randomUUID(),store=UUID.randomUUID(),program=UUID.randomUUID();customer=UUID.randomUUID();actor=UUID.randomUUID();
  jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"积分租户");
  jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'issuer','发行主体','active')",issuer,tenant);
  jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'main','测试门店','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,issuer);
  jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,phone_e164,status) values(?,?,'customer','regular','积分顾客','+6591234567','active')",customer,tenant);
  jdbc.update("insert into wallet_programs(id,tenant_id,operating_entity_id) values(?,?,?)",program,tenant,issuer);
  jdbc.update("insert into wallet_program_stores(program_id,tenant_id,store_id,operating_entity_id) values(?,?,?,?)",program,tenant,store,issuer);
  UUID pointsProgram=UUID.randomUUID();
  jdbc.update("insert into loyalty_programs(id,tenant_id,operating_entity_id,name) values(?,?,?,'积分')",pointsProgram,tenant,issuer);
  jdbc.update("insert into wallet_loyalty_bindings(tenant_id,wallet_program_id,loyalty_program_id,operating_entity_id) values(?,?,?,?)",tenant,program,pointsProgram,issuer);
  b=new Binding(tenant,issuer,program,store,pointsProgram);repo=new PointsRepository(jdbc);service=new PointsService(repo,new PointsCommandRepository(jdbc,new ObjectMapper().findAndRegisterModules()));
 }
 Mutation m(int n,Integer version){return new Mutation(n,"原因","备注","外部参考",null,version,UUID.randomUUID().toString());}
 Result mutate(String kind,Mutation m){return tx.execute(s->service.mutate(b,actor,customer,kind,m));}
 Result grant(int n){return mutate("manual_grant",m(n,null));}
 @Test void independentLedgerHasAtomicRedemptionAndSuccessfulReplay(){
  assertThat(service.get(b,customer).id()).isNull();assertThat(service.get(b,customer).balance()).isZero();
  var req=m(100,null);var first=mutate("manual_grant",req);assertThat(mutate("manual_grant",req)).isEqualTo(first);
  assertThatThrownBy(()->mutate("manual_grant",new Mutation(101,"原因","备注","外部参考",null,null,req.idempotencyKey()))).isInstanceOf(PointsException.class);
  var r=mutate("redemption",new Mutation(30,"兑换",null,"external","咖啡",1,UUID.randomUUID().toString()));
  assertThat(r.account().balance()).isEqualTo(70);assertThat(r.redemption().redemptionDescription()).isEqualTo("咖啡");
  assertThat(service.entries(b,customer,0).getFirst().redemptionDescription()).isEqualTo("咖啡");
  assertThat(jdbc.queryForObject("select count(*) from wallet_accounts where tenant_id=?",Integer.class,b.tenantId())).isZero();
  assertThat(jdbc.queryForObject("select count(*) from wallet_entries where tenant_id=?",Integer.class,b.tenantId())).isZero();
  assertThat(jdbc.queryForObject("select count(*) from loyalty_audit where tenant_id=?",Integer.class,b.tenantId())).isEqualTo(2);
 }
 @Test void invalidCommandsRollbackAccountAndIdempotencyAndRedemption(){
  assertThatThrownBy(()->mutate("manual_deduct",m(1,0))).isInstanceOf(PointsException.class);
  assertThat(service.get(b,customer).id()).isNull();
  grant(5);
  assertThatThrownBy(()->mutate("redemption",new Mutation(1,"兑换",null,null,"",1,"bad"))).isInstanceOf(PointsException.class);
  assertThatThrownBy(()->mutate("manual_deduct",m(6,1))).isInstanceOf(PointsException.class);
  assertThatThrownBy(()->mutate("manual_deduct",m(1,0))).isInstanceOf(PointsException.class);
  assertThat(service.get(b,customer).balance()).isEqualTo(5);assertThat(service.entries(b,customer,0)).hasSize(1);
 }
 @Test void fullOriginalReversalUniqueAndGrantCannotReverseAfterConsumption(){
  var g=grant(100);var d=mutate("manual_deduct",m(30,1));
  assertThatThrownBy(()->tx.execute(s->service.reverse(b,actor,g.entry().id(),new Reverse("纠错",2,"too-low")))).isInstanceOf(PointsException.class).hasMessageContaining("不足");
  var req=new Reverse("纠错",2,"reverse");var result=tx.execute(s->service.reverse(b,actor,d.entry().id(),req));
  assertThat(result.account().balance()).isEqualTo(100);Result replay=tx.execute(s->service.reverse(b,actor,d.entry().id(),req));assertThat(replay).isEqualTo(result);
  assertThatThrownBy(()->tx.execute(s->service.reverse(b,actor,d.entry().id(),new Reverse("再次",3,"again")))).isInstanceOf(PointsException.class);
  assertThatThrownBy(()->tx.execute(s->service.reverse(b,actor,result.entry().id(),new Reverse("撤销冲正",3,"bad-reverse")))).isInstanceOf(PointsException.class);
 }
 @Test void upperLimitAndCrossTenantIssuerProgramScope(){
  var g=grant(Integer.MAX_VALUE);assertThatThrownBy(()->grant(1)).isInstanceOf(PointsException.class);
  var foreign=new Binding(UUID.randomUUID(),b.issuerId(),b.walletProgramId(),b.storeId(),b.programId());
  assertThat(service.get(foreign,customer).id()).isNull();assertThatThrownBy(()->service.customerForEntry(foreign,g.entry().id())).isInstanceOf(PointsException.class);
  assertThat(service.originalEntryStore(b,g.entry().id())).isEqualTo(b.storeId());assertThatThrownBy(()->service.originalEntryStore(foreign,g.entry().id())).isInstanceOf(PointsException.class);
  assertThat(service.get(new Binding(b.tenantId(),UUID.randomUUID(),b.walletProgramId(),b.storeId(),b.programId()),customer).id()).isNull();
  assertThat(service.get(new Binding(b.tenantId(),b.issuerId(),UUID.randomUUID(),b.storeId(),UUID.randomUUID()),customer).id()).isNull();
 }
 @Test void competingRedemptionsCannotOverdraw()throws Exception{
  grant(100);var pool=Executors.newFixedThreadPool(2);var latch=new CountDownLatch(1);
  try{
   Callable<Boolean> task=()->{latch.await();try{mutate("redemption",new Mutation(70,"兑换",null,null,"商品",1,UUID.randomUUID().toString()));return true;}catch(PointsException e){return false;}};
   var a=pool.submit(task);var c=pool.submit(task);latch.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),c.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
   assertThat(service.get(b,customer).balance()).isEqualTo(30);assertThat(service.entries(b,customer,0)).hasSize(2);
  }finally{pool.shutdownNow();}
 }
 @Test void databaseRejectsCrossIssuerProgramAndWrongReversalDirection(){
  assertThatThrownBy(()->jdbc.update("insert into wallet_loyalty_bindings(tenant_id,operating_entity_id,wallet_program_id,loyalty_program_id) values(?,?,?,?)",b.tenantId(),UUID.randomUUID(),b.walletProgramId(),b.programId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
  var g=grant(10);
  assertThatThrownBy(()->jdbc.update("insert into loyalty_entries(tenant_id,program_id,operating_entity_id,account_id,store_id,actor_id,kind,quantity,before_balance,after_balance,account_version,reason,original_entry_id) values(?,?,?,?,?,?,'reversal',10,10,20,2,'bad',?)",b.tenantId(),g.account().programId(),b.issuerId(),g.account().id(),b.storeId(),actor,g.entry().id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void adapterSeparatesPermissionsRequiresPhoneAndAllowsOnlyManagerOriginalCorrectionAfterExpiry(){
  var gate=org.mockito.Mockito.mock(com.rpb.reservation.appgate.application.AppGateService.class);
  var directory=org.mockito.Mockito.mock(com.rpb.reservation.wallet.application.WalletDirectory.class);
  var settings=org.mockito.Mockito.mock(com.rpb.reservation.wallet.marketing.WalletMarketingService.class);
  var scope=new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(b.tenantId()),b.storeId());
  org.mockito.Mockito.when(directory.store(scope)).thenReturn(new com.rpb.reservation.wallet.application.WalletViews.StoreIdentity(b.tenantId(),b.storeId(),b.issuerId(),"主体","店","tenant","Asia/Singapore","zh-CN",null));
  org.mockito.Mockito.when(directory.customer(scope.tenantScope(),customer)).thenReturn(new com.rpb.reservation.wallet.application.WalletViews.Customer(customer,"C","顾客",null,null));
  org.mockito.Mockito.when(settings.pointsEnabled(scope)).thenReturn(true);
  org.mockito.Mockito.when(gate.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(com.rpb.reservation.appgate.domain.AppGateDecision.allow("wallet",b.tenantId(),b.storeId(),"wallet.points.grant"));
  var access=new com.rpb.reservation.wallet.application.WalletAccess(gate,directory);
  var adapter=new com.rpb.reservation.wallet.integration.WalletPointsAdapter(access,directory,service,jdbc,settings);
  var staff=new com.rpb.reservation.walkin.api.CurrentActor(b.tenantId(),actor,"staff",Set.of("store_staff"),Set.of("wallet.points.grant","wallet.points.reverse"),Set.of(b.storeId()));
  assertThatThrownBy(()->tx.execute(t->adapter.mutate(scope,staff,customer,"manual_grant",m(100,null)))).isInstanceOf(com.rpb.reservation.wallet.domain.WalletException.class).hasMessageContaining("手机号");
  org.mockito.Mockito.when(directory.customer(scope.tenantScope(),customer)).thenReturn(new com.rpb.reservation.wallet.application.WalletViews.Customer(customer,"C","顾客",null,"+6591234567"));
  org.mockito.Mockito.when(settings.pointsEnabled(scope)).thenReturn(false);
  assertThatThrownBy(()->tx.execute(t->adapter.mutate(scope,staff,customer,"manual_grant",m(100,null)))).isInstanceOf(com.rpb.reservation.wallet.domain.WalletException.class).hasMessageContaining("尚未启用");
  org.mockito.Mockito.when(settings.pointsEnabled(scope)).thenReturn(true);
  for(var reason:List.of(com.rpb.reservation.appgate.domain.AppGateDenyReason.APP_DISABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.TENANT_APP_NOT_ENABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.TENANT_APP_EXPIRED,com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED)){
   org.mockito.Mockito.when(gate.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(com.rpb.reservation.appgate.domain.AppGateDecision.deny("wallet",b.tenantId(),b.storeId(),"wallet.points.grant",reason));
   assertThatThrownBy(()->tx.execute(t->adapter.mutate(scope,staff,customer,"manual_grant",m(100,null)))).isInstanceOf(com.rpb.reservation.wallet.domain.WalletException.class);
  }
  org.mockito.Mockito.when(gate.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(com.rpb.reservation.appgate.domain.AppGateDecision.allow("wallet",b.tenantId(),b.storeId(),"wallet.points.grant"));
  var g=tx.execute(t->adapter.mutate(scope,staff,customer,"manual_grant",m(100,null)));
  assertThatThrownBy(()->tx.execute(t->adapter.mutate(scope,staff,customer,"redemption",m(1,1)))).isInstanceOf(com.rpb.reservation.wallet.domain.WalletException.class);
  assertThatThrownBy(()->tx.execute(t->adapter.reverse(scope,staff,g.entry().id(),new Reverse("纠错",1,"staff-reverse")))).isInstanceOf(com.rpb.reservation.wallet.domain.WalletException.class);
  var manager=new com.rpb.reservation.walkin.api.CurrentActor(b.tenantId(),actor,"manager",Set.of("store_manager"),Set.of("wallet.points.view","wallet.points.reverse"),Set.of(b.storeId()));
  org.mockito.Mockito.when(settings.pointsEnabled(scope)).thenReturn(false);
  org.mockito.Mockito.when(gate.evaluate(org.mockito.ArgumentMatchers.any())).thenThrow(new AssertionError("expired original correction must not require new transaction commercial gate"));
  assertThat(adapter.get(scope,manager,customer).balance()).isEqualTo(100);
  var r=tx.execute(t->adapter.reverse(scope,manager,g.entry().id(),new Reverse("到期纠错",1,"manager-reverse")));
  assertThat(r.account().balance()).isZero();
  var wrongStore=new com.rpb.reservation.walkin.api.CurrentActor(b.tenantId(),actor,"manager",Set.of("store_manager"),Set.of("wallet.points.view"),Set.of());
  assertThatThrownBy(()->adapter.get(scope,wrongStore,customer)).isInstanceOf(com.rpb.reservation.wallet.domain.WalletException.class);
 }

 @Test void adapterFirstBindingPlanAndZeroAccountAllRollbackWhenGrantFails(){
  jdbc.update("delete from wallet_loyalty_bindings where tenant_id=?",b.tenantId());
  jdbc.update("delete from loyalty_programs where tenant_id=?",b.tenantId());
  var gate=org.mockito.Mockito.mock(com.rpb.reservation.appgate.application.AppGateService.class);
  var directory=org.mockito.Mockito.mock(com.rpb.reservation.wallet.application.WalletDirectory.class);
  var settings=org.mockito.Mockito.mock(com.rpb.reservation.wallet.marketing.WalletMarketingService.class);
  var scope=new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(b.tenantId()),b.storeId());
  org.mockito.Mockito.when(directory.customer(scope.tenantScope(),customer)).thenReturn(new com.rpb.reservation.wallet.application.WalletViews.Customer(customer,"C","顾客",null,"+6591234567"));
  org.mockito.Mockito.when(settings.pointsEnabled(scope)).thenReturn(true);
  org.mockito.Mockito.when(gate.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(com.rpb.reservation.appgate.domain.AppGateDecision.allow("wallet",b.tenantId(),b.storeId(),"wallet.points.grant"));
  var adapter=new com.rpb.reservation.wallet.integration.WalletPointsAdapter(new com.rpb.reservation.wallet.application.WalletAccess(gate,directory),directory,service,jdbc,settings);
  var staff=new com.rpb.reservation.walkin.api.CurrentActor(b.tenantId(),actor,"staff",Set.of("store_staff"),Set.of("wallet.points.grant"),Set.of(b.storeId()));
  assertThatThrownBy(()->tx.execute(t->adapter.mutate(scope,staff,customer,"manual_grant",m(0,null)))).isInstanceOf(PointsException.class);
  assertThat(jdbc.queryForObject("select count(*) from loyalty_programs where tenant_id=?",Integer.class,b.tenantId())).isZero();
  assertThat(jdbc.queryForObject("select count(*) from wallet_loyalty_bindings where tenant_id=?",Integer.class,b.tenantId())).isZero();
  assertThat(jdbc.queryForObject("select count(*) from loyalty_accounts where tenant_id=?",Integer.class,b.tenantId())).isZero();
  assertThat(jdbc.queryForObject("select count(*) from loyalty_commands where tenant_id=?",Integer.class,b.tenantId())).isZero();
  var result=tx.execute(t->adapter.mutate(scope,staff,customer,"manual_grant",m(100,null)));assertThat(result.account().balance()).isEqualTo(100);
 }

 @Test void customerReadUsesOnlySessionCustomerAndEnforcesTenant(){
  grant(100);UUID other=UUID.randomUUID();
  jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,phone_e164,status) values(?,?,'other','regular','另一顾客','+6591234568','active')",other,b.tenantId());
  var directory=org.mockito.Mockito.mock(com.rpb.reservation.wallet.application.WalletDirectory.class);
  var gate=org.mockito.Mockito.mock(com.rpb.reservation.appgate.application.AppGateService.class);
  var settings=org.mockito.Mockito.mock(com.rpb.reservation.wallet.marketing.WalletMarketingService.class);
  var scope=new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(b.tenantId()),b.storeId());
  var adapter=new com.rpb.reservation.wallet.integration.WalletPointsAdapter(new com.rpb.reservation.wallet.application.WalletAccess(gate,directory),directory,service,jdbc,settings);
  var own=new com.rpb.reservation.customerauth.application.CustomerAuthPrincipal(b.tenantId(),UUID.randomUUID(),customer,"own@example.com","本人");
  var otherPrincipal=new com.rpb.reservation.customerauth.application.CustomerAuthPrincipal(b.tenantId(),UUID.randomUUID(),other,"other@example.com","其他");
  assertThat(adapter.own(scope,own).balance()).isEqualTo(100);
  assertThat(adapter.own(scope,otherPrincipal).balance()).isZero();assertThat(adapter.ownEntries(scope,otherPrincipal,0)).isEmpty();
  var foreign=new com.rpb.reservation.customerauth.application.CustomerAuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),customer,"x@example.com","跨租户");
  assertThatThrownBy(()->adapter.own(scope,foreign)).isInstanceOf(com.rpb.reservation.wallet.domain.WalletException.class);
  org.mockito.Mockito.verifyNoInteractions(gate,settings);
 }

 @Test void pointsOnlyContextRemainsReadOnlyAndNeverCreatesProgramBindingsOrMoneyAccounts(){
  jdbc.update("delete from wallet_loyalty_bindings where tenant_id=?",b.tenantId());jdbc.update("delete from loyalty_programs where tenant_id=?",b.tenantId());
  var scope=new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(b.tenantId()),b.storeId());
  var gate=org.mockito.Mockito.mock(com.rpb.reservation.appgate.application.AppGateService.class);var directory=org.mockito.Mockito.mock(com.rpb.reservation.wallet.application.WalletDirectory.class);
  org.mockito.Mockito.when(directory.store(scope)).thenReturn(new com.rpb.reservation.wallet.application.WalletViews.StoreIdentity(b.tenantId(),b.storeId(),b.issuerId(),"主体","门店","T","Asia/Singapore","zh-CN",null));
  org.mockito.Mockito.when(gate.evaluateCommercial("wallet",b.tenantId(),b.storeId())).thenReturn(com.rpb.reservation.appgate.domain.AppGateDecision.allow("wallet",b.tenantId(),b.storeId(),null));
  var access=new com.rpb.reservation.wallet.application.WalletAccess(gate,directory);var accounts=new com.rpb.reservation.wallet.persistence.WalletAccountRepository(jdbc,new ObjectMapper());
  var marketing=new com.rpb.reservation.wallet.marketing.WalletMarketingService(new com.rpb.reservation.wallet.marketing.MarketingRepository(jdbc,new ObjectMapper()),accounts,access,org.mockito.Mockito.mock(com.rpb.reservation.wallet.application.WalletCollectionPort.class));
  var adapter=new com.rpb.reservation.wallet.integration.WalletPointsAdapter(access,directory,service,jdbc,marketing);
  var staff=new com.rpb.reservation.walkin.api.CurrentActor(b.tenantId(),actor,"staff",Set.of("store_staff"),Set.of("wallet.points.view"),Set.of(b.storeId()));
  var readOnly=new TransactionTemplate(tx.getTransactionManager());readOnly.setReadOnly(true);
  com.rpb.reservation.wallet.application.WalletViews.Context unconfigured=readOnly.execute(t->adapter.context(scope,staff));assertThat(unconfigured.transactionsEnabled()).isFalse();assertThat(unconfigured.denialReason()).isEqualTo("WALLET_POINTS_DISABLED");assertThat(unconfigured.program()).isNull();assertThat(unconfigured.paynowConfigured()).isFalse();
  assertThat(jdbc.queryForObject("select count(*) from wallet_marketing_settings where tenant_id=?",Integer.class,b.tenantId())).isZero();
  jdbc.update("insert into wallet_marketing_settings(tenant_id,program_id,points_enabled) values(?,?,true)",b.tenantId(),b.walletProgramId());
  com.rpb.reservation.wallet.application.WalletViews.Context enabled=readOnly.execute(t->adapter.context(scope,staff));assertThat(enabled.transactionsEnabled()).isTrue();
  jdbc.update("update wallet_program_stores set enabled=false where tenant_id=? and program_id=?",b.tenantId(),b.walletProgramId());
  com.rpb.reservation.wallet.application.WalletViews.Context disabled=readOnly.execute(t->adapter.context(scope,staff));assertThat(disabled.transactionsEnabled()).isFalse();
  org.mockito.Mockito.when(gate.evaluateCommercial("wallet",b.tenantId(),b.storeId())).thenReturn(com.rpb.reservation.appgate.domain.AppGateDecision.deny("wallet",b.tenantId(),b.storeId(),null,com.rpb.reservation.appgate.domain.AppGateDenyReason.TENANT_APP_EXPIRED));
  com.rpb.reservation.wallet.application.WalletViews.Context expired=readOnly.execute(t->adapter.context(scope,staff));assertThat(expired.denialReason()).isEqualTo("TENANT_APP_EXPIRED");
  assertThat(jdbc.queryForObject("select count(*) from wallet_loyalty_bindings where tenant_id=?",Integer.class,b.tenantId())).isZero();assertThat(jdbc.queryForObject("select count(*) from loyalty_programs where tenant_id=?",Integer.class,b.tenantId())).isZero();assertThat(jdbc.queryForObject("select count(*) from wallet_accounts where tenant_id=?",Integer.class,b.tenantId())).isZero();
 }

}
