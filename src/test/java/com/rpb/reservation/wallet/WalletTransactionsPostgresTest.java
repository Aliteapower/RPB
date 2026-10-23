package com.rpb.reservation.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.common.scope.*;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.domain.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.funding.application.*;
import com.rpb.reservation.wallet.funding.persistence.*;
import com.rpb.reservation.wallet.marketing.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.nio.file.*;
import com.rpb.reservation.wallet.transactions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static com.rpb.reservation.wallet.application.WalletViews.*;
import static com.rpb.reservation.wallet.application.WalletCommands.*;

/** Real pointer PostgreSQL, isolated schema. No production or shared validation-schema business rows. */
class WalletTransactionsPostgresTest {
    static final String SCHEMA="wallet_transactions_test_"+UUID.randomUUID().toString().replace("-","");
    static JdbcTemplate jdbc,root; static TransactionTemplate tx;
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    UUID tenant,issuer,store,customer,actorId; StoreScope scope; CurrentActor actor; CustomerAuthPrincipal principal;
    AppGateService gate; WalletDirectory directory; WalletCollectionPort collectionPort; Clock clock;
    WalletAccountRepository accounts; WalletLedgerRepository ledger; WalletCommandRepository commands;
    WalletTopupRepository topupRepo; WalletBillRepository bills; WalletAuthorizationRepository auths; WalletRefundRepository refundRepo;
    WalletFundingService funding;WalletMarketingService marketing;
    WalletAccess access; WalletAccountService accountService; WalletTopupService topups; WalletCheckoutService checkout; WalletAuthorizationService authorization; WalletRefundService refunds;

    @BeforeAll static void database() throws Exception {
        String port=Files.readAllLines(Path.of("target/local-postgres-current.txt")).stream().filter(l->l.startsWith("port=")).findFirst().orElseThrow().substring(5);
        String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?stringtype=unspecified";
        root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres","")); root.execute("create schema "+SCHEMA);
        var ds=new DriverManagerDataSource(url+"&currentSchema="+SCHEMA+",public","postgres","");
        jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        try(var files=Files.list(Path.of("src/main/resources/db/migration"))) {
            for(var file:files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList()) jdbc.execute(Files.readString(file));
        }
    }
    @AfterAll static void close() {if(root!=null)root.execute("drop schema if exists "+SCHEMA+" cascade");}
    @BeforeEach void setup() {
        tenant=UUID.randomUUID();issuer=UUID.randomUUID();store=UUID.randomUUID();customer=UUID.randomUUID();actorId=UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,?,'active')",tenant,tenant.toString(),"钱包测试");
        jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'issuer','发行主体','active')",issuer,tenant);
        jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'main','测试门店','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",store,tenant,issuer);
        jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,phone_e164,status) values(?,?,'customer','regular','测试顾客','+6591234567','active')",customer,tenant);
        scope=new StoreScope(new TenantId(tenant),store);
        actor=new CurrentActor(tenant,actorId,"tenant_admin",Set.of("tenant_admin"),Set.of("wallet.view","wallet.topup.create","wallet.charge.create","wallet.collection.confirm","wallet.refund.manage","wallet.reconcile.view","wallet.settings.manage"),Set.of(store));
        principal=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),customer,"test@example.com","测试顾客");
        clock=Clock.fixed(Instant.parse("2026-10-22T01:00:00Z"),ZoneOffset.UTC);
        gate=mock(AppGateService.class);when(gate.evaluate(any())).thenReturn(AppGateDecision.allow("wallet",tenant,store,"wallet.view"));
        when(gate.evaluateCommercial(any(),any(),any())).thenReturn(AppGateDecision.allow("wallet",tenant,store,null));
        directory=mock(WalletDirectory.class);when(directory.store(scope)).thenReturn(new StoreIdentity(tenant,store,issuer,"发行主体","测试门店","test","Asia/Singapore","zh-CN",null));
        when(directory.customer(scope.tenantScope(),customer)).thenReturn(new Customer(customer,"customer","测试顾客","test@example.com","+6591234567"));
        collectionPort=mock(WalletCollectionPort.class);when(collectionPort.paynowConfigured(any())).thenReturn(true);
        when(collectionPort.canonicalAccount(any(),any(),any(),any())).thenAnswer(i->i.getArgument(2));
        when(collectionPort.canonicalRecoveryAccount(any(),any(),any(),any())).thenAnswer(i->i.getArgument(2));
        accounts=new WalletAccountRepository(jdbc,json); ledger=new WalletLedgerRepository(jdbc); commands=new WalletCommandRepository(jdbc,json);
        topupRepo=new WalletTopupRepository(jdbc); bills=new WalletBillRepository(jdbc);auths=new WalletAuthorizationRepository(jdbc);refundRepo=new WalletRefundRepository(jdbc);
        access=new WalletAccess(gate,directory);accountService=new WalletAccountService(accounts,ledger,commands,access,directory,collectionPort);
        funding=new WalletFundingService(new WalletFundingRepository(jdbc),accounts,ledger);marketing=new WalletMarketingService(new MarketingRepository(jdbc,json),accounts,access,collectionPort);
        var collections=new WalletCollectionService(new WalletCollectionRepository(jdbc),collectionPort);
        topups=new WalletTopupService(accounts,ledger,commands,topupRepo,collections,access,funding,marketing);
        checkout=new WalletCheckoutService(accounts,commands,bills,collections,access,auths,clock);
        authorization=new WalletAuthorizationService(accounts,ledger,commands,bills,auths,access,clock,funding);
        refunds=new WalletRefundService(accounts,ledger,commands,topupRepo,bills,refundRepo,access,funding);
        tx.execute(s->accountService.configure(scope,actor,new ProgramSettings(null,List.of("cash","paynow","credit_card"),key())));
    }
    String key(){return UUID.randomUUID().toString();}
    Account open(){return tx.execute(s->accountService.open(scope,actor,customer,key()));}
    Topup create(Account a,String amount,String channel){return tx.execute(s->topups.create(scope,actor,new TopupCreate(a.id(),amount,channel,key())));}
    CollectionConfirm confirm(String amount,String channel,String ref){return new CollectionConfirm(channel,amount,"merchant-account",ref,"T1","商户端已核实",true,key());}
    Account credit(Account a,String amount,String channel){var t=create(a,amount,channel);return tx.execute(s->topups.confirm(scope,actor,t.id(),confirm(amount,channel,key()))).account();}
    Bill bill(Account a,String amount){return tx.execute(s->checkout.create(scope,actor,new BillCreate(a.id(),amount,key())));}
    Paid pay(Bill b){var q=tx.execute(s->authorization.quote(scope,principal,b.id()));return tx.execute(s->authorization.pay(scope,principal,b.id(),new Pay(q.id(),key())));}

    TransactionService query(){return new TransactionService(new TransactionRepository(jdbc),access,Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"),ZoneOffset.UTC));}
    Map<String,String> all(){return Map.of("period","range","startDate","2020-01-01","endDate","2030-12-31");}
    com.rpb.reservation.wallet.transactions.TransactionViews.Page list(Map<String,String> q){return tx.execute(s->query().list(scope,actor,null,q));}
    @Test void businessRowsAreDeduplicatedAndSummaryIsIndependentOfCursorAndLimit() {
        var a=credit(open(),"100","cash");var b=bill(a,"25");pay(b);bill(accounts.account(scope,a.id(),false),"10");
        var q=new HashMap<>(all());q.put("limit","1");var page=list(q);
        assertThat(page.totalCount()).isEqualTo(3);assertThat(page.items()).hasSize(1);assertThat(page.summary().consumptionCents()).isEqualTo(2500);
        assertThat(page.summary().walletPaymentCents()).isEqualTo(2500);assertThat(page.summary().topupPrincipalCents()).isEqualTo(10000);
        Set<UUID> ids=new HashSet<>();ids.add(page.items().getFirst().id());
        while(page.nextCursor()!=null){q.put("cursor",page.nextCursor());page=list(q);assertThat(page.totalCount()).isEqualTo(3);assertThat(page.summary().consumptionCents()).isEqualTo(2500);for(var item:page.items())assertThat(ids.add(item.id())).isTrue();}
        assertThat(ids).hasSize(3);
        q.put("status","completed");assertThatThrownBy(()->list(q)).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_REQUEST_INVALID");
    }
    @Test void refundDoesNotMoveOriginalPaymentTimeOrTurnPaidBillIntoCancellation() {
        var a=credit(open(),"100","cash");var b=bill(a,"25");pay(b);
        var original=list(all()).items().stream().filter(i->i.id().equals(b.id())).findFirst().orElseThrow();
        var refund=tx.execute(s->refunds.create(scope,actor,new RefundCreate("bill",b.id(),"25","退回测试",key())));
        jdbc.update("update wallet_bills set updated_at='2031-02-01' where id=?",b.id());
        var page=list(all());var paid=page.items().stream().filter(i->i.id().equals(b.id())).findFirst().orElseThrow();
        assertThat(paid.status()).isEqualTo("completed");assertThat(paid.amountCents()).isEqualTo(2500);assertThat(paid.refundStatus()).isEqualTo("refunded");
        assertThat(paid.occurredAt()).isEqualTo(original.occurredAt());assertThat(page.summary().consumptionCents()).isEqualTo(2500);assertThat(page.summary().refundCents()).isEqualTo(2500);
        var detail=tx.execute(s->query().detail(scope,actor,null,"bill",b.id()));assertThat(detail.related()).extracting("sourceType").contains("refund");
        assertThat(detail.events()).extracting("type").contains("created","posted","wallet_posted");
    }
    @Test void ownershipTenantStoresAndTypePermissionsAreEnforced() {
        var a=credit(open(),"100","cash");var b=bill(a,"25");pay(b);
        var p=tx.execute(s->query().list(scope,null,principal,all()));assertThat(p.items()).allSatisfy(i->{assertThat(i.customerPhone()).isNull();assertThat(i.actorName()).isNull();});
        var another=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),UUID.randomUUID(),"other@example.invalid","other");
        assertThat(tx.execute(s->query().list(scope,null,another,all())).items()).isEmpty();
        assertThatThrownBy(()->query().detail(scope,null,another,"bill",b.id())).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_NOT_FOUND");
        for(String key:List.of("customerId","customerQuery","search")){var spoof=new HashMap<>(all());spoof.put(key,customer.toString());assertThatThrownBy(()->query().list(scope,null,principal,spoof)).isInstanceOf(WalletException.class);}
        var denied=new HashMap<>(all());denied.put("targetStoreId",UUID.randomUUID().toString());assertThatThrownBy(()->list(denied)).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_FORBIDDEN");
        var staff=new CurrentActor(tenant,actorId,"staff",Set.of("store_staff"),Set.of("wallet.view"),Set.of(store));
        var money=tx.execute(s->query().list(scope,staff,null,all()));assertThat(money.capabilities().points()).isFalse();
        var points=new HashMap<>(all());points.put("type","points_grant");assertThatThrownBy(()->query().list(scope,staff,null,points)).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_FORBIDDEN");
        var alien=new CurrentActor(UUID.randomUUID(),actorId,"staff",Set.of(),Set.of("wallet.view"),Set.of(store));assertThatThrownBy(()->query().list(scope,alien,null,all())).isInstanceOf(WalletException.class);
    }
    @Test void sourceStoreTimeZoneOwnsCrossYearDateBoundary() {
        var a=open();var t=create(a,"1","cash");jdbc.update("update wallet_topups set created_at='2026-12-31T16:00:00Z' where id=?",t.id());
        assertThat(list(Map.of("period","range","startDate","2026-12-31","endDate","2026-12-31")).items()).isEmpty();
        assertThat(list(Map.of("period","month","year","2027","month","1")).items()).extracting("id").contains(t.id());
        assertThat(list(Map.of("period","year","year","2027")).totalCount()).isEqualTo(1);
        assertThat(list(Map.of("period","today")).period().startDate()).isEqualTo(LocalDate.of(2026,10,23));
        assertThat(list(Map.of("period","month")).period().startDate()).isEqualTo(LocalDate.of(2026,10,1));
    }
    @Test void pointsIncludeBothReversalDirectionsAndOnlyWalletBoundPrograms() {
        actor=new CurrentActor(tenant,actorId,"tenant_admin",Set.of("tenant_admin"),Set.of("wallet.view","wallet.points.view","wallet.refund.manage"),Set.of(store));
        var pointsRepository=new com.rpb.reservation.loyalty.persistence.PointsRepository(jdbc);
        var pointsService=new com.rpb.reservation.loyalty.application.PointsService(pointsRepository,new com.rpb.reservation.loyalty.persistence.PointsCommandRepository(jdbc,json));
        UUID program=tx.execute(s->pointsService.createProgram(tenant,issuer));UUID walletProgram=accounts.requireProgram(scope,false).id();
        jdbc.update("insert into wallet_loyalty_bindings(tenant_id,wallet_program_id,loyalty_program_id,operating_entity_id) values(?,?,?,?)",tenant,walletProgram,program,issuer);
        var binding=new com.rpb.reservation.loyalty.application.PointsViews.Binding(tenant,issuer,walletProgram,store,program);
        var grant=tx.execute(s->pointsService.mutate(binding,actorId,customer,"manual_grant",new com.rpb.reservation.loyalty.application.PointsViews.Mutation(20,"奖励","备注","REF-POINTS",null,null,key())));
        var deduct=tx.execute(s->pointsService.mutate(binding,actorId,customer,"manual_deduct",new com.rpb.reservation.loyalty.application.PointsViews.Mutation(5,"扣减",null,null,null,1,key())));
        tx.execute(s->pointsService.reverse(binding,actorId,deduct.entry().id(),new com.rpb.reservation.loyalty.application.PointsViews.Reverse("退回扣减",2,key())));
        tx.execute(s->pointsService.reverse(binding,actorId,grant.entry().id(),new com.rpb.reservation.loyalty.application.PointsViews.Reverse("撤销奖励",3,key())));
        var page=list(all());assertThat(page.totalCount()).isEqualTo(4);assertThat(page.summary().pointsGranted()).isEqualTo(20);assertThat(page.summary().pointsDeducted()).isEqualTo(5);
        assertThat(page.summary().pointsReversed()).isEqualTo(25);assertThat(page.summary().pointsReversalIn()).isEqualTo(5);assertThat(page.summary().pointsReversalOut()).isEqualTo(20);
        assertThat(page.summary().consumptionCents()).isZero();assertThat(page.items()).allSatisfy(i->assertThat(i.amountCents()).isZero());
        var reference=new HashMap<>(all());reference.put("reference","REF-POINTS");assertThat(list(reference).items()).singleElement().extracting("id").isEqualTo(grant.entry().id());
        UUID independent=tx.execute(s->pointsService.createProgram(tenant,issuer));var otherBinding=new com.rpb.reservation.loyalty.application.PointsViews.Binding(tenant,issuer,null,store,independent);
        tx.execute(s->pointsService.mutate(otherBinding,actorId,customer,"manual_grant",new com.rpb.reservation.loyalty.application.PointsViews.Mutation(99,"其他产品积分",null,null,null,null,key())));
        assertThat(list(all()).totalCount()).isEqualTo(4);
        var onlyMoney=new CurrentActor(tenant,actorId,"staff",Set.of(),Set.of("wallet.view"),Set.of(store));assertThat(query().list(scope,onlyMoney,null,all()).items()).isEmpty();
    }
    @Test void historyDoesNotRequireCommercialEligibilityOrActiveStoreButValidatesCustomerIdentity() {
        var a=credit(open(),"10","cash");var b=bill(a,"3");pay(b);jdbc.update("update stores set status='inactive' where id=?",store);
        reset(gate);assertThat(list(all()).totalCount()).isEqualTo(2);assertThat(query().list(scope,null,principal,all()).totalCount()).isEqualTo(2);verifyNoInteractions(gate);
        when(directory.customer(scope.tenantScope(),customer)).thenThrow(WalletException.missing());assertThatThrownBy(()->query().list(scope,null,principal,all())).isInstanceOf(WalletException.class);
    }
    @Test void bonusExitIsOneSeparateRightsReversalAndTopupComponentsNeverBecomeDuplicateCash() {
        var a=open();var t=create(a,"100","cash");jdbc.update("update wallet_topups set bonus_cents=1000,deduction_policy='principal_first' where id=?",t.id());
        a=tx.execute(s->topups.confirm(scope,actor,t.id(),confirm("100","cash",key()))).account();
        var b=bill(a,"100");pay(b);var current=accounts.account(scope,a.id(),false);var lot=funding.lots(scope,current).getFirst();
        tx.execute(s->funding.exitBonus(scope,current,lot.id(),"退出剩余赠送",actorId));
        jdbc.update("update stores set currency='MYR' where id=?",store);
        var page=list(all());assertThat(page.totalCount()).isEqualTo(3);assertThat(page.summary().topupPrincipalCents()).isEqualTo(10000);assertThat(page.summary().topupBonusCents()).isEqualTo(1000);assertThat(page.summary().topupCreditCents()).isEqualTo(11000);
        assertThat(page.summary().moneyReversalCents()).isEqualTo(1000);assertThat(page.items().stream().filter(i->i.sourceType().equals("money_reversal")).findFirst().orElseThrow().walletDeltaCents()).isEqualTo(-1000);
        assertThat(page.items()).allSatisfy(i->{assertThat(i.currency()).isEqualTo("SGD");assertThat(i.customerPhone()).isEqualTo("****4567");});
    }
    @Test void mixedRefundCompletionUsesFinalPartEvidenceAndNotEarlierWalletRestoration() {
        var a=credit(open(),"10","cash");var b=bill(a,"25");pay(b);tx.execute(s->checkout.collect(scope,actor,b.id(),confirm("15","cash",key())));
        var original=list(all()).items().stream().filter(i->i.id().equals(b.id())).findFirst().orElseThrow();
        var refund=tx.execute(s->refunds.create(scope,actor,new RefundCreate("bill",b.id(),"25","混合退款",key())));
        var pending=list(all());assertThat(pending.summary().refundCents()).isZero();
        var part=refund.parts().getFirst();tx.execute(s->refunds.confirmPart(scope,actor,refund.refund().id(),part.id(),"REF-"+key(),key()));
        jdbc.update("update wallet_refund_parts set updated_at='2027-01-01T00:00:00Z' where id=?",part.id());
        var result=list(all()).items().stream().filter(i->i.id().equals(refund.refund().id())).findFirst().orElseThrow();
        assertThat(result.postedAt()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));assertThat(result.walletDeltaCents()).isEqualTo(1000);assertThat(result.externalPaidCents()).isEqualTo(1500);
        assertThat(list(all()).items().stream().filter(i->i.id().equals(b.id())).findFirst().orElseThrow().postedAt()).isEqualTo(original.postedAt());
        var detail=query().detail(scope,null,principal,"refund",result.id());assertThat(detail.events()).anySatisfy(e->{assertThat(e.type()).isEqualTo("refund_part_completed");assertThat(e.occurredAt()).isEqualTo(result.postedAt());});
    }
    @Test void personalPaymentDescriptionIsReadWithoutExposingCodeSecrets() {
        var a=credit(open(),"10","cash");var b=bill(a,"3");pay(b);
        String secret="a".repeat(64);
        jdbc.update("insert into wallet_customer_identity_codes(tenant_id,customer_id,program_id,code_hash,expires_at,payment_bill_id,payment_store_id,payment_description) values(?,?,?,?,now()+interval '1 hour',?,?,?)",tenant,customer,a.programId(),secret,b.id(),store,"午市咖啡");
        var item=list(all()).items().stream().filter(i->i.id().equals(b.id())).findFirst().orElseThrow();assertThat(item.summary()).isEqualTo("午市咖啡");assertThat(item.toString()).doesNotContain(secret);
    }
    @Test void pageAndSummaryUseOneReadOnlyRepeatableSnapshotDuringConcurrentInsert() {
        var a=open();create(a,"10","cash");
        var repo=new TransactionRepository(jdbc){
            @Override public Totals totals(String where,org.springframework.jdbc.core.namedparam.MapSqlParameterSource parameters){
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
                var result=super.totals(where,parameters);
                CompletableFuture.runAsync(()->jdbc.update("insert into wallet_topups(tenant_id,program_id,account_id,store_id,amount_cents,channel,created_by) values(?,?,?,?,100,'cash',?)",tenant,a.programId(),a.id(),store,actorId)).join();return result;
            }
        };
        var factory=new org.springframework.aop.framework.ProxyFactory(new TransactionService(repo,access,clock));factory.setProxyTargetClass(true);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(tx.getTransactionManager(),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var service=(TransactionService)factory.getProxy();var result=service.list(scope,actor,null,all());
        assertThat(result.totalCount()).isEqualTo(1);assertThat(result.items()).hasSize(1);assertThat(list(all()).totalCount()).isEqualTo(2);
    }
    @Test void identicalPostingTimesAcrossAllFiveSourcesPageWithoutGapsOrDuplicates() {
        var perms=new HashSet<>(actor.permissions());perms.add("wallet.points.view");actor=new CurrentActor(tenant,actorId,"tenant_admin",Set.of("tenant_admin"),perms,Set.of(store));
        tx.execute(s->{
            var a=accountService.open(scope,actor,customer,key());var t=topups.create(scope,actor,new TopupCreate(a.id(),"100","cash",key()));
            jdbc.update("update wallet_topups set bonus_cents=1000,deduction_policy='principal_first' where id=?",t.id());
            var credited=topups.confirm(scope,actor,t.id(),confirm("100","cash",key())).account();var b=checkout.create(scope,actor,new BillCreate(a.id(),"100",key()));
            var q=authorization.quote(scope,principal,b.id());authorization.pay(scope,principal,b.id(),new Pay(q.id(),key()));
            var current=accounts.account(scope,a.id(),false);funding.exitBonus(scope,current,funding.lots(scope,current).getFirst().id(),"赠送退出",actorId);
            refunds.create(scope,actor,new RefundCreate("bill",b.id(),"100","原单退款",key()));
            var ps=new com.rpb.reservation.loyalty.application.PointsService(new com.rpb.reservation.loyalty.persistence.PointsRepository(jdbc),new com.rpb.reservation.loyalty.persistence.PointsCommandRepository(jdbc,json));
            UUID program=ps.createProgram(tenant,issuer);jdbc.update("insert into wallet_loyalty_bindings(tenant_id,wallet_program_id,loyalty_program_id,operating_entity_id) values(?,?,?,?)",tenant,a.programId(),program,issuer);
            ps.mutate(new com.rpb.reservation.loyalty.application.PointsViews.Binding(tenant,issuer,a.programId(),store,program),actorId,customer,"manual_grant",new com.rpb.reservation.loyalty.application.PointsViews.Mutation(10,"奖励",null,null,null,null,key()));return null;
        });
        var full=list(all());assertThat(full.items()).hasSize(5);assertThat(full.items()).extracting("sourceType").containsExactly("topup","refund","points","money_reversal","bill");
        assertThat(full.items()).extracting("occurredAt").containsOnly(full.items().getFirst().occurredAt());
        var q=new HashMap<>(all());q.put("limit","2");List<UUID> seen=new ArrayList<>();String cursor=null;
        do{if(cursor!=null)q.put("cursor",cursor);var page=list(q);page.items().forEach(i->seen.add(i.id()));assertThat(page.summary()).isEqualTo(full.summary());cursor=page.nextCursor();}while(cursor!=null);
        assertThat(seen).containsExactlyElementsOf(full.items().stream().map(com.rpb.reservation.wallet.transactions.TransactionViews.Item::id).toList());
    }
    @Test void allStoresIsAuthorizedIntersectionAndDatesUseEachSourceStoreZone() {
        var a=open();var first=create(a,"1","cash");UUID other=UUID.randomUUID(),second=UUID.randomUUID();
        jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'NY','纽约店','active','America/New_York','zh-CN','DD-MM-YYYY','HH:mm','SGD')",other,tenant,issuer);
        jdbc.update("insert into wallet_program_stores(program_id,tenant_id,store_id,operating_entity_id) values(?,?,?,?)",a.programId(),tenant,other,issuer);
        jdbc.update("insert into wallet_topups(id,tenant_id,program_id,account_id,store_id,amount_cents,channel,created_by) values(?,?,?,?,?,100,'cash',?)",second,tenant,a.programId(),a.id(),other,actorId);
        jdbc.update("update wallet_topups set created_at='2027-01-01T04:00:00Z' where id in (?,?)",first.id(),second);
        var params=Map.of("period","range","startDate","2027-01-01","endDate","2027-01-01","targetStoreId","all");
        var both=new CurrentActor(tenant,actorId,"tenant_admin",Set.of("tenant_admin"),Set.of("wallet.view"),Set.of(store,other));
        var jan=query().list(scope,both,null,params);assertThat(jan.items()).extracting("id").containsExactly(first.id());assertThat(jan.stores()).hasSize(2);
        var dec=query().list(scope,both,null,Map.of("period","range","startDate","2026-12-31","endDate","2026-12-31","targetStoreId","all"));assertThat(dec.items()).extracting("id").containsExactly(second);
        assertThat(query().list(scope,actor,null,all()).stores()).extracting("id").containsExactly(store);
        assertThat(query().list(scope,null,principal,all()).stores()).extracting("id").contains(store,other);
    }
    @Test void topupRefundKeepsCashPrincipalSeparateFromRemovedGiftRights() {
        var a=open();var t=create(a,"100","cash");jdbc.update("update wallet_topups set bonus_cents=1000 where id=?",t.id());
        tx.execute(s->topups.confirm(scope,actor,t.id(),confirm("100","cash",key())));
        var refund=tx.execute(s->refunds.create(scope,actor,new RefundCreate("top_up",t.id(),"100","充值退出",key(),"remaining_lot_exit")));
        var pending=list(all()).items().stream().filter(i->i.sourceType().equals("refund")).findFirst().orElseThrow();
        assertThat(pending.walletDeltaCents()).isZero();assertThat(list(all()).summary().refundCents()).isZero();
        tx.execute(s->refunds.confirmPart(scope,actor,refund.refund().id(),refund.parts().getFirst().id(),"refund-"+key(),key()));
        var page=list(all());assertThat(page.totalCount()).isEqualTo(2);var item=page.items().stream().filter(i->i.sourceType().equals("refund")).findFirst().orElseThrow();
        assertThat(item.amountCents()).isEqualTo(10000);assertThat(item.externalPaidCents()).isEqualTo(10000);assertThat(item.walletDeltaCents()).isEqualTo(-11000);assertThat(item.bonusCents()).isEqualTo(1000);
        assertThat(page.summary().refundCents()).isEqualTo(10000);assertThat(page.summary().moneyReversalCents()).isZero();assertThat(page.summary().topupCreditCents()).isEqualTo(11000);
    }
    @Test void crossStorePointsReversalOnlyRelatesOriginalWhenItsStoreIsAuthorized() {
        UUID other=UUID.randomUUID();var program=accounts.requireProgram(scope,false).id();
        jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'branch','分店','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",other,tenant,issuer);
        jdbc.update("insert into wallet_program_stores(program_id,tenant_id,store_id,operating_entity_id) values(?,?,?,?)",program,tenant,other,issuer);
        var ps=new com.rpb.reservation.loyalty.application.PointsService(new com.rpb.reservation.loyalty.persistence.PointsRepository(jdbc),new com.rpb.reservation.loyalty.persistence.PointsCommandRepository(jdbc,json));
        UUID loyalty=tx.execute(s->ps.createProgram(tenant,issuer));jdbc.update("insert into wallet_loyalty_bindings(tenant_id,wallet_program_id,loyalty_program_id,operating_entity_id) values(?,?,?,?)",tenant,program,loyalty,issuer);
        var originalBinding=new com.rpb.reservation.loyalty.application.PointsViews.Binding(tenant,issuer,program,store,loyalty);
        var otherBinding=new com.rpb.reservation.loyalty.application.PointsViews.Binding(tenant,issuer,program,other,loyalty);
        var grant=tx.execute(s->ps.mutate(originalBinding,actorId,customer,"manual_grant",new com.rpb.reservation.loyalty.application.PointsViews.Mutation(10,"奖励",null,null,null,null,key())));
        var reversal=tx.execute(s->ps.reverse(otherBinding,actorId,grant.entry().id(),new com.rpb.reservation.loyalty.application.PointsViews.Reverse("跨店冲正",1,key())));
        var otherScope=new StoreScope(new TenantId(tenant),other);
        var both=new CurrentActor(tenant,actorId,"staff",Set.of(),Set.of("wallet.points.view"),Set.of(store,other));
        assertThat(query().detail(otherScope,both,null,"points",reversal.entry().id()).related()).extracting("id").containsExactly(grant.entry().id());
        var onlyOther=new CurrentActor(tenant,actorId,"staff",Set.of(),Set.of("wallet.points.view"),Set.of(other));
        assertThat(query().detail(otherScope,onlyOther,null,"points",reversal.entry().id()).related()).isEmpty();
        assertThat(query().detail(otherScope,null,principal,"points",reversal.entry().id()).related()).extracting("id").containsExactly(grant.entry().id());
        assertThatThrownBy(()->query().detail(scope,both,null,"points",reversal.entry().id())).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_NOT_FOUND");
    }
}
