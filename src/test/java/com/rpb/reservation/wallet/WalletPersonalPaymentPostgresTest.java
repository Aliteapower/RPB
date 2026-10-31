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
class WalletPersonalPaymentPostgresTest {
    static final String SCHEMA="wallet_personal_test_"+UUID.randomUUID().toString().replace("-","");
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
    com.rpb.reservation.wallet.integration.WalletPointsAdapter redemptionPoints;
    CurrentActor redemptionActor;
    com.rpb.reservation.wallet.redemption.RedemptionService redemptionService(){
        tx.execute(s->marketing.offers(scope,actor));jdbc.update("update wallet_marketing_settings set points_enabled=true where tenant_id=?",tenant);
        var permissions=new HashSet<>(actor.permissions());permissions.addAll(Set.of("wallet.points.view","wallet.points.redeem","wallet.points.grant"));
        redemptionActor=new CurrentActor(tenant,actorId,"tenant_admin",Set.of("tenant_admin"),permissions,Set.of(store));
        var points=new com.rpb.reservation.loyalty.application.PointsService(new com.rpb.reservation.loyalty.persistence.PointsRepository(jdbc),new com.rpb.reservation.loyalty.persistence.PointsCommandRepository(jdbc,json));
        redemptionPoints=new com.rpb.reservation.wallet.integration.WalletPointsAdapter(access,directory,points,jdbc,marketing);
        tx.execute(s->redemptionPoints.mutate(scope,redemptionActor,customer,"manual_grant",new com.rpb.reservation.loyalty.application.PointsViews.Mutation(100,"测试赠分",null,null,null,null,key())));
        return new com.rpb.reservation.wallet.redemption.RedemptionService(new com.rpb.reservation.wallet.redemption.RedemptionRepository(jdbc),accounts,commands,access,redemptionPoints,clock);
    }
    @Test void merchantPointsCodeOnlyConfirmsForOwnerAndRecordsOneRedemptionWithoutMoney(){
        var service=redemptionService();var request=tx.execute(s->service.create(scope,redemptionActor,new com.rpb.reservation.wallet.redemption.RedemptionDtos.Create(customer,20,"兑换咖啡",key())));
        var code=tx.execute(s->service.issue(scope,redemptionActor,request.id()));assertThat(code.code()).startsWith("WR.");
        var preview=service.scan(scope,principal,code.code());assertThat(preview.balance()).isEqualTo(100);assertThat(preview.status()).isEqualTo("pending");
        var other=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),UUID.randomUUID(),"other@example.invalid","other");
        assertThatThrownBy(()->service.scan(scope,other,code.code())).isInstanceOf(WalletException.class);
        var body=new com.rpb.reservation.wallet.redemption.RedemptionDtos.Confirm(preview.expectedVersion(),key());
        var result=tx.execute(s->service.confirm(scope,principal,request.id(),body));assertThat(result.status()).isEqualTo("completed");assertThat(result.balance()).isEqualTo(80);assertThat(result.entryId()).isNotNull();
        assertThat(tx.execute(s->service.confirm(scope,principal,request.id(),body)).entryId()).isEqualTo(result.entryId());
        assertThat(tx.execute(s->service.confirm(scope,principal,request.id(),new com.rpb.reservation.wallet.redemption.RedemptionDtos.Confirm(preview.expectedVersion(),key()))).entryId()).isEqualTo(result.entryId());
        assertThat(jdbc.queryForObject("select count(*) from loyalty_entries where tenant_id=? and kind='redemption'",Integer.class,tenant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from wallet_entries where tenant_id=?",Integer.class,tenant)).isZero();
        assertThat(jdbc.queryForObject("select response::text from wallet_commands where tenant_id=? and operation='points.request.create'",String.class,tenant)).doesNotContain(code.code());
    }

    @Test void redemptionRefreshExpiryCancelStaleAndDisabledLeaveBalancesIntact(){
        var service=redemptionService();var request=tx.execute(s->service.create(scope,redemptionActor,new com.rpb.reservation.wallet.redemption.RedemptionDtos.Create(customer,20,"兑换咖啡",key())));
        var first=tx.execute(s->service.issue(scope,redemptionActor,request.id()));var second=tx.execute(s->service.issue(scope,redemptionActor,request.id()));
        assertThatThrownBy(()->service.scan(scope,principal,first.code())).isInstanceOf(WalletException.class);
        var preview=service.scan(scope,principal,second.code());
        var expired=new com.rpb.reservation.wallet.redemption.RedemptionService(new com.rpb.reservation.wallet.redemption.RedemptionRepository(jdbc),accounts,commands,access,redemptionPoints,Clock.offset(clock,Duration.ofSeconds(121)));
        assertThatThrownBy(()->tx.execute(s->expired.confirm(scope,principal,request.id(),new com.rpb.reservation.wallet.redemption.RedemptionDtos.Confirm(preview.expectedVersion(),key())))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_REDEMPTION_EXPIRED");
        tx.execute(s->redemptionPoints.mutate(scope,redemptionActor,customer,"manual_grant",new com.rpb.reservation.loyalty.application.PointsViews.Mutation(1,"更新版本",null,null,null,null,key())));
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,principal,request.id(),new com.rpb.reservation.wallet.redemption.RedemptionDtos.Confirm(preview.expectedVersion(),key())))).isInstanceOf(RuntimeException.class);
        jdbc.update("update wallet_marketing_settings set points_enabled=false where tenant_id=?",tenant);
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,principal,request.id(),new com.rpb.reservation.wallet.redemption.RedemptionDtos.Confirm(preview.expectedVersion()+1,key())))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_POINTS_DISABLED");
        tx.execute(s->service.cancel(scope,redemptionActor,request.id(),new com.rpb.reservation.wallet.redemption.RedemptionDtos.Cancel(key())));
        assertThatThrownBy(()->service.scan(scope,principal,second.code())).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->tx.execute(s->service.create(scope,actor,new com.rpb.reservation.wallet.redemption.RedemptionDtos.Create(customer,20,"无权出码",key())))).isInstanceOf(WalletException.class);
        assertThat(redemptionPoints.own(scope,principal).balance()).isEqualTo(101);
        assertThat(jdbc.queryForObject("select count(*) from loyalty_entries where tenant_id=? and kind='redemption'",Integer.class,tenant)).isZero();
    }
    @Test void concurrentRedemptionConfirmationsWithDifferentKeysCreateOneEntry() throws Exception {
        var service=redemptionService();var request=tx.execute(s->service.create(scope,redemptionActor,new com.rpb.reservation.wallet.redemption.RedemptionDtos.Create(customer,20,"兑换咖啡",key())));
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<UUID> action=()->{start.await();return tx.execute(s->service.confirm(scope,principal,request.id(),new com.rpb.reservation.wallet.redemption.RedemptionDtos.Confirm(request.expectedVersion(),key()))).entryId();};
            var a=pool.submit(action);var b=pool.submit(action);start.countDown();assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
            assertThat(redemptionPoints.own(scope,principal).balance()).isEqualTo(80);
            assertThat(jdbc.queryForObject("select count(*) from loyalty_entries where tenant_id=? and kind='redemption'",Integer.class,tenant)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }

    @Test void merchantBillCodeUsesExistingCustomerConfirmationPayment(){
        var a=credit(open(),"100","cash");var b=bill(a,"12");var code=tx.execute(s->authorization.issueBillCode(scope,actor,b.id()));
        assertThat(authorization.scanBill(scope,principal,code.code()).id()).isEqualTo(b.id());assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(10000);
        pay(b);assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(8800);
    }
    @Test void redemptionCannotCrossScopeOrConfirmAfterFundsOrEntitlementChange(){
        var service=redemptionService();var request=tx.execute(s->service.create(scope,redemptionActor,new com.rpb.reservation.wallet.redemption.RedemptionDtos.Create(customer,80,"兑换",key())));
        assertThatThrownBy(()->service.own(new StoreScope(scope.tenantId(),UUID.randomUUID()),principal,request.id())).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->service.own(new StoreScope(new TenantId(UUID.randomUUID()),store),principal,request.id())).isInstanceOf(WalletException.class);
        tx.execute(s->redemptionPoints.mutate(scope,redemptionActor,customer,"redemption",new com.rpb.reservation.loyalty.application.PointsViews.Mutation(50,"其他兑换",null,null,"兑换",request.expectedVersion(),key())));
        var latest=redemptionPoints.own(scope,principal);
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,principal,request.id(),new com.rpb.reservation.wallet.redemption.RedemptionDtos.Confirm(latest.version(),key())))).isInstanceOf(RuntimeException.class);
        when(gate.evaluateCommercial(any(),any(),any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.STORE_APP_NOT_ENABLED));
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,principal,request.id(),new com.rpb.reservation.wallet.redemption.RedemptionDtos.Confirm(latest.version(),key())))).isInstanceOf(WalletException.class);
        assertThat(redemptionPoints.own(scope,principal).balance()).isEqualTo(50);
        assertThat(jdbc.queryForObject("select status from wallet_points_redemption_requests where id=?",String.class,request.id())).isEqualTo("pending");
    }

    com.rpb.reservation.wallet.merchantorder.MerchantOrderService merchantOrders(){
        redemptionService();
        var money=new com.rpb.reservation.wallet.integration.WalletMerchantMoneyAdapter(accounts,bills,commands,authorization,access);
        return new com.rpb.reservation.wallet.merchantorder.MerchantOrderService(new com.rpb.reservation.wallet.merchantorder.MerchantOrderRepository(jdbc),accounts,commands,access,redemptionPoints,money,clock);
    }
    @Test void unboundMoneyOrderCreatesOnlyOnCustomerConfirmationAndPaysExistingLedger(){
        var service=merchantOrders();var a=credit(open(),"100","cash");
        var order=tx.execute(s->service.create(scope,redemptionActor,new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Create("money","12",null,"消费",key())));
        var code=tx.execute(s->service.issue(scope,redemptionActor,order.id()));var preview=service.scan(scope,principal,code.code());
        assertThat(preview.customerId()).isNull();assertThat(bills.list(scope)).isEmpty();assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(10000);
        var body=new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Confirm(preview.expectedVersion(),key());
        var paid=tx.execute(s->service.confirm(scope,principal,order.id(),body));assertThat(paid.status()).isEqualTo("completed");assertThat(paid.customerId()).isEqualTo(customer);assertThat(paid.sourceType()).isEqualTo("bill");assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(8800);
        assertThat(tx.execute(s->service.confirm(scope,principal,order.id(),body)).sourceId()).isEqualTo(paid.sourceId());assertThat(bills.list(scope)).hasSize(1);
    }

    CustomerAuthPrincipal anotherCustomer(){
        UUID id=UUID.randomUUID();jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,phone_e164,status) values(?,?,'other','regular','另一顾客','+6598887777','active')",id,tenant);
        when(directory.customer(scope.tenantScope(),id)).thenReturn(new Customer(id,"other","另一顾客",null,"+6598887777"));return new CustomerAuthPrincipal(tenant,UUID.randomUUID(),id,"other@example.invalid","另一顾客");
    }
    @Test void unboundOrderDoesNotBindOnReadOrFailedPaymentAndAnotherCustomerCanPay(){
        var service=merchantOrders();var other=anotherCustomer();var a=tx.execute(s->accountService.open(scope,actor,other.customerId(),key()));credit(a,"50","cash");
        var o=tx.execute(s->service.create(scope,redemptionActor,new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Create("money","20",null,"消费",key())));var code=tx.execute(s->service.issue(scope,redemptionActor,o.id()));
        assertThat(service.scan(scope,principal,code.code()).customerId()).isNull();assertThat(service.scan(scope,other,code.code()).customerId()).isNull();
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,principal,o.id(),new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Confirm(0,key())))).isInstanceOf(WalletException.class);
        assertThat(service.get(scope,redemptionActor,o.id()).customerId()).isNull();assertThat(bills.list(scope)).isEmpty();
        var preview=service.own(scope,other,o.id());var paid=tx.execute(s->service.confirm(scope,other,o.id(),new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Confirm(preview.expectedVersion(),key())));assertThat(paid.customerId()).isEqualTo(other.customerId());
        assertThatThrownBy(()->service.own(scope,principal,o.id())).isInstanceOf(WalletException.class);assertThat(bills.list(scope)).hasSize(1);
    }
    @Test void unboundMultiCustomerConcurrentMoneyAndPointsConfirmOnlyOnePayer() throws Exception {
        var service=merchantOrders();var other=anotherCustomer();credit(open(),"100","cash");var a=tx.execute(s->accountService.open(scope,actor,other.customerId(),key()));credit(a,"100","cash");
        tx.execute(s->redemptionPoints.mutate(scope,redemptionActor,other.customerId(),"manual_grant",new com.rpb.reservation.loyalty.application.PointsViews.Mutation(100,"测试",null,null,null,null,key())));
        for(String kind:List.of("money","points")){
            var order=tx.execute(s->service.create(scope,redemptionActor,new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Create(kind,kind.equals("money")?"20":null,kind.equals("points")?20:null,"兑换",key())));
            var p1=service.own(scope,principal,order.id());var p2=service.own(scope,other,order.id());var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
            try{
                var f1=pool.submit(()->{start.await();try{return tx.execute(s->service.confirm(scope,principal,order.id(),new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Confirm(p1.expectedVersion(),key()))).status();}catch(WalletException e){return "lost";}});
                var f2=pool.submit(()->{start.await();try{return tx.execute(s->service.confirm(scope,other,order.id(),new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Confirm(p2.expectedVersion(),key()))).status();}catch(WalletException e){return "lost";}});
                start.countDown();assertThat(List.of(f1.get(15,TimeUnit.SECONDS),f2.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("completed","lost");
            }finally{pool.shutdownNow();}
        }
        assertThat(jdbc.queryForObject("select count(*) from wallet_bills where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sum(balance_cents) from wallet_accounts where tenant_id=?",Long.class,tenant)).isEqualTo(18000);
        assertThat(jdbc.queryForObject("select sum(balance) from loyalty_accounts where tenant_id=?",Long.class,tenant)).isEqualTo(180);
        assertThat(jdbc.queryForObject("select count(*) from loyalty_entries where tenant_id=? and kind='redemption'",Integer.class,tenant)).isEqualTo(1);
    }
    @Test void unboundOrderRefreshExpiryCancelVersionAndScopesCannotProduceBusinessRecords(){
        var service=merchantOrders();var a=credit(open(),"10","cash");
        var o=tx.execute(s->service.create(scope,redemptionActor,new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Create("money","20",null,"余额不足",key())));var first=tx.execute(s->service.issue(scope,redemptionActor,o.id()));var second=tx.execute(s->service.issue(scope,redemptionActor,o.id()));
        assertThatThrownBy(()->service.scan(scope,principal,first.code())).isInstanceOf(WalletException.class);
        var preview=service.scan(scope,principal,second.code());
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,principal,o.id(),new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Confirm(preview.expectedVersion(),key())))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_INSUFFICIENT_FUNDS");
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,principal,o.id(),new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Confirm(preview.expectedVersion()+1,key())))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_QUOTE_STALE");
        var later=new com.rpb.reservation.wallet.merchantorder.MerchantOrderService(new com.rpb.reservation.wallet.merchantorder.MerchantOrderRepository(jdbc),accounts,commands,access,redemptionPoints,new com.rpb.reservation.wallet.integration.WalletMerchantMoneyAdapter(accounts,bills,commands,authorization,access),Clock.offset(clock,Duration.ofSeconds(121)));
        assertThatThrownBy(()->tx.execute(s->later.confirm(scope,principal,o.id(),new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Confirm(preview.expectedVersion(),key())))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_MERCHANT_ORDER_EXPIRED");
        tx.execute(s->service.cancel(scope,redemptionActor,o.id(),new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Cancel(key())));
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,principal,o.id(),new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Confirm(preview.expectedVersion(),key())))).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->service.own(new StoreScope(new TenantId(UUID.randomUUID()),store),principal,o.id())).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->service.get(new StoreScope(scope.tenantId(),UUID.randomUUID()),actor,o.id())).isInstanceOf(WalletException.class);
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(1000);assertThat(bills.list(scope)).isEmpty();assertThat(service.get(scope,redemptionActor,o.id()).customerId()).isNull();
    }

    @Test void unboundOrderChecksFrozenAccountEntitlementPermissionsAndPointsSwitch(){
        var service=merchantOrders();var a=credit(open(),"100","cash");var o=tx.execute(s->service.create(scope,redemptionActor,new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Create("money","20",null,"冻结",key())));var preview=service.own(scope,principal,o.id());
        jdbc.update("update wallet_accounts set status='frozen' where id=?",a.id());
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,principal,o.id(),new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Confirm(preview.expectedVersion(),key())))).isInstanceOf(WalletException.class);
        jdbc.update("update wallet_marketing_settings set points_enabled=false where tenant_id=?",tenant);
        assertThatThrownBy(()->tx.execute(s->service.create(scope,redemptionActor,new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Create("points",null,5,"兑换",key())))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_POINTS_DISABLED");
        var denied=new CurrentActor(tenant,actorId,"staff",Set.of(),Set.of("wallet.view"),Set.of(store));assertThatThrownBy(()->tx.execute(s->service.create(scope,denied,new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Create("money","1",null,"",key())))).isInstanceOf(WalletException.class);
        when(gate.evaluateCommercial(any(),any(),any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.STORE_APP_NOT_ENABLED));assertThatThrownBy(()->tx.execute(s->service.confirm(scope,principal,o.id(),new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Confirm(preview.expectedVersion(),key())))).isInstanceOf(WalletException.class);
        assertThat(service.get(scope,redemptionActor,o.id()).customerId()).isNull();assertThat(bills.list(scope)).isEmpty();
    }
    @Test void unboundExpiryAfterAccountWorkRollsBackBillLedgerAndPayer(){
        merchantOrders();var a=credit(open(),"100","cash");var now=new java.util.concurrent.atomic.AtomicReference<>(clock.instant());var changing=mock(Clock.class);when(changing.instant()).thenAnswer(i->now.get());
        var adapter=spy(new com.rpb.reservation.wallet.integration.WalletMerchantMoneyAdapter(accounts,bills,commands,authorization,access));
        doAnswer(i->{var result=i.callRealMethod();now.set(clock.instant().plusSeconds(121));return result;}).when(adapter).pay(any(),any(),any(),any(),anyLong(),anyInt());
        var service=new com.rpb.reservation.wallet.merchantorder.MerchantOrderService(new com.rpb.reservation.wallet.merchantorder.MerchantOrderRepository(jdbc),accounts,commands,access,redemptionPoints,adapter,changing);
        var o=tx.execute(s->service.create(scope,redemptionActor,new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Create("money","20",null,"过期回滚",key())));var preview=service.own(scope,principal,o.id());
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,principal,o.id(),new com.rpb.reservation.wallet.merchantorder.MerchantOrderDtos.Confirm(preview.expectedVersion(),key())))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_MERCHANT_ORDER_EXPIRED");
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(10000);assertThat(bills.list(scope)).isEmpty();assertThat(service.get(scope,redemptionActor,o.id()).customerId()).isNull();
    }
    Account open(){return tx.execute(s->accountService.open(scope,actor,customer,key()));}
    Topup create(Account a,String amount,String channel){return tx.execute(s->topups.create(scope,actor,new TopupCreate(a.id(),amount,channel,key())));}
    CollectionConfirm confirm(String amount,String channel,String ref){return new CollectionConfirm(channel,amount,"merchant-account",ref,"T1","商户端已核实",true,key());}
    Account credit(Account a,String amount,String channel){var t=create(a,amount,channel);return tx.execute(s->topups.confirm(scope,actor,t.id(),confirm(amount,channel,key()))).account();}
    Bill bill(Account a,String amount){return tx.execute(s->checkout.create(scope,actor,new BillCreate(a.id(),amount,key())));}
    Paid pay(Bill b){var q=tx.execute(s->authorization.quote(scope,principal,b.id()));return tx.execute(s->authorization.pay(scope,principal,b.id(),new Pay(q.id(),key())));}

    WalletScanChargeService scanner(Clock time) {return new WalletScanChargeService(accounts,ledger,commands,bills,auths,access,directory,time,funding);}
    PaymentCode chargeCode(Account a) {return tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id(),"merchant_charge"));}
    ScanCharge charge(Account a,String amount,String code) {return new ScanCharge(a.id(),amount,code,a.version(),null,null,key());}
    void noScanEffects(Account a,long balance,int billCount) {
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(balance);
        assertThat(bills.list(scope)).hasSize(billCount);
        assertThat(jdbc.queryForObject("select count(*) from wallet_commands where tenant_id=? and operation='bill.scan_charge'",Integer.class,tenant)).isZero();
    }


    com.rpb.reservation.wallet.personalpayment.WalletPersonalPaymentService personal() {
        return new com.rpb.reservation.wallet.personalpayment.WalletPersonalPaymentService(accounts,bills,commands,
          new com.rpb.reservation.wallet.personalpayment.WalletPersonalPaymentRepository(jdbc),
          new WalletCustomerIdentityRepository(jdbc),access,directory,funding,clock);
    }
    void consent(long limit) {
        var p=personal();
        tx.execute(s->p.consent(scope,principal,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ConsentUpdate(0,limit,true,key())));
    }
    @Test void customerAuthorizesOnceWithoutAdminAndNewCodesKeepPaying() {
        consent(2000);var p=personal();var a=credit(open(),"100","cash");
        assertThat(p.consent(scope,principal).limitCents()).isEqualTo(2000);
        for(int i=0;i<3;i++){
            var code=tx.execute(s->p.issue(scope,principal));assertThat(code.paymentEnabled()).isTrue();
            assertThat(tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"10",null,key()))).status()).isEqualTo("paid");
        }
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(7000);
        assertThat(jdbc.queryForObject("select count(*) from wallet_personal_payment_policies where tenant_id=?",Integer.class,tenant)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from wallet_audit where tenant_id=? and action='personal.consent'",Integer.class,tenant)).isEqualTo(1);
        assertThat(p.consent(scope,principal).policyVersion()).isEqualTo(1);
    }
    @Test void identityNeedsNoAccountOrConsentAndPolicyDefaultsClosed() {
        var p=personal();assertThat(p.consent(scope,principal).configured()).isFalse();
        var code=tx.execute(s->p.issue(scope,principal));assertThat(code.paymentEnabled()).isFalse();assertThat(code.type()).isEqualTo("customer_personal");
        assertThat(jdbc.queryForObject("select count(*) from wallet_accounts where tenant_id=?",Integer.class,tenant)).isZero();
    }
    @Test void personalCodeLives120SecondsAndRepeatedIdentityReadsNeverConsumeIt() {
        var p=personal();var code=tx.execute(s->p.issue(scope,principal));
        assertThat(Duration.between(clock.instant(),code.expiresAt())).isEqualTo(Duration.ofSeconds(120));
        var codes=new WalletCustomerIdentityRepository(jdbc);var program=accounts.requireProgram(scope,true).id();
        assertThat(codes.resolve(scope,program,WalletTokens.hash(code.code()),clock.instant().plusSeconds(119))).isEqualTo(customer);
        assertThat(codes.resolve(scope,program,WalletTokens.hash(code.code()),clock.instant().plusSeconds(119))).isEqualTo(customer);
        assertThatThrownBy(()->codes.resolve(scope,program,WalletTokens.hash(code.code()),clock.instant().plusSeconds(120)))
          .isInstanceOf(WalletException.class).extracting(e->((WalletException)e).code()).isEqualTo("WALLET_PERSONAL_CODE_EXPIRED");
        var fresh=tx.execute(s->p.issue(scope,principal));
        assertThatThrownBy(()->codes.resolve(scope,program,WalletTokens.hash(code.code()),clock.instant()))
          .isInstanceOf(WalletException.class).extracting(e->((WalletException)e).code()).isEqualTo("WALLET_PERSONAL_CODE_EXPIRED");
        assertThat(codes.resolve(scope,program,WalletTokens.hash(fresh.code()),clock.instant())).isEqualTo(customer);
        var other=new StoreScope(new TenantId(UUID.randomUUID()),store);
        assertThatThrownBy(()->codes.resolve(other,program,WalletTokens.hash(fresh.code()),clock.instant()))
          .isInstanceOf(WalletException.class).extracting(e->((WalletException)e).code()).isEqualTo("WALLET_NOT_FOUND");
    }
    @Test void oneCodePaysOnceWithExactRetryAndHashOnlyPersistence() {
        consent(5000);var a=credit(open(),"100","cash");var p=personal();var code=tx.execute(s->p.issue(scope,principal));
        var identity=new WalletCustomerIdentityService(accounts,auths,new WalletCustomerIdentityRepository(jdbc),access,directory,clock);
        assertThat(identity.resolve(scope,actor,code.code()).id()).isEqualTo(customer);assertThat(identity.resolve(scope,actor,code.code()).id()).isEqualTo(customer);
        var c=new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"12.34","coffee",key());
        var paid=tx.execute(s->p.charge(scope,actor,c));assertThat(paid.status()).isEqualTo("paid");assertThat(paid.account().balanceCents()).isEqualTo(8766);
        var retried=tx.execute(s->p.charge(scope,actor,c));assertThat(retried).isEqualTo(paid);assertThat(p.result(scope,actor,c.idempotencyKey())).isEqualTo(paid);
        assertThat(p.status(scope,principal,code.code()).state()).isEqualTo("used");
        assertThatThrownBy(()->tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"1",null,key())))).isInstanceOf(WalletException.class);
        assertThat(jdbc.queryForObject("select response::text from wallet_commands where tenant_id=? and operation='bill.personal_scan_charge'",String.class,tenant)).doesNotContain(code.code());
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(8766);
        assertThat(jdbc.queryForObject("select count(*) from wallet_audit where tenant_id=? and action='personal.policy'",Integer.class,tenant)).isZero();
        assertThat(jdbc.queryForObject("select details->>'consentedLimitCents' from wallet_audit where tenant_id=? and action='personal.consent'",String.class,tenant)).isEqualTo("5000");
        assertThat(jdbc.queryForObject("select details->>'effectiveCapCents' from wallet_audit where tenant_id=? and action='bill.personal_scan_charge'",String.class,tenant)).isEqualTo("5000");
    }
    @Test void overLimitBindsOnlyOnePendingBillAndExplicitPaymentIsFull() {
        consent(1000);var a=credit(open(),"100","cash");var p=personal();var code=tx.execute(s->p.issue(scope,principal));
        var result=tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"20",null,key())));
        assertThat(result.status()).isEqualTo("confirmation_required");assertThat(result.account().balanceCents()).isEqualTo(10000);
        assertThat(p.status(scope,principal,code.code()).state()).isEqualTo("confirmation_required");
        assertThat(pay(result.bill()).account().balanceCents()).isEqualTo(8000);assertThat(p.status(scope,principal,code.code()).state()).isEqualTo("used");
        assertThat(bills.list(scope)).hasSize(1);
    }
    @Test void insufficientFundsAndRefreshedCodeProduceNoBillOrDebit() {
        consent(5000);var a=credit(open(),"1","cash");var p=personal();var old=tx.execute(s->p.issue(scope,principal));var fresh=tx.execute(s->p.issue(scope,principal));
        assertThatThrownBy(()->tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(old.code(),"1",null,key())))).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(fresh.code(),"2",null,key())))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_INSUFFICIENT_FUNDS");
        assertThat(bills.list(scope)).isEmpty();assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(100);
    }
    @Test void customerIncreaseInvalidatesOldCodeAndDoesNotRequireAdmin() {
        consent(1000);credit(open(),"100","cash");var p=personal();var code=tx.execute(s->p.issue(scope,principal));
        tx.execute(s->p.consent(scope,principal,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ConsentUpdate(1,3000L,true,key())));
        assertThat(p.consent(scope,principal).paymentEnabled()).isTrue();
        assertThatThrownBy(()->tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"20",null,key())))).isInstanceOf(WalletException.class);
        var fresh=tx.execute(s->p.issue(scope,principal));
        assertThat(tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(fresh.code(),"20",null,key()))).status()).isEqualTo("paid");
    }
    @Test void staleUpdatesAndInvalidLimitsCannotOverridePersonalAuthorization() {
        consent(5000);var p=personal();
        tx.execute(s->p.consent(scope,principal,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ConsentUpdate(1,2000L,true,key())));
        assertThatThrownBy(()->tx.execute(s->p.consent(scope,principal,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ConsentUpdate(1,9000L,true,key())))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_POLICY_STALE");
        for(Long limit:Arrays.asList(null,0L,-1L,10000000000000L))assertThatThrownBy(()->tx.execute(s->p.consent(scope,principal,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ConsentUpdate(2,limit,true,key())))).isInstanceOf(WalletException.class);
        assertThat(p.consent(scope,principal).limitCents()).isEqualTo(2000);assertThat(p.consent(scope,principal).policyVersion()).isEqualTo(2);
        var command=new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ConsentUpdate(2,3000L,true,key());
        var first=tx.execute(s->p.consent(scope,principal,command));var retried=tx.execute(s->p.consent(scope,principal,command));assertThat(retried).isEqualTo(first);assertThat(first.policyVersion()).isEqualTo(3);
    }
    @Test void customerLimitsAreIndependentAndIgnoreHistoricalAdminPolicy() {
        consent(5000);var p=personal();var program=accounts.requireProgram(scope,false).id();
        jdbc.update("insert into wallet_personal_payment_policies(tenant_id,program_id,limit_cents,version) values(?,?,100,0)",tenant,program);
        assertThat(p.consent(scope,principal).limitCents()).isEqualTo(5000);assertThat(p.consent(scope,principal).paymentEnabled()).isTrue();
        var other=UUID.randomUUID();jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,phone_e164,status) values(?,?,'other','regular','其他顾客','+6597654321','active')",other,tenant);
        var stranger=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),other,"other@test.invalid","other");
        assertThat(p.consent(scope,stranger).paymentEnabled()).isFalse();
        tx.execute(s->p.consent(scope,stranger,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ConsentUpdate(0,1000L,true,key())));
        assertThat(p.consent(scope,stranger).limitCents()).isEqualTo(1000);assertThat(p.consent(scope,principal).limitCents()).isEqualTo(5000);
    }
    @Test void loweringThenRaisingDoesNotReviveEarlierPaymentSnapshot() {
        consent(5000);credit(open(),"100","cash");var p=personal();var old=tx.execute(s->p.issue(scope,principal));
        tx.execute(s->p.consent(scope,principal,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ConsentUpdate(1,1000L,true,key())));
        var lower=tx.execute(s->p.issue(scope,principal));
        tx.execute(s->p.consent(scope,principal,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ConsentUpdate(2,5000L,true,key())));
        for(var code:List.of(old,lower))assertThatThrownBy(()->tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"5",null,key())))).isInstanceOf(WalletException.class);
        assertThat(bills.list(scope)).isEmpty();assertThat(p.consent(scope,principal).paymentEnabled()).isTrue();
    }
    @Test void concurrentLimitChangesHaveOneWinnerWithoutSilentOverwrite() throws Exception {
        consent(1000);var p=personal();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<String> update=()->{start.await();try{tx.execute(s->p.consent(scope,principal,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ConsentUpdate(1,2000L,true,key())));return "saved";}catch(WalletException e){return e.code();}};
            var one=pool.submit(update);var two=pool.submit(update);start.countDown();assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("saved","WALLET_POLICY_STALE");assertThat(p.consent(scope,principal).policyVersion()).isEqualTo(2);
        }finally{pool.shutdownNow();}
    }
    @Test void revocationDisablesOutstandingCodeEvenAfterReconsent() {
        consent(5000);credit(open(),"100","cash");var p=personal();var code=tx.execute(s->p.issue(scope,principal));
        tx.execute(s->p.consent(scope,principal,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ConsentUpdate(1,5000L,false,key())));
        tx.execute(s->p.consent(scope,principal,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ConsentUpdate(2,5000L,true,key())));
        assertThatThrownBy(()->tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"1",null,key())))).isInstanceOf(WalletException.class);
        assertThat(bills.list(scope)).isEmpty();
    }
    @Test void concurrentDifferentRequestsCannotSpendOneAuthorizationTwice() throws Exception {
        consent(5000);var a=credit(open(),"100","cash");var p=personal();var code=tx.execute(s->p.issue(scope,principal));
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Boolean> work=()->{start.await();try{tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"20",null,key())));return true;}catch(WalletException e){return false;}};
            var one=pool.submit(work);var two=pool.submit(work);start.countDown();assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(bills.list(scope)).hasSize(1);assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(8000);
        } finally {pool.shutdownNow();}
    }
    @Test void statusAndResultRequireOwnerStaffAndTenantAndNeverCreateEffects() {
        consent(5000);credit(open(),"100","cash");var p=personal();var code=tx.execute(s->p.issue(scope,principal));
        var stranger=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),UUID.randomUUID(),"other@test.invalid","other");
        assertThatThrownBy(()->p.status(scope,stranger,code.code())).isInstanceOf(WalletException.class);
        var noPermission=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of("wallet.charge.create"),Set.of(store));
        assertThatThrownBy(()->tx.execute(s->p.charge(scope,noPermission,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"1",null,key())))).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->p.result(scope,actor,key())).isInstanceOf(WalletException.class);
        var foreign=new StoreScope(new TenantId(UUID.randomUUID()),store);
        assertThatThrownBy(()->p.status(foreign,principal,code.code())).isInstanceOf(WalletException.class);
        assertThat(bills.list(scope)).isEmpty();
    }
    @Test void pendingPersonalBillCannotPayPartialBalanceAndRefreshRetainsReceipt() {
        consent(1000);var a=credit(open(),"1","cash");var p=personal();var code=tx.execute(s->p.issue(scope,principal));
        var result=tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"20",null,key())));
        assertThatThrownBy(()->tx.execute(s->authorization.quote(scope,principal,result.bill().id()))).isInstanceOf(WalletException.class);
        tx.execute(s->p.issue(scope,principal));assertThat(p.status(scope,principal,code.code()).bill().id()).isEqualTo(result.bill().id());
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(100);assertThat(bills.get(scope,result.bill().id(),false).walletApplied()).isFalse();
    }

    @Test void reconciliationClosesAbsentRequestAndRejectsLateCharge() {
        consent(5000);credit(open(),"100","cash");var p=personal();var code=tx.execute(s->p.issue(scope,principal));var key=key();
        var closed=tx.execute(s->p.reconcile(scope,actor,key));assertThat(closed.status()).isEqualTo("not_submitted");assertThat(closed.storeId()).isEqualTo(store);
        assertThat(p.result(scope,actor,key)).isEqualTo(closed);
        assertThatThrownBy(()->tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"1",null,key)))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_IDEMPOTENCY_CONFLICT");
        assertThat(bills.list(scope)).isEmpty();
    }
    @Test void concurrentChargeAndReconcileProduceOnlyPaidOrClosed() throws Exception {
        consent(5000);var a=credit(open(),"100","cash");var p=personal();var code=tx.execute(s->p.issue(scope,principal));var key=key();
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var charge=pool.submit(()->{start.await();try{return tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"20",null,key))).status();}catch(WalletException e){return "closed";}});
            var recovery=pool.submit(()->{start.await();return tx.execute(s->p.reconcile(scope,actor,key)).status();});start.countDown();
            String paid=charge.get(15,TimeUnit.SECONDS),reconciled=recovery.get(15,TimeUnit.SECONDS);
            assertThat(reconciled).isIn("paid","not_submitted");assertThat(paid).isEqualTo(reconciled.equals("paid")?"paid":"closed");
            assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(reconciled.equals("paid")?8000:10000);
        } finally {pool.shutdownNow();}
    }

    @Test void issuerBoundaryAndStaffResultCannotCrossStore() {
        consent(5000);var a=credit(open(),"100","cash");var p=personal();var code=tx.execute(s->p.issue(scope,principal));
        var sibling=UUID.randomUUID();var otherStore=UUID.randomUUID();var otherIssuer=UUID.randomUUID();
        jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'personal-other','other','active')",otherIssuer,tenant);
        for(var id:List.of(sibling,otherStore))jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,?,?,'active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",id,tenant,id.equals(sibling)?issuer:otherIssuer,id.toString(),"store");
        var siblingScope=new StoreScope(scope.tenantId(),sibling);var otherScope=new StoreScope(scope.tenantId(),otherStore);
        tx.execute(s->accounts.configure(siblingScope,issuer,List.of("cash"),null));tx.execute(s->accounts.configure(otherScope,otherIssuer,List.of("cash"),null));
        var allStores=new CurrentActor(tenant,actorId,"tenant_admin",actor.roles(),actor.permissions(),Set.of(store,sibling,otherStore));
        assertThatThrownBy(()->tx.execute(s->p.charge(otherScope,allStores,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"20",null,key())))).isInstanceOf(WalletException.class);
        var key=key();var result=tx.execute(s->p.charge(siblingScope,allStores,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"20",null,key)));
        assertThat(p.status(scope,principal,code.code()).bill()).isEqualTo(result.bill());
        assertThatThrownBy(()->p.result(scope,allStores,key)).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->tx.execute(s->p.reconcile(scope,allStores,key))).isInstanceOf(WalletException.class);
        assertThat(p.result(siblingScope,allStores,key)).isEqualTo(result);
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(8000);
    }
    @Test void expiredMissingAccountFrozenAndCommercialDeniedCannotDebit() {
        consent(5000);var p=personal();var code=tx.execute(s->p.issue(scope,principal));
        assertThatThrownBy(()->tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"1",null,key())))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_ACCOUNT_REQUIRED");
        var a=credit(open(),"100","cash");
        var later=new com.rpb.reservation.wallet.personalpayment.WalletPersonalPaymentService(accounts,bills,commands,new com.rpb.reservation.wallet.personalpayment.WalletPersonalPaymentRepository(jdbc),new WalletCustomerIdentityRepository(jdbc),access,directory,funding,Clock.offset(clock,Duration.ofSeconds(121)));
        assertThatThrownBy(()->tx.execute(s->later.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"1",null,key())))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_PERSONAL_CODE_EXPIRED");
        jdbc.update("update wallet_accounts set status='frozen' where id=?",a.id());
        assertThatThrownBy(()->tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"1",null,key())))).isInstanceOf(WalletException.class);
        when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.STORE_APP_NOT_ENABLED));
        assertThatThrownBy(()->tx.execute(s->p.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(code.code(),"1",null,key())))).isInstanceOf(WalletException.class);
        assertThat(bills.list(scope)).isEmpty();assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(10000);
    }

    @Test void expiryIsRecheckedAfterWaitingForTheAccountLock() throws Exception {
        consent(5000);var a=credit(open(),"100","cash");var issued=tx.execute(s->personal().issue(scope,principal));
        var now=new java.util.concurrent.atomic.AtomicReference<>(clock.instant());var waited=mock(Clock.class);when(waited.instant()).thenAnswer(i->now.get());
        var requestingAccount=new CountDownLatch(1);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        var observedAccounts=new WalletAccountRepository(jdbc,json){@Override public Account account(StoreScope s,UUID id,boolean lock){if(lock)requestingAccount.countDown();return super.account(s,id,lock);}};
        var service=new com.rpb.reservation.wallet.personalpayment.WalletPersonalPaymentService(observedAccounts,bills,commands,new com.rpb.reservation.wallet.personalpayment.WalletPersonalPaymentRepository(jdbc),new WalletCustomerIdentityRepository(jdbc),access,directory,funding,waited);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var blocker=pool.submit(()->tx.execute(s->{accounts.account(scope,a.id(),true);locked.countDown();try{if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");}catch(InterruptedException e){throw new IllegalStateException(e);}return true;}));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var scan=pool.submit(()->{try{tx.execute(s->service.charge(scope,actor,new com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.ScanCharge(issued.code(),"1",null,key())));return "paid";}catch(WalletException e){return e.code();}});
            assertThat(requestingAccount.await(10,TimeUnit.SECONDS)).isTrue();now.set(clock.instant().plusSeconds(121));release.countDown();
            assertThat(blocker.get(15,TimeUnit.SECONDS)).isTrue();assertThat(scan.get(15,TimeUnit.SECONDS)).isEqualTo("WALLET_PERSONAL_CODE_EXPIRED");
            assertThat(bills.list(scope)).isEmpty();assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(10000);
        } finally {release.countDown();pool.shutdownNow();}
    }
}
