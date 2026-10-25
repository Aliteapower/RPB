package com.rpb.reservation.wallet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.funding.application.WalletFundingService;
import com.rpb.reservation.wallet.funding.persistence.WalletFundingRepository;
import com.rpb.reservation.common.scope.*;
import com.rpb.reservation.tenant.value.TenantId;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static com.rpb.reservation.wallet.application.WalletViews.*;
class WalletComponentReportingTest {
 static final String SCHEMA="wallet_report_"+UUID.randomUUID().toString().replace("-","");
 static JdbcTemplate jdbc,root;static TransactionTemplate tx;
 StoreScope scope;UUID tenant,issuer,store,customer,actor;Program program;
 WalletAccountRepository accounts;WalletLedgerRepository ledger;WalletFundingService funding;WalletTopupRepository topups;WalletCollectionRepository collections;WalletReconciliationRepository reports;
 @BeforeAll static void database()throws Exception{
  String port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream().filter(l->l.startsWith("port=")).findFirst().orElseThrow().substring(5);
  String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));root.execute("create schema "+SCHEMA);
  var ds=new DriverManagerDataSource(url+"&currentSchema="+SCHEMA+",public","postgres","");jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  try(var files=Files.list(Path.of("src/main/resources/db/migration"))){for(var f:files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList())jdbc.execute(Files.readString(f));}
 }
 @AfterAll static void close(){if(root!=null)root.execute("drop schema if exists "+SCHEMA+" cascade");}
 @BeforeEach void setup(){
  tenant=UUID.randomUUID();issuer=UUID.randomUUID();store=UUID.randomUUID();customer=UUID.randomUUID();actor=UUID.randomUUID();
  jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"报表测试");
  jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'issuer','主体','active')",issuer,tenant);
  jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'main','测试店','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,issuer);
  jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,phone_e164,status) values(?,?,'C','regular','顾客','+6591234567','active')",customer,tenant);
  UUID pid=UUID.randomUUID();jdbc.update("insert into wallet_programs(id,tenant_id,operating_entity_id) values(?,?,?)",pid,tenant,issuer);jdbc.update("insert into wallet_program_stores(program_id,tenant_id,store_id,operating_entity_id) values(?,?,?,?)",pid,tenant,store,issuer);
  scope=new StoreScope(new TenantId(tenant),store);accounts=new WalletAccountRepository(jdbc,new ObjectMapper().findAndRegisterModules());ledger=new WalletLedgerRepository(jdbc);funding=new WalletFundingService(new WalletFundingRepository(jdbc),accounts,ledger);topups=new WalletTopupRepository(jdbc);collections=new WalletCollectionRepository(jdbc);reports=new WalletReconciliationRepository(jdbc,collections);program=accounts.requireProgram(scope,false);
 }
 Reconciliation report(){return reports.query(scope,program,Instant.EPOCH,Instant.now().plusSeconds(60));}
 @Test void businessReportUsesPostingPeriodAndHidesAbsentItemsWithNoTruncationOrCrossStoreLeak(){
  UUID id=tx.execute(t->{var a=accounts.open(scope,program,customer);var x=topups.create(scope,a,10000,"cash",actor);topups.status(scope,x.id(),"credited");
   jdbc.update("update wallet_accounts set balance_cents=10000,principal_balance_cents=10000,version=1,funding_cutover_version=1 where tenant_id=? and id=?",tenant,a.id());
   jdbc.update("insert into wallet_funding_lots(tenant_id,program_id,account_id,legacy,policy,initial_principal,initial_bonus,principal,bonus,credit_sequence) values(?,?,?,true,'principal_first',10000,0,10000,0,0)",tenant,program.id(),a.id());
   jdbc.update("insert into wallet_entries(tenant_id,program_id,account_id,store_id,actor_id,kind,source_id,amount_cents,before_cents,after_cents,account_version,created_at) values(?,?,?,?,?,'topup_credit',?,10000,0,10000,1,'2024-02-29T16:00:00Z')",tenant,program.id(),a.id(),store,actor,x.id());
   jdbc.update("insert into wallet_collections(tenant_id,store_id,topup_id,channel,amount_cents,collection_account,receipt_reference,terminal_code,confirmed_by,created_at) values(?,?,?,'cash',10000,'cash','report-test','T1',?,'2024-02-29T16:00:00Z')",tenant,store,x.id(),actor);
   return x.id();});
  jdbc.update("update wallet_topups set created_at='2024-01-01T00:00:00Z' where tenant_id=? and id=?",tenant,id);
  var r=new com.rpb.reservation.wallet.transactions.WalletBusinessReportRepository(jdbc);
  var rows=r.query(scope,Instant.parse("2024-02-29T16:00:00Z"),Instant.parse("2024-03-31T16:00:00Z"),"Asia/Singapore","day",true);
  assertThat(rows).anySatisfy(v->{assertThat(v.metric()).isEqualTo("topupPrincipal");assertThat(v.amount()).isEqualTo(10000);assertThat(v.count()).isEqualTo(1);assertThat(v.bucket()).isEqualTo("2024-03-01");});
  assertThat(rows).anySatisfy(v->{assertThat(v.metric()).isEqualTo("topupReceipt");assertThat(v.amount()).isEqualTo(10000);assertThat(v.channel()).isEqualTo("cash");});
  assertThat(rows).noneSatisfy(v->assertThat(v.metric()).isIn("consumption","pointsGrant","topupBonus"));
  assertThat(r.query(scope,Instant.parse("2024-01-01T00:00:00Z"),Instant.parse("2024-02-29T16:00:00Z"),"Asia/Singapore","day",true)).isEmpty();
  assertThat(r.query(new StoreScope(scope.tenantId(),UUID.randomUUID()),Instant.EPOCH,Instant.now(),"Asia/Singapore","month",true)).isEmpty();
  assertThat(r.query(new StoreScope(new TenantId(UUID.randomUUID()),store),Instant.EPOCH,Instant.now(),"Asia/Singapore","month",true)).isEmpty();
  jdbc.update("insert into wallet_topups(tenant_id,program_id,account_id,store_id,amount_cents,bonus_cents,channel,status,created_by,created_at,credited_at) select tenant_id,program_id,account_id,store_id,100,10,channel,'credited',created_by,'2024-03-02T00:00:00Z','2024-03-02T00:00:00Z' from wallet_topups cross join generate_series(1,1201) where id=?",id);
  jdbc.update("insert into wallet_topups(tenant_id,program_id,account_id,store_id,amount_cents,channel,status,created_by,created_at) select tenant_id,program_id,account_id,store_id,999999,channel,'pending_collection',created_by,'2024-03-02T00:00:00Z' from wallet_topups where id=?",id);
  var annual=r.query(scope,Instant.parse("2023-12-31T16:00:00Z"),Instant.parse("2024-12-31T16:00:00Z"),"Asia/Singapore","month",false);
  assertThat(annual).anySatisfy(v->{assertThat(v.metric()).isEqualTo("topupPrincipal");assertThat(v.count()).isEqualTo(1202);assertThat(v.amount()).isEqualTo(130100);assertThat(v.bucket()).isEqualTo("2024-03-01");});
  assertThat(annual).anySatisfy(v->{assertThat(v.metric()).isEqualTo("topupBonus");assertThat(v.amount()).isEqualTo(12010);});
 }
 @Test void giftComponentsNeverIncreaseCashAndExitReportsPrincipalCashAndRevokedBonusSeparately(){
  Topup topup=tx.execute(t->{var a=accounts.open(scope,program,customer);var x=topups.create(scope,a,10000,"cash",actor);jdbc.update("update wallet_topups set bonus_cents=1000 where tenant_id=? and id=?",tenant,x.id());var updated=topups.get(scope,x.id(),false);funding.credit(scope,a,updated,actor);topups.status(scope,x.id(),"credited");collections.confirm(scope,x.id(),null,"cash",10000,"cash","cash-test-"+UUID.randomUUID(),"T1","收款",actor);return updated;});
  var initial=report();assertThat(initial.topupCashCents()).isEqualTo(10000);assertThat(initial.creditedTopupCents()).isEqualTo(11000);assertThat(initial.creditedPrincipalCents()).isEqualTo(10000);assertThat(initial.creditedBonusCents()).isEqualTo(1000);
  UUID bill=UUID.randomUUID();tx.execute(t->{var a=accounts.account(scope,topup.accountId(),true);funding.debit(scope,a,5500,bill,actor);return null;});
  var spent=report();assertThat(spent.walletSpentCents()).isEqualTo(5500);assertThat(spent.spentPrincipalCents()).isEqualTo(5000);assertThat(spent.spentBonusCents()).isEqualTo(500);assertThat(spent.programPrincipalBalanceCents()).isEqualTo(5000);assertThat(spent.programBonusBalanceCents()).isEqualTo(500);
  tx.execute(t->{var a=accounts.account(scope,topup.accountId(),true);funding.refundBill(scope,a,bill,UUID.randomUUID(),1100,actor);return null;});
  var returned=report();assertThat(returned.returnedPrincipalCents()).isEqualTo(1000);assertThat(returned.returnedBonusCents()).isEqualTo(100);
  var refundRepo=new WalletRefundRepository(jdbc);
  UUID refundId=tx.execute(t->{var a=accounts.account(scope,topup.accountId(),true);var split=funding.topupRefundPreview(scope,a,topup.id(),6000);var refund=refundRepo.create(scope,a,topup.id(),null,6000,6600,"退出",actor,"pending");refundRepo.components(scope,refund.id(),6000,600);funding.reserve(scope,a,refund.id(),split);var receipt=collections.list(scope,topup.id(),null).getFirst();refundRepo.addPart(scope,refund.id(),receipt.id(),6000);return refund.id();});
  var frozen=report();assertThat(frozen.programPrincipalReservedCents()).isEqualTo(6000);assertThat(frozen.programBonusReservedCents()).isEqualTo(600);
  tx.execute(t->{var a=accounts.account(scope,topup.accountId(),true);funding.finish(scope,a,refundId,topup.id(),actor);refundRepo.completePart(scope,refundRepo.parts(scope,refundId).getFirst(),"refund-reference");refundRepo.status(scope,refundId,"completed",actor);return null;});
  var finished=report();assertThat(finished.cashPrincipalRefundCents()).isEqualTo(6000);assertThat(finished.revokedBonusCents()).isEqualTo(600);assertThat(finished.programBalanceCents()).isZero();assertThat(finished.componentMismatchAccounts()).isZero();assertThat(finished.fundingMismatchAccounts()).isZero();assertThat(finished.topupCashCents()).isEqualTo(10000);
 }
 @Test void issuerAggregationCountsSharedBalancesOnceAndPreservesCashFields(){
  var access=org.mockito.Mockito.mock(WalletAccess.class);var directory=org.mockito.Mockito.mock(WalletDirectory.class);var accountRepo=org.mockito.Mockito.mock(WalletAccountRepository.class);var reportRepo=org.mockito.Mockito.mock(WalletReconciliationRepository.class);
  UUID other=UUID.randomUUID();var second=new StoreScope(scope.tenantId(),other);org.mockito.Mockito.when(directory.store(scope)).thenReturn(new StoreIdentity(tenant,store,issuer,"主体","店","T","Asia/Singapore","zh-CN",null));org.mockito.Mockito.when(accountRepo.requireProgram(scope,false)).thenReturn(program);org.mockito.Mockito.when(accountRepo.programStores(tenant,program.id())).thenReturn(List.of(store,other));
  var one=new Reconciliation(10000,0,0,5500,0,0,0,0,5500,0,0,11000,0,0,List.of(),10000,1000,5000,500,0,0,0,0,5000,500,0,0,0,0);
  var two=new Reconciliation(0,0,0,0,0,0,0,0,5500,0,0,0,0,0,List.of(),0,0,0,0,0,0,0,0,5000,500,0,0,0,0);
  org.mockito.Mockito.when(reportRepo.query(org.mockito.ArgumentMatchers.eq(scope),org.mockito.ArgumentMatchers.eq(program),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(one);org.mockito.Mockito.when(reportRepo.query(org.mockito.ArgumentMatchers.eq(second),org.mockito.ArgumentMatchers.eq(program),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(two);
  var actorRecord=new com.rpb.reservation.walkin.api.CurrentActor(tenant,actor,"manager",Set.of("tenant_admin"),Set.of("wallet.reconcile.view"),Set.of(store,other));
  var service=new WalletReconciliationService(access,directory,accountRepo,reportRepo);var r=service.query(scope,actorRecord,LocalDate.now(),LocalDate.now(),"issuer");assertThat(r.topupCashCents()).isEqualTo(10000);assertThat(r.creditedBonusCents()).isEqualTo(1000);assertThat(r.programPrincipalBalanceCents()).isEqualTo(5000);assertThat(r.programBonusBalanceCents()).isEqualTo(500);assertThat(r.programBalanceCents()).isEqualTo(5500);
 }
 @Test void historicalEntriesWithoutFabricatedAllocationsRemainEntirelyPrincipal(){
  tx.execute(t->{
   UUID account=UUID.randomUUID();
   jdbc.update("insert into wallet_accounts(id,tenant_id,program_id,customer_id,balance_cents,principal_balance_cents,version,funding_cutover_version) values(?,?,?,?,10000,10000,1,1)",account,tenant,program.id(),customer);
   jdbc.update("insert into wallet_entries(tenant_id,program_id,account_id,store_id,actor_id,kind,source_id,amount_cents,before_cents,after_cents,account_version) values(?,?,?,?,?,'topup_credit',?,10000,0,10000,1)",tenant,program.id(),account,store,actor,UUID.randomUUID());
   jdbc.update("insert into wallet_funding_lots(tenant_id,program_id,account_id,legacy,policy,initial_principal,initial_bonus,principal,bonus,credit_sequence) values(?,?,?,true,'principal_first',10000,0,10000,0,0)",tenant,program.id(),account);
   return null;
  });
  var r=report();assertThat(r.creditedTopupCents()).isEqualTo(10000);assertThat(r.creditedPrincipalCents()).isEqualTo(10000);assertThat(r.creditedBonusCents()).isZero();assertThat(r.programPrincipalBalanceCents()).isEqualTo(10000);assertThat(r.topupCashCents()).isZero();assertThat(r.componentMismatchAccounts()).isZero();assertThat(r.fundingMismatchAccounts()).isZero();
 }

}
