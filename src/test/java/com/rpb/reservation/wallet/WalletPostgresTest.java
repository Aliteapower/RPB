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
class WalletPostgresTest {
    static final String SCHEMA="wallet_test_"+UUID.randomUUID().toString().replace("-","");
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
    com.rpb.reservation.wallet.collectionmethods.CollectionMethodService methodService(){return new com.rpb.reservation.wallet.collectionmethods.CollectionMethodService(access,accounts,directory,commands,new com.rpb.reservation.wallet.collectionmethods.CollectionMethodRepository(jdbc,json));}
    @Test void customCollectionSubjectsPreserveHistoryAndRecoverPendingAfterRemoval(){
        var service=methodService();var before=service.get(scope,actor);var method=new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Method(null,"柜台现金","cash");String requestKey=key();
        var change=new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Update(before.version(),List.of(method),requestKey);
        var saved=tx.execute(s->service.save(scope,actor,change));var replay=tx.execute(s->service.save(scope,actor,change));assertThat(replay).isEqualTo(saved);
        String code=saved.methods().getFirst().code();var account=open();var first=create(account,"10",code);assertThat(first.offerSnapshot().get("channelName")).isEqualTo("柜台现金");
        var renamed=tx.execute(s->service.save(scope,actor,new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Update(saved.version(),List.of(new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Method(code,"新现金名称","cash")),key())));
        var second=create(account,"5",code);assertThat(second.offerSnapshot().get("channelName")).isEqualTo("新现金名称");
        tx.execute(s->service.save(scope,actor,new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Update(renamed.version(),List.of(new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Method(null,"转账","other")),key())));
        assertThatThrownBy(()->create(account,"1",code)).isInstanceOf(WalletException.class);
        var paid=tx.execute(s->topups.confirm(scope,actor,first.id(),confirm("10",code,"")));assertThat(paid.account().balanceCents()).isEqualTo(1000);
        var receipt=new WalletCollectionRepository(jdbc).list(scope,first.id(),null).getFirst();assertThat(receipt.channelName()).isEqualTo("柜台现金");assertThat(receipt.receiptReference()).startsWith("CASH-TOPUP-");
        assertThat(topupRepo.get(scope,first.id(),false).offerSnapshot().get("channelName")).isEqualTo("柜台现金");
        var repository=new WalletCollectionRepository(jdbc);var exceptions=new WalletCollectionExceptionService(accounts,topupRepo,bills,new WalletCollectionService(repository,collectionPort),repository,commands,access);String otherCode=service.get(scope,actor).methods().getFirst().code();
        var fact=tx.execute(s->exceptions.record(scope,actor,new CollectionException("top_up",second.id(),confirm("5",otherCode,"OTHER-ACTUAL-RECEIPT"),"实际收款方式不同",key())));assertThat(fact.channelName()).isEqualTo("转账");assertThat(accounts.account(scope,account.id(),false).balanceCents()).isEqualTo(1000);
    }
    @Test void collectionSubjectConfigurationIsVersionedAndTenantAdministratorScoped(){
        var service=methodService();var original=service.get(scope,actor);
        var input=new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Update(original.version(),List.of(new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Method("cash","现金","cash")),key());
        tx.execute(s->service.save(scope,actor,input));assertThatThrownBy(()->tx.execute(s->service.save(scope,actor,new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Update(original.version(),input.methods(),key())))).isInstanceOf(WalletException.class);
        var employee=new CurrentActor(tenant,actorId,"staff",Set.of("store_manager"),actor.permissions(),actor.storeIds());assertThatThrownBy(()->service.get(scope,employee)).isInstanceOf(WalletException.class);
        var outsider=new CurrentActor(UUID.randomUUID(),actorId,"tenant_admin",actor.roles(),actor.permissions(),actor.storeIds());assertThatThrownBy(()->service.get(scope,outsider)).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->service.get(new StoreScope(scope.tenantId(),UUID.randomUUID()),actor)).isInstanceOf(WalletException.class);
        UUID nextStore=UUID.randomUUID();jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'second','二店','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",nextStore,tenant,issuer);
        var nextScope=new StoreScope(scope.tenantId(),nextStore);var nextActor=new CurrentActor(tenant,actorId,"tenant_admin",actor.roles(),actor.permissions(),Set.of(nextStore));when(directory.store(nextScope)).thenReturn(new StoreIdentity(tenant,nextStore,issuer,"发行主体","二店","test","Asia/Singapore","zh-CN",null));
        assertThatThrownBy(()->tx.execute(s->service.save(nextScope,nextActor,new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Update(null,input.methods(),key())))).isInstanceOf(WalletException.class);
        var attached=tx.execute(s->accountService.configure(nextScope,nextActor,new ProgramSettings(null,List.of("cash","paynow","credit_card"),key())));assertThat(attached.channels()).containsExactly("cash");assertThat(attached.channelLabels().get("cash")).isEqualTo("现金");
    }
    @Test void methodChangesInvalidateQuotesAndAliasesCannotReuseReceipts(){
        var service=methodService();var current=service.get(scope,actor);var input=List.of(new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Method(null,"现金一","cash"),new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Method(null,"现金二","cash"));
        var saved=tx.execute(s->service.save(scope,actor,new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Update(current.version(),input,key())));var account=open();var offers=tx.execute(s->marketing.offers(scope,actor));var offer=offers.offers().getFirst();String one=saved.methods().getFirst().code(),two=saved.methods().getLast().code();
        var quote=tx.execute(s->marketing.quote(scope,actor,new com.rpb.reservation.wallet.marketing.MarketingDtos.QuoteRequest(account.id(),offer.id(),offer.version(),one)));
        tx.execute(s->service.save(scope,actor,new com.rpb.reservation.wallet.collectionmethods.CollectionMethods.Update(saved.version(),saved.methods().reversed(),key())));
        assertThatThrownBy(()->tx.execute(s->topups.create(scope,actor,new TopupCreate(account.id(),null,null,key(),quote.quoteId())))).isInstanceOf(WalletException.class);
        var first=create(account,"10",one);tx.execute(s->topups.confirm(scope,actor,first.id(),confirm("10",one,"DUPLICATE-RECEIPT")));
        var second=create(account,"10",two);assertThatThrownBy(()->tx.execute(s->topups.confirm(scope,actor,second.id(),confirm("10",two,"DUPLICATE-RECEIPT")))).isInstanceOf(WalletException.class);
        assertThat(accounts.account(scope,account.id(),false).balanceCents()).isEqualTo(1000);
    }
    String key(){return UUID.randomUUID().toString();}
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

    WalletCustomerIdentityService identities(Clock time){return new WalletCustomerIdentityService(accounts,auths,new WalletCustomerIdentityRepository(jdbc),access,directory,time);}
    @Test void personalIdentityWorksWithoutMoneyAccountForPointsEmployeeAndDoesNotGrantDebitConsent(){
        var service=identities(clock);var code=tx.execute(s->service.issue(scope,principal));assertThat(code.code()).startsWith("WI.");
        var pointsActor=new CurrentActor(tenant,actorId,"staff",Set.of(),Set.of("wallet.points.view"),Set.of(store));
        assertThat(service.resolve(scope,pointsActor,code.code()).id()).isEqualTo(customer);assertThat(service.resolve(scope,pointsActor,code.code()).id()).isEqualTo(customer);
        assertThat(jdbc.queryForObject("select count(*) from wallet_accounts where tenant_id=?",Integer.class,tenant)).isZero();assertThat(jdbc.queryForObject("select count(*) from wallet_entries where tenant_id=?",Integer.class,tenant)).isZero();
        assertThat(jdbc.queryForObject("select code_hash from wallet_customer_identity_codes where tenant_id=?",String.class,tenant)).isEqualTo(com.rpb.reservation.wallet.domain.WalletTokens.hash(code.code()));
        var a=open();assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,charge(a,"1",code.code())))).isInstanceOf(WalletException.class);noScanEffects(a,0,0);
    }
    @Test void personalIdentityRequiresFreshScopedCodeAndAuthorizedActor(){
        var service=identities(clock);var old=tx.execute(s->service.issue(scope,principal));var fresh=tx.execute(s->service.issue(scope,principal));
        assertThatThrownBy(()->service.resolve(scope,actor,old.code())).isInstanceOf(WalletException.class);
        assertThat(identities(Clock.offset(clock,Duration.ofSeconds(119))).resolve(scope,actor,fresh.code()).id()).isEqualTo(customer);
        assertThatThrownBy(()->identities(Clock.offset(clock,Duration.ofSeconds(121))).resolve(scope,actor,fresh.code())).isInstanceOf(WalletException.class);
        var denied=new CurrentActor(tenant,actorId,"staff",Set.of(),Set.of("wallet.view"),Set.of(store));assertThatThrownBy(()->service.resolve(scope,denied,fresh.code())).isInstanceOf(WalletException.class);
        var foreign=new StoreScope(new TenantId(UUID.randomUUID()),store);assertThatThrownBy(()->service.resolve(foreign,actor,fresh.code())).isInstanceOf(WalletException.class);
        var anotherStore=new StoreScope(new TenantId(tenant),UUID.randomUUID());assertThatThrownBy(()->service.resolve(anotherStore,actor,fresh.code())).isInstanceOf(WalletException.class);
        var a=open();var wp=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id(),"topup"));assertThat(service.resolve(scope,actor,wp.code()).id()).isEqualTo(customer);
        var wc=chargeCode(a);assertThatThrownBy(()->service.resolve(scope,actor,wc.code())).isInstanceOf(WalletException.class);
        for(var reason:List.of(AppGateDenyReason.STORE_APP_EXPIRED,AppGateDenyReason.TENANT_APP_NOT_ENABLED,AppGateDenyReason.STORE_APP_NOT_ENABLED)){
            when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,reason));assertThatThrownBy(()->service.resolve(scope,actor,fresh.code())).isInstanceOf(WalletException.class);
        }

    }

    @Test void topupCodeResolvesWithTopupPermissionOnlyWithoutFinancialEffectsOrChargeConsent() {
        var a=open();var code=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id(),"topup"));
        assertThat(code.type()).isEqualTo("customer_wallet");assertThat(code.code()).startsWith("WP.");
        var cashier=new CurrentActor(tenant,actorId,"staff",Set.of("store_staff"),Set.of("wallet.view","wallet.topup.create"),Set.of(store));
        for(int i=0;i<2;i++)assertThat(authorization.resolve(scope,cashier,code.code(),"topup").id()).isEqualTo(a.id());
        assertThatThrownBy(()->authorization.resolve(scope,cashier,code.code())).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->authorization.resolve(scope,actor,code.code(),"unknown")).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,charge(a,"1",code.code())))).isInstanceOf(WalletException.class);
        noScanEffects(a,0,0);assertThat(ledger.entries(scope,a.id(),0)).isEmpty();assertThat(topupRepo.list(scope,new WalletHistoryQuery(0,"all"))).isEmpty();
        var t=create(a,"10","cash");var c=confirm("10","cash","");
        var result=tx.execute(s->topups.confirm(scope,actor,t.id(),c));
        TopupResult replay=tx.execute(s->topups.confirm(scope,actor,t.id(),c));assertThat(replay).isEqualTo(result);
        assertThat(result.account().balanceCents()).isEqualTo(1000);assertThat(ledger.entries(scope,a.id(),0)).hasSize(1);
    }
    @Test void resolvingPaymentCodeForTopupDoesNotConsumeItAndTopupCodesStillEnforceScopeAndExpiry() {
        var a=credit(open(),"5","cash");var charge=chargeCode(a);
        assertThat(authorization.resolve(scope,actor,charge.code(),"topup").id()).isEqualTo(a.id());
        assertThat(tx.execute(s->scanner(clock).charge(scope,actor,charge(a,"1",charge.code()))).account().balanceCents()).isEqualTo(400);
        var code=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id(),"topup"));
        var foreign=new StoreScope(new TenantId(UUID.randomUUID()),store);
        assertThatThrownBy(()->authorization.resolve(foreign,actor,code.code(),"topup")).isInstanceOf(WalletException.class);
        var later=new WalletAuthorizationService(accounts,ledger,commands,bills,auths,access,Clock.offset(clock,Duration.ofSeconds(60)),funding);
        assertThatThrownBy(()->later.resolve(scope,actor,code.code(),"topup")).isInstanceOf(WalletException.class);
        jdbc.update("update wallet_accounts set status='frozen' where id=?",a.id());
        assertThatThrownBy(()->authorization.resolve(scope,actor,code.code(),"topup")).isInstanceOf(WalletException.class);
        jdbc.update("update wallet_accounts set status='active' where id=?",a.id());
        for(var reason:List.of(AppGateDenyReason.STORE_APP_EXPIRED,AppGateDenyReason.TENANT_APP_NOT_ENABLED,AppGateDenyReason.APP_DISABLED)) {
            when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,reason));
            assertThatThrownBy(()->authorization.resolve(scope,actor,code.code(),"topup")).isInstanceOf(WalletException.class);
        }
        assertThat(ledger.entries(scope,a.id(),0)).hasSize(2);assertThat(bills.list(scope)).hasSize(1);
    }
    @Test void merchantScanConfirmationImmediatelySettlesAndCustomerSeesTheSameReceiptWithoutConfirming() {
        var a=credit(open(),"50","cash");var code=chargeCode(a);assertThat(code.code()).startsWith("WC.");
        assertThat(scanner(clock).status(scope,principal,code.code()).bill()).isNull();
        var c=charge(a,"20",code.code());var result=tx.execute(s->scanner(clock).charge(scope,actor,c));
        assertThat(result.bill().status()).isEqualTo("paid");assertThat(result.bill().walletApplied()).isTrue();assertThat(result.account().balanceCents()).isEqualTo(3000);
        assertThat(result.bill().walletPaidCents()).isEqualTo(2000);assertThat(result.bill().externalPaidCents()).isZero();
        assertThat(scanner(clock).status(scope,principal,code.code()).bill()).isEqualTo(result.bill());
        var later=scanner(Clock.offset(clock,Duration.ofMinutes(2)));
        Paid replay=tx.execute(s->later.charge(scope,actor,c));assertThat(replay).isEqualTo(result);
        assertThat(later.status(scope,principal,code.code()).bill()).isEqualTo(result.bill());
        assertThatThrownBy(()->pay(result.bill())).isInstanceOf(WalletException.class);
        assertThat(ledger.entries(scope,a.id(),0)).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from wallet_audit where tenant_id=? and action='bill.scan_charge'",Integer.class,tenant)).isEqualTo(1);
    }
    @Test void scanChargeHandlesInsufficientAndZeroAvailableBalanceWithoutPretendingSupplementWasReceived() {
        var a=credit(open(),"5","cash");var code=chargeCode(a);
        var paid=tx.execute(s->scanner(clock).charge(scope,actor,charge(a,"8",code.code())));
        assertThat(paid.bill().status()).isEqualTo("partially_paid");assertThat(paid.bill().remainingCents()).isEqualTo(300);assertThat(paid.account().balanceCents()).isZero();
        var zero=paid.account();var next=chargeCode(zero);var unfunded=tx.execute(s->scanner(clock).charge(scope,actor,charge(zero,"2",next.code())));
        assertThat(unfunded.bill().status()).isEqualTo("unpaid");assertThat(unfunded.bill().walletApplied()).isTrue();assertThat(unfunded.bill().remainingCents()).isEqualTo(200);
        assertThat(ledger.entries(scope,a.id(),0)).hasSize(2);
        var settled=tx.execute(s->checkout.collect(scope,actor,paid.bill().id(),confirm("3","cash",key())));assertThat(settled.status()).isEqualTo("paid");
    }
    @Test void expiredOrOldIdentityCodesCannotLeaveAnyScanChargeBillOrCommand() {
        var a=credit(open(),"5","cash");var old=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id()));
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,charge(a,"1",old.code())))).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).code()).isEqualTo("WALLET_REQUEST_INVALID");
        var code=chargeCode(a);var later=scanner(Clock.offset(clock,Duration.ofSeconds(60)));
        assertThatThrownBy(()->tx.execute(s->later.charge(scope,actor,charge(a,"1",code.code())))).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).code()).isEqualTo("WALLET_AUTHORIZATION_EXPIRED");
        noScanEffects(a,500,0);assertThat(ledger.entries(scope,a.id(),0)).hasSize(1);
    }
    @Test void staleAccountPreviewAndMismatchedIdempotencyCannotSilentlyChargeAnotherAmount() {
        var a=credit(open(),"5","cash");var code=chargeCode(a);var command=charge(a,"2",code.code());credit(a,"1","cash");
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,command))).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).code()).isEqualTo("WALLET_QUOTE_STALE");
        noScanEffects(a,600,0);
        var refreshed=accounts.account(scope,a.id(),false);var c=charge(refreshed,"2",code.code());tx.execute(s->scanner(clock).charge(scope,actor,c));
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,new ScanCharge(c.accountId(),"3",c.customerCode(),c.expectedAccountVersion(),null,null,c.idempotencyKey())))).isInstanceOf(WalletException.class).extracting(e->((WalletException)e).code()).isEqualTo("WALLET_IDEMPOTENCY_CONFLICT");
        assertThat(bills.list(scope)).hasSize(1);assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(400);
    }
    @Test void freshScanCanPayAnExistingBillButNeverChangesItsAmountOrCreatesADuplicate() {
        var a=credit(open(),"5","cash");var b=bill(a,"5");var code=chargeCode(a);
        var wrong=new ScanCharge(a.id(),"6",code.code(),a.version(),b.id(),b.version(),key());
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,wrong))).isInstanceOf(WalletException.class);
        var c=new ScanCharge(a.id(),"5",code.code(),a.version(),b.id(),b.version(),key());
        var result=tx.execute(s->scanner(clock).charge(scope,actor,c));assertThat(result.bill().id()).isEqualTo(b.id());assertThat(result.bill().status()).isEqualTo("paid");assertThat(bills.list(scope)).hasSize(1);
        assertThat(result.account().balanceCents()).isZero();
        var next=chargeCode(result.account());
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,new ScanCharge(a.id(),"5",next.code(),result.account().version(),b.id(),result.bill().version(),key())))).isInstanceOf(WalletException.class);
    }
    @Test void existingBillVersionAndCancellationAreCheckedBeforeUsingTheCode() {
        var a=credit(open(),"5","cash");var b=bill(a,"5");var code=chargeCode(a);
        tx.execute(s->checkout.collect(scope,actor,b.id(),confirm("1","cash",key())));
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,new ScanCharge(a.id(),"5",code.code(),a.version(),b.id(),b.version(),key())))).isInstanceOf(WalletException.class);
        var latest=bills.get(scope,b.id(),false);var result=tx.execute(s->scanner(clock).charge(scope,actor,new ScanCharge(a.id(),"5",code.code(),a.version(),b.id(),latest.version(),key())));
        assertThat(result.bill().walletPaidCents()).isEqualTo(400);assertThat(result.account().balanceCents()).isEqualTo(100);
        var closed=bill(result.account(),"1");tx.execute(s->checkout.cancel(scope,actor,closed.id(),key()));var next=chargeCode(result.account());
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,new ScanCharge(a.id(),"1",next.code(),result.account().version(),closed.id(),bills.get(scope,closed.id(),false).version(),key())))).isInstanceOf(WalletException.class);
    }
    @Test void scanChargeRequiresBothPermissionsAndCommercialAccessAndKeepsReceiptReadableAfterExpiry() {
        var a=credit(open(),"5","cash");var code=chargeCode(a);
        for(String missing:List.of("wallet.charge.create","wallet.collection.confirm")) {
            var perms=new HashSet<>(actor.permissions());perms.remove(missing);var restricted=new CurrentActor(tenant,actorId,"staff",Set.of("store_staff"),perms,Set.of(store));
            assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,restricted,charge(a,"1",code.code())))).isInstanceOf(WalletException.class);
        }
        for(var reason:List.of(AppGateDenyReason.STORE_APP_EXPIRED,AppGateDenyReason.TENANT_APP_NOT_ENABLED,AppGateDenyReason.APP_DISABLED)) {
            when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,reason));
            assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,charge(a,"1",code.code())))).isInstanceOf(WalletException.class);
        }
        noScanEffects(a,500,0);when(gate.evaluate(any())).thenReturn(AppGateDecision.allow("wallet",tenant,store,null));
        var result=tx.execute(s->scanner(clock).charge(scope,actor,charge(a,"1",code.code())));
        when(gate.evaluateCommercial(any(),any(),any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.STORE_APP_EXPIRED));
        assertThat(scanner(clock).status(scope,principal,code.code()).bill()).isEqualTo(result.bill());
    }
    @Test void wrongCustomerTenantStoreAndFrozenOrArchivedIdentityCannotUseOrReadChargeCode() {
        var a=credit(open(),"5","cash");var code=chargeCode(a);
        var other=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),UUID.randomUUID(),"other@example.invalid","other");
        assertThatThrownBy(()->scanner(clock).status(scope,other,code.code())).isInstanceOf(WalletException.class);
        var otherTenant=new StoreScope(new TenantId(UUID.randomUUID()),store);
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(otherTenant,actor,charge(a,"1",code.code())))).isInstanceOf(WalletException.class);
        var otherStore=new StoreScope(new TenantId(tenant),UUID.randomUUID());
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(otherStore,actor,charge(a,"1",code.code())))).isInstanceOf(WalletException.class);
        jdbc.update("update wallet_accounts set status='frozen' where id=?",a.id());
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,charge(a,"1",code.code())))).isInstanceOf(WalletException.class);
        jdbc.update("update wallet_accounts set status='active' where id=?",a.id());
        when(directory.customer(scope.tenantScope(),customer)).thenThrow(WalletException.missing());
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,charge(a,"1",code.code())))).isInstanceOf(WalletException.class);
        noScanEffects(a,500,0);
    }
    @Test void racingScanCommandsWithDifferentKeysDebitOnceAndCreateOnlyOneBill() throws Exception {
        var a=credit(open(),"5","cash");var code=chargeCode(a);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var jobs=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)jobs.add(pool.submit(()->{start.await();try{tx.execute(s->scanner(clock).charge(scope,actor,charge(a,"3",code.code())));return true;}catch(WalletException e){return false;}}));
            start.countDown();int success=0;for(var job:jobs)if(job.get(15,TimeUnit.SECONDS))success++;
            assertThat(success).isEqualTo(1);assertThat(bills.list(scope)).hasSize(1);assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(200);assertThat(ledger.entries(scope,a.id(),0)).hasSize(2);
        } finally {pool.shutdownNow();}
    }
    @Test void failedScanTransactionRollsBackTheDebitBillBindingLedgerAndIdempotencyTogether() {
        var a=credit(open(),"5","cash");var code=chargeCode(a);var c=charge(a,"3",code.code());
        assertThatThrownBy(()->tx.execute(s->{scanner(clock).charge(scope,actor,c);throw new IllegalStateException("force rollback");})).isInstanceOf(IllegalStateException.class);
        noScanEffects(a,500,0);assertThat(ledger.entries(scope,a.id(),0)).hasSize(1);assertThat(scanner(clock).status(scope,principal,code.code()).bill()).isNull();
        assertThat(tx.execute(s->scanner(clock).charge(scope,actor,c)).bill().status()).isEqualTo("paid");
    }
    @Test void codeThatExpiresWhileWaitingForItsDatabaseLockDoesNotLeaveABill() throws Exception {
        var a=credit(open(),"5","cash");var code=chargeCode(a);var current=new java.util.concurrent.atomic.AtomicReference<>(clock.instant());
        var time=mock(Clock.class);when(time.instant()).thenAnswer(i->current.get());
        var reached=new CountDownLatch(1);when(directory.customer(scope.tenantScope(),customer)).thenAnswer(i->{reached.countDown();return new Customer(customer,"customer","测试顾客",null,null);});
        var pool=Executors.newSingleThreadExecutor();var job=new java.util.concurrent.atomic.AtomicReference<Future<Paid>>();
        try {
            tx.execute(s->{
                jdbc.queryForList("select id from wallet_payment_codes where tenant_id=? and code_hash=? for update",tenant,WalletTokens.hash(code.code()));
                job.set(pool.submit(()->tx.execute(inner->scanner(time).charge(scope,actor,charge(a,"1",code.code())))));
                try {assertThat(reached.await(5,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->job.get().get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}
                catch(InterruptedException e){throw new IllegalStateException(e);}
                current.set(clock.instant().plusSeconds(60));return null;
            });
            assertThatThrownBy(()->job.get().get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(WalletException.class);
            noScanEffects(a,500,0);assertThat(ledger.entries(scope,a.id(),0)).hasSize(1);
        } finally {pool.shutdownNow();}
    }
    @Test void chargeCodeCannotPayTheWrongAccountOrBeDowngradedToLegacyBinding() {
        var a=credit(open(),"5","cash");var code=chargeCode(a);var another=UUID.randomUUID();
        jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,status) values(?,?,'another','regular','active')",another,tenant);
        var other=tx.execute(s->accounts.open(scope,accounts.requireProgram(scope,true),another));
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(scope,actor,charge(other,"1",code.code())))).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->tx.execute(s->checkout.create(scope,actor,new BillCreate(a.id(),"1",key(),code.code())))).isInstanceOf(WalletException.class);
        noScanEffects(a,500,0);
    }

    @Test void newAccountRequiresPhoneWhileExistingLegacyAccountIsPreserved() {
        when(directory.customer(scope.tenantScope(),customer)).thenReturn(new Customer(customer,"customer","Legacy",null,null));
        assertThatThrownBy(this::open).isInstanceOf(WalletException.class)
            .extracting(e -> ((WalletException)e).code()).isEqualTo("CUSTOMER_PHONE_REQUIRED");
        var legacy=tx.execute(s->accounts.open(scope,accounts.requireProgram(scope,true),customer));
        assertThat(open().id()).isEqualTo(legacy.id());
        assertThat(accountService.forCustomer(scope,actor,customer)).extracting(Account::id).containsExactly(legacy.id());
    }

    @Test void reusesCustomerAndCreatesOnlyOneZeroBalanceAccount() {
        var a=open();var b=open();assertThat(a.id()).isEqualTo(b.id());assertThat(a.balanceCents()).isZero();assertThat(a.customerId()).isEqualTo(customer);
        assertThat(jdbc.queryForObject("select count(*) from customers where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
    }
    @Test void allThreeChannelsRequireExplicitOfflineConfirmationAndAreIdempotent() {
        var a=open();
        for(String channel:List.of("cash","paynow","credit_card")) {
            var t=create(a,"10.00",channel);assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(a.balanceCents());
            var c=confirm("10.00",channel,key());
            var result=tx.execute(s->topups.confirm(scope,actor,t.id(),c));
            TopupResult replay=tx.execute(s->topups.confirm(scope,actor,t.id(),c));assertThat(replay).isEqualTo(result);a=result.account();
        }
        assertThat(a.balanceCents()).isEqualTo(3000);assertThat(ledger.entries(scope,a.id(),0)).hasSize(3);
    }
    @Test void cashTopupsWithoutPaperReceiptCreditOnceAndKeepDistinctInternalReferences() {
        var a=open();var first=create(a,"10","cash");
        var c=confirm("10","cash",null);
        var result=tx.execute(s->topups.confirm(scope,actor,first.id(),c));
        TopupResult replay=tx.execute(s->topups.confirm(scope,actor,first.id(),c));assertThat(replay).isEqualTo(result);
        tx.execute(s->topups.confirm(scope,actor,first.id(),confirm("10","cash","")));
        var second=create(a,"10","cash");
        tx.execute(s->topups.confirm(scope,actor,second.id(),confirm("10","cash","  ")));
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(2000);
        assertThat(ledger.entries(scope,a.id(),0)).hasSize(2);
        assertThat(jdbc.queryForList("select receipt_reference from wallet_collections where tenant_id=? order by receipt_reference",String.class,tenant))
            .containsExactlyInAnyOrder("CASH-TOPUP-"+first.id(),"CASH-TOPUP-"+second.id());
    }

    @Test void preventsReceiptReuseAcrossDifferentTopupsAndRejectsOcrLikeUnverifiedConfirmation() {
        var a=open();var t=create(a,"50","paynow");var confirmation=confirm("50","paynow","bank-unique");
        tx.execute(s->topups.confirm(scope,actor,t.id(),confirmation));var another=create(a,"50","paynow");
        assertThatThrownBy(()->tx.execute(s->topups.confirm(scope,actor,another.id(),confirm("50","paynow","bank-unique")))).isInstanceOf(WalletException.class).hasMessageContaining("凭证");
        var unverified=new CollectionConfirm("paynow","50","merchant-account","other","T1","screenshot",false,key());
        assertThatThrownBy(()->tx.execute(s->topups.confirm(scope,actor,another.id(),unverified))).isInstanceOf(WalletException.class);
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(5000);
    }
    @Test void partialPaymentDebitsWalletOnceAndKeepsTheOutstandingBillUntilConfirmed() {
        for(String channel:List.of("cash","paynow","credit_card")) {
        var a=credit(open(),"50","cash");var b=bill(a,"80");var paid=pay(b);
        assertThat(paid.account().balanceCents()).isZero();assertThat(paid.bill().remainingCents()).isEqualTo(3000);assertThat(paid.bill().status()).isEqualTo("partially_paid");
        var settled=tx.execute(s->checkout.collect(scope,actor,b.id(),confirm("30",channel,key())));
        assertThat(settled.status()).isEqualTo("paid");assertThat(settled.walletPaidCents()).isEqualTo(5000);
        assertThatThrownBy(()->pay(b)).isInstanceOf(WalletException.class);
        var equal=pay(bill(credit(a,"10","cash"),"10"));assertThat(equal.bill().status()).isEqualTo("paid");assertThat(equal.account().balanceCents()).isZero();
        }
    }
    @Test void staleQuoteCannotSilentlyDebitChangedBalance() {
        var a=credit(open(),"50","cash");var b=bill(a,"80");var q=tx.execute(s->authorization.quote(scope,principal,b.id()));
        credit(a,"10","cash");assertThatThrownBy(()->tx.execute(s->authorization.pay(scope,principal,b.id(),new Pay(q.id(),key())))).isInstanceOf(WalletException.class);
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(6000);
    }
    @Test void concurrentBillsNeverOverdrawWalletAndForceRequote() throws Exception {
        var a=credit(open(),"50","cash");var first=bill(a,"40");var second=bill(a,"40");
        var q1=tx.execute(s->authorization.quote(scope,principal,first.id()));var q2=tx.execute(s->authorization.quote(scope,principal,second.id()));
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> jobs=new ArrayList<>();
            for(var q:List.of(q1,q2)) jobs.add(pool.submit(()->{start.await();try{tx.execute(s->authorization.pay(scope,principal,q.billId(),new Pay(q.id(),key())));return true;}catch(WalletException e){return false;}}));
            start.countDown();int successes=0;for(var job:jobs)if(job.get(15,TimeUnit.SECONDS))successes++;
            assertThat(successes).isEqualTo(1);assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(1000);
        } finally {pool.shutdownNow();}
    }
    @Test void tokensBindOnlyOnceAndDoNotAuthorizePaymentByScanning() {
        var a=credit(open(),"50","cash");var one=bill(a,"20");var two=bill(a,"20");
        var code=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id()));
        tx.execute(s->authorization.bind(scope,actor,one.id(),code.code()));
        assertThatThrownBy(()->tx.execute(s->authorization.bind(scope,actor,two.id(),code.code()))).isInstanceOf(WalletException.class);
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(5000);
    }
    @Test void billHistoryIncludesPaidClosedAndZeroDebitBillsButOnlyForTheCurrentCustomerAndStore() {
        var a=credit(open(),"1","cash");var settled=pay(bill(a,"1")).bill();
        var zero=pay(bill(a,"2")).bill(); // zero balance creates no debit entry, but remains a bill
        var closed=bill(a,"3");tx.execute(s->checkout.cancel(scope,actor,closed.id(),key()));
        var sibling=UUID.randomUUID();
        jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'history-sibling','其他门店','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",sibling,tenant,issuer);
        jdbc.update("insert into wallet_program_stores(program_id,tenant_id,store_id,operating_entity_id) values(?,?,?,?)",a.programId(),tenant,sibling,issuer);
        var siblingScope=new StoreScope(new TenantId(tenant),sibling);
        when(directory.store(siblingScope)).thenReturn(new StoreIdentity(tenant,sibling,issuer,"发行主体","其他门店","test","Asia/Singapore","zh-CN",null));
        Bill otherStore=tx.execute(s->bills.create(siblingScope,a,100,actorId));
        var otherCustomer=UUID.randomUUID();
        jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,status) values(?,?,'history-other','regular','active')",otherCustomer,tenant);
        var other=tx.execute(s->accounts.open(scope,accounts.requireProgram(scope,true),otherCustomer));bill(other,"4");
        var history=checkout.customerHistory(scope,principal,new WalletHistoryQuery(0,"all"));
        assertThat(history).extracting(Bill::id).containsExactlyInAnyOrder(settled.id(),zero.id(),closed.id());
        assertThat(checkout.customerHistory(siblingScope,principal,new WalletHistoryQuery(0,"all"))).extracting(Bill::id).containsExactly(otherStore.id());
        assertThat(checkout.customerHistory(scope,principal,new WalletHistoryQuery(0,"pending"))).extracting(Bill::id).containsExactly(zero.id());
        assertThat(checkout.customerHistory(scope,principal,new WalletHistoryQuery(0,"completed"))).extracting(Bill::id).containsExactly(settled.id());
        assertThat(checkout.customerHistory(scope,principal,new WalletHistoryQuery(0,"closed"))).extracting(Bill::id).containsExactly(closed.id());
        assertThat(checkout.customerHistory(scope,principal,new WalletHistoryQuery(100,"all"))).isEmpty();
        var wrongTenant=new CustomerAuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),customer,"other@example.com","other");
        assertThatThrownBy(()->checkout.customerHistory(scope,wrongTenant,new WalletHistoryQuery(0,"all"))).isInstanceOf(WalletException.class);
        when(gate.evaluateCommercial(any(),any(),any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.STORE_APP_EXPIRED));
        assertThat(checkout.customerHistory(scope,principal,new WalletHistoryQuery(0,"all"))).hasSize(3);
    }
    @Test void pagedStaffHistoryAndTopupDetailRetainScopeAndRejectInvalidQueryParameters() {
        var a=open();var pending=create(a,"1","cash");var cancelled=create(a,"2","cash");
        tx.execute(s->topups.cancel(scope,actor,cancelled.id(),key()));
        assertThat(topups.get(scope,actor,pending.id())).isEqualTo(pending);
        assertThat(topups.list(scope,actor,new WalletHistoryQuery(0,"pending"))).extracting(Topup::id).containsExactly(pending.id());
        assertThat(topups.list(scope,actor,new WalletHistoryQuery(0,"closed"))).extracting(Topup::id).containsExactly(cancelled.id());
        jdbc.update("insert into wallet_bills(tenant_id,program_id,account_id,store_id,amount_cents,code_hash,created_by) select ?,?,?,?,1,gen_random_uuid()::text,? from generate_series(1,101)",tenant,a.programId(),a.id(),store,actorId);
        assertThat(checkout.list(scope,actor,new WalletHistoryQuery(0,"all"))).hasSize(100);
        assertThat(checkout.list(scope,actor,new WalletHistoryQuery(100,"all"))).hasSize(1);
        assertThatThrownBy(()->new WalletHistoryQuery(-1,"all")).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->new WalletHistoryQuery(0,"invalid")).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->new WalletHistoryQuery(100001,"all")).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->new WalletHistoryQuery(0,null)).isInstanceOf(WalletException.class);
        var denied=new CurrentActor(tenant,actorId,"staff",Set.of("store_staff"),Set.of(),Set.of(store));
        assertThatThrownBy(()->topups.get(scope,denied,pending.id())).isInstanceOf(WalletException.class);
        var otherScope=new StoreScope(new TenantId(UUID.randomUUID()),store);
        assertThatThrownBy(()->topups.get(otherScope,actor,pending.id())).isInstanceOf(WalletException.class);
    }
    @Test void expiredOrRegeneratedScansNeverCreateBillsCommandsOrAudit() {
        var a=open();
        var old=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id()));
        var fresh=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id()));
        assertRejectedScanLeavesNoRows(new BillCreate(a.id(),"5",key(),old.code()));
        jdbc.update("update wallet_payment_codes set expires_at=? where tenant_id=?",java.sql.Timestamp.from(clock.instant()),tenant);
        assertRejectedScanLeavesNoRows(new BillCreate(a.id(),"5",key(),fresh.code()));
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isZero();
    }
    @Test void scanCreatesAndBindsOnceAndReplaysAfterExpiryWithoutDebiting() {
        var a=credit(open(),"5","cash");var code=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id()));
        var c=new BillCreate(a.id(),"5",key(),code.code());
        var b=tx.execute(s->checkout.create(scope,actor,c));
        assertThat(jdbc.queryForObject("select bill_id from wallet_payment_codes where tenant_id=? and account_id=?",UUID.class,tenant,a.id())).isEqualTo(b.id());
        jdbc.update("update wallet_payment_codes set expires_at=? where tenant_id=?",java.sql.Timestamp.from(clock.instant()),tenant);
        Bill replay=tx.execute(s->checkout.create(scope,actor,c));assertThat(replay).isEqualTo(b);
        assertRejectedScanLeavesNoRows(new BillCreate(a.id(),"5",key(),code.code()));
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(500);
        assertThat(b.walletPaidCents()).isZero();
    }
    @Test void simultaneousScanCommandsBindOnlyOneBill() throws Exception {
        var a=open();var code=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id()));
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> jobs=new ArrayList<>();
            for(int i=0;i<2;i++)jobs.add(pool.submit(()->{start.await();try{tx.execute(s->checkout.create(scope,actor,new BillCreate(a.id(),"5",key(),code.code())));return true;}catch(WalletException e){return false;}}));
            start.countDown();int successes=0;for(var job:jobs)if(job.get(15,TimeUnit.SECONDS))successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(count("wallet_bills")).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from wallet_commands where tenant_id=? and operation='bill.create'",Integer.class,tenant)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void wrongAccountAndTenantScanCannotLeaveOrders() {
        var a=open();var code=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id()));
        assertRejectedScanLeavesNoRows(new BillCreate(UUID.randomUUID(),"5",key(),code.code()));
        var otherCustomer=UUID.randomUUID();
        jdbc.update("insert into customers(id,tenant_id,customer_code,customer_type,display_name,status) values(?,?,'other','regular','其他顾客','active')",otherCustomer,tenant);
        var other=tx.execute(s->accounts.open(scope,accounts.requireProgram(scope,true),otherCustomer));
        assertRejectedScanLeavesNoRows(new BillCreate(other.id(),"5",key(),code.code()));
        var strangerScope=new StoreScope(new TenantId(UUID.randomUUID()),store);
        assertThatThrownBy(()->tx.execute(s->checkout.create(strangerScope,actor,new BillCreate(a.id(),"5",key(),code.code())))).isInstanceOf(WalletException.class);
        assertThat(count("wallet_bills")).isZero();
        jdbc.update("update wallet_payment_codes set code_hash=? where tenant_id=?",WalletTokens.hash("WP.other"),tenant);
        assertRejectedScanLeavesNoRows(new BillCreate(a.id(),"5",key(),code.code()));
    }
    @Test void scanCreationPreservesExistingManualCommandReplayAndRejectsPermissionsAndCommercialDenials() {
        var a=open();String idempotency=key();
        var request=Map.of("accountId",a.id(),"amount","5","idempotencyKey",idempotency);
        Bill old=tx.execute(s->commands.run(scope,actor.actorId(),"bill.create",idempotency,request,Bill.class,()->bills.create(scope,a,500,actor.actorId())));
        Bill replay=tx.execute(s->checkout.create(scope,actor,new BillCreate(a.id(),"5",idempotency)));
        assertThat(replay).isEqualTo(old);
        var code=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id()));
        var noPermission=new CurrentActor(tenant,actorId,"staff",Set.of("store_staff"),Set.of("wallet.view"),Set.of(store));
        assertThatThrownBy(()->tx.execute(s->checkout.create(scope,noPermission,new BillCreate(a.id(),"5",key(),code.code())))).isInstanceOf(WalletException.class);
        when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,"wallet.charge.create",AppGateDenyReason.STORE_APP_EXPIRED));
        assertRejectedScanLeavesNoRows(new BillCreate(a.id(),"5",key(),code.code()));
        assertThat(count("wallet_bills")).isEqualTo(1);
    }
    @Test void transactionFailureAfterScanRollsBackBindingBillAndCommand() {
        var a=open();var code=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id()));
        var c=new BillCreate(a.id(),"5",key(),code.code());
        assertThatThrownBy(()->tx.execute(s->{checkout.create(scope,actor,c);throw new IllegalStateException("after bind");})).isInstanceOf(IllegalStateException.class);
        assertThat(count("wallet_bills")).isZero();
        assertThat(jdbc.queryForObject("select bill_id is null from wallet_payment_codes where tenant_id=?",Boolean.class,tenant)).isTrue();
        Bill retry=tx.execute(s->checkout.create(scope,actor,c));assertThat(retry).isNotNull();
    }
    int count(String table){return jdbc.queryForObject("select count(*) from "+table+" where tenant_id=?",Integer.class,tenant);}
    void assertRejectedScanLeavesNoRows(BillCreate c){
        int beforeBills=count("wallet_bills"),beforeAudit=count("wallet_audit"),beforeCommands=count("wallet_commands");
        assertThatThrownBy(()->tx.execute(s->checkout.create(scope,actor,c))).isInstanceOf(WalletException.class);
        assertThat(count("wallet_bills")).isEqualTo(beforeBills);assertThat(count("wallet_audit")).isEqualTo(beforeAudit);assertThat(count("wallet_commands")).isEqualTo(beforeCommands);
    }
    @Test void refundDoesNotReopenPaidBillAndCannotExceedOriginalPayment() {
        var a=credit(open(),"50","cash");var b=pay(bill(a,"80")).bill();tx.execute(s->checkout.collect(scope,actor,b.id(),confirm("30","paynow",key())));
        var refund=tx.execute(s->refunds.create(scope,actor,new RefundCreate("bill",b.id(),"80","取消消费",key())));
        assertThat(refund.refund().walletCents()).isEqualTo(5000);
        for(var part:refund.parts())tx.execute(s->refunds.confirmPart(scope,actor,refund.refund().id(),part.id(),"refund-bank-ref",key()));
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(5000);
        assertThat(bills.get(scope,b.id(),false).status()).isEqualTo("paid");
        assertThatThrownBy(()->tx.execute(s->refunds.create(scope,actor,new RefundCreate("bill",b.id(),"0.01","重复退",key())))).isInstanceOf(WalletException.class);
    }
    @Test void wrongCustomerTenantAndMissingPermissionCannotMoveMoney() {
        var a=credit(open(),"50","cash");var b=bill(a,"20");
        var stranger=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),UUID.randomUUID(),"other@example.com","other");
        assertThatThrownBy(()->tx.execute(s->authorization.quote(scope,stranger,b.id()))).isInstanceOf(WalletException.class);
        var noPermission=new CurrentActor(tenant,actorId,"staff",Set.of("store_staff"),Set.of("wallet.view"),Set.of(store));
        assertThatThrownBy(()->tx.execute(s->topups.create(scope,noPermission,new TopupCreate(a.id(),"1","cash",key())))).isInstanceOf(WalletException.class);
    }
    @Test void immutableLedgerAndSharedCustomerIdentityAreProtectedInDatabase() {
        var a=credit(open(),"50","cash");
        assertThatThrownBy(()->jdbc.update("update wallet_entries set amount_cents=1 where tenant_id=?",tenant)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update customers set status='archived' where id=?",customer)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(5000);
    }

    @Test void selectedStoreBillingPreservesSiblingPeriodsAndBindsIdempotencyToTheSelection() {
        var subscriptionRepo=new com.rpb.reservation.platformbilling.persistence.JdbcProductSubscriptionRepository(jdbc);
        var eventRepo=new com.rpb.reservation.platformbilling.persistence.JdbcProductSubscriptionEventRepository(jdbc);
        var itemRepo=new com.rpb.reservation.platformbilling.persistence.JdbcProductSubscriptionItemRepository(jdbc);
        var quoteService=new com.rpb.reservation.platformbilling.application.SubscriptionQuoteService(new com.rpb.reservation.platformbilling.persistence.JdbcPlatformProductLinePriceRepository(jdbc,new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc)));
        var billing=new com.rpb.reservation.platformbilling.application.SelectedStoreBillingService(subscriptionRepo,eventRepo,itemRepo,
            new com.rpb.reservation.platformbilling.persistence.JdbcTenantProductEntitlementSyncGateway(jdbc),quoteService,
            new com.rpb.reservation.platformbilling.persistence.SelectedBillingCommandRepository(jdbc,json),
            new com.rpb.reservation.platform.application.SharedStoreIdentityQuery(new com.rpb.reservation.platform.persistence.PlatformTenantStructureRepository(jdbc)),clock);
        var operator=new com.rpb.reservation.platformbilling.application.PlatformBillingOperator(actorId,"platform_admin");
        jdbc.update("update platform_product_line_prices set status='disabled' where app_key='wallet'");
        var purchase=new com.rpb.reservation.platformbilling.application.ProductSubscriptionCommand(key(),"wallet","monthly",null,null,null,"SGD","线下平台收费",1,null,List.of(store));
        assertThatThrownBy(()->tx.execute(s->billing.purchase(tenant,purchase,operator))).isInstanceOf(com.rpb.reservation.platformbilling.application.PlatformBillingServiceException.class);
        jdbc.update("update platform_product_line_prices set status='active',amount=30 where app_key='wallet'");
        assertThatThrownBy(()->tx.execute(s->{billing.purchase(tenant,purchase,operator);throw new IllegalStateException("simulate failure after entitlement sync");})).isInstanceOf(IllegalStateException.class);
        assertThat(subscriptionRepo.findByTenantIdAndAppKey(tenant,"wallet")).isEmpty();assertThat(itemRepo.listByTenantId(tenant)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from tenant_app_entitlements where tenant_id=? and app_key='wallet'",Integer.class,tenant)).isZero();
        var first=tx.execute(s->billing.purchase(tenant,purchase,operator));assertThat(first.subscription().items()).hasSize(1);assertThat(first.quote().finalAmount()).isEqualByComparingTo("30");
        UUID sibling=UUID.randomUUID();jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'sibling','兄弟店','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",sibling,tenant,issuer);
        var add=new com.rpb.reservation.platformbilling.application.ProductSubscriptionCommand(key(),"wallet","monthly",null,null,null,"SGD",null,1,first.subscription().version(),List.of(sibling));
        var added=tx.execute(s->billing.addStores(tenant,first.subscription().id(),add,operator));assertThat(added.subscription().items()).hasSize(2);
        var siblingBefore=added.subscription().items().stream().filter(i->i.storeId().equals(sibling)).findFirst().orElseThrow();
        var renew=new com.rpb.reservation.platformbilling.application.ProductSubscriptionCommand(key(),"wallet","monthly",null,null,null,"SGD",null,1,added.subscription().version(),List.of(store));
        var renewed=tx.execute(s->billing.renew(tenant,first.subscription().id(),renew,operator));
        assertThat(renewed.subscription().items().stream().filter(i->i.storeId().equals(sibling)).findFirst().orElseThrow()).isEqualTo(siblingBefore);
        var item=renewed.subscription().items().stream().filter(i->i.storeId().equals(store)).findFirst().orElseThrow();
        var itemCommand=new com.rpb.reservation.platformbilling.application.ProductSubscriptionCommand(key(),"wallet","monthly",null,null,null,"SGD",null,1,item.version(),null);
        var itemRenewed=tx.execute(s->billing.renewItem(tenant,first.subscription().id(),item.id(),itemCommand,operator));
        var itemReplay=tx.execute(s->billing.renewItem(tenant,first.subscription().id(),item.id(),itemCommand,operator));
        assertThat(itemReplay.subscription()).isEqualTo(itemRenewed.subscription());assertThat(itemReplay.replayed()).isTrue();
        var changedSelection=new com.rpb.reservation.platformbilling.application.ProductSubscriptionCommand(renew.idempotencyKey(),"wallet","monthly",null,null,null,"SGD",null,1,added.subscription().version(),List.of(sibling));
        assertThatThrownBy(()->tx.execute(s->billing.renew(tenant,first.subscription().id(),changedSelection,operator))).isInstanceOf(com.rpb.reservation.platformbilling.application.PlatformBillingServiceException.class);
        assertThat(jdbc.queryForObject("select count(*) from tenant_app_entitlements where tenant_id=? and app_key='payment'",Integer.class,tenant)).isZero();
    }

    @Test void zeroBalanceCreatesNoLedgerEntryAndCannotLaterDeductANewTopupOnTheSameBill() {
        var a=open();var b=bill(a,"80");var paid=pay(b);assertThat(paid.bill().walletApplied()).isTrue();assertThat(paid.bill().remainingCents()).isEqualTo(8000);assertThat(ledger.entries(scope,a.id(),0)).isEmpty();
        credit(a,"50","cash");assertThatThrownBy(()->pay(b)).isInstanceOf(WalletException.class);assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(5000);
    }
    @Test void transactionFailureRollsBackBalanceReceiptLedgerAndReplaySnapshotTogether() {
        var a=open();var t=create(a,"50","paynow");var c=confirm("50","paynow","rollback-ref");
        assertThatThrownBy(()->tx.execute(s->{topups.confirm(scope,actor,t.id(),c);throw new IllegalStateException("simulated failure");})).isInstanceOf(IllegalStateException.class);
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isZero();assertThat(ledger.entries(scope,a.id(),0)).isEmpty();assertThat(topupRepo.get(scope,t.id(),false).status()).isEqualTo("pending_collection");
        tx.execute(s->topups.confirm(scope,actor,t.id(),c));assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(5000);
    }
    @Test void topupRefundReservesFundsAndCancellationRestoresAvailability() {
        var a=credit(open(),"50","cash");var t=topupRepo.list(scope).getFirst();
        var r=tx.execute(s->refunds.create(scope,actor,new RefundCreate("top_up",t.id(),"30","错充",key())));
        assertThat(accounts.account(scope,a.id(),false).availableCents()).isEqualTo(2000);
        var b=pay(bill(a,"40"));assertThat(b.bill().walletPaidCents()).isEqualTo(2000);
        tx.execute(s->refunds.cancel(scope,actor,r.refund().id(),key()));assertThat(accounts.account(scope,a.id(),false).availableCents()).isEqualTo(3000);
    }
    @Test void expiryStopsNewTransactionsButAllowsOriginalReceiptRecoveryAndCustomerReads() {
        var a=open();var t=create(a,"50","cash");
        when(gate.evaluateCommercial(any(),any(),any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.STORE_APP_EXPIRED));
        when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.STORE_APP_EXPIRED));
        assertThatThrownBy(()->create(a,"10","cash")).isInstanceOf(WalletException.class);
        tx.execute(s->topups.confirm(scope,actor,t.id(),confirm("50","cash",key())));
        assertThat(accountService.ownAccounts(scope,principal).getFirst().balanceCents()).isEqualTo(5000);
        assertThatThrownBy(()->tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id()))).isInstanceOf(WalletException.class);
    }
    @Test void tamperedBalanceCannotBeUsedToSpendUnbackedMoney() {
        var a=credit(open(),"50","cash");var b=bill(a,"80");
        assertThatThrownBy(()->jdbc.update("update wallet_accounts set balance_cents=6000 where id=?",a.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update wallet_accounts set balance_cents=6000,principal_balance_cents=6000 where id=?",a.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(5000);
        assertThat(bills.get(scope,b.id(),false).walletPaidCents()).isZero();
    }

    @Test void sharedCustomerArchiveIsRejectedWithAnExplicitDomainReason() {
        open();var guard=new com.rpb.reservation.wallet.integration.WalletCustomerLifecycleGuard(accounts);
        assertThatThrownBy(()->guard.beforeArchive(scope.tenantScope(),new com.rpb.reservation.customer.value.CustomerId(customer)))
            .isInstanceOf(com.rpb.reservation.customer.application.CustomerManagementException.class)
            .extracting(e->((com.rpb.reservation.customer.application.CustomerManagementException)e).code()).isEqualTo(com.rpb.reservation.customer.application.CustomerManagementError.CUSTOMER_HAS_RETAINED_ASSETS);
    }

    @Test void reconciliationSeparatesCreditsPaymentsRefundExecutionAndCurrentIssuerBalance() {
        var a=credit(open(),"50","cash");var b=pay(bill(a,"80")).bill();tx.execute(s->checkout.collect(scope,actor,b.id(),confirm("30","credit_card",key())));
        var r=tx.execute(s->refunds.create(scope,actor,new RefundCreate("bill",b.id(),"20","部分退款",key())));
        var reportService=new WalletReconciliationService(access,directory,accounts,new WalletReconciliationRepository(jdbc,new WalletCollectionRepository(jdbc)));
        // DB timestamps are the actual local clock, not the deterministic authorization clock.
        var date=LocalDate.now(ZoneId.of("Asia/Singapore"));var before=reportService.query(scope,actor,date,date,"issuer");
        assertThat(before.topupCashCents()).isEqualTo(5000);assertThat(before.creditedTopupCents()).isEqualTo(5000);assertThat(before.walletSpentCents()).isEqualTo(5000);assertThat(before.externalBillCents()).isEqualTo(3000);
        assertThat(before.refundsCents()).isEqualTo(1250);assertThat(before.pendingRefundCents()).isEqualTo(750);assertThat(before.programBalanceCents()).isEqualTo(1250);assertThat(before.ledgerMismatchAccounts()).isZero();
        for(var part:r.parts())tx.execute(s->refunds.confirmPart(scope,actor,r.refund().id(),part.id(),"report-refund",key()));
        var after=reportService.query(scope,actor,date,date,"store");assertThat(after.refundsCents()).isEqualTo(2000);assertThat(after.pendingRefundCents()).isZero();
        assertThat(reportService.query(scope,actor,date.minusYears(1),date.minusYears(1)).collections()).isEmpty();
    }

    @Test void simultaneousConfirmationsAndSupplementsRecordOnlyOneActualCollection() throws Exception {
        var a=open();var t=create(a,"50","cash");var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var one=pool.submit(()->{start.await();return tx.execute(s->topups.confirm(scope,actor,t.id(),confirm("50","cash","same-receipt")));});
            var two=pool.submit(()->{start.await();return tx.execute(s->topups.confirm(scope,actor,t.id(),confirm("50","cash","same-receipt")));});
            start.countDown();one.get(15,TimeUnit.SECONDS);two.get(15,TimeUnit.SECONDS);assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(5000);assertThat(ledger.entries(scope,a.id(),0)).hasSize(1);
            var b=pay(bill(a,"80")).bill();var next=new CountDownLatch(1);List<Future<Boolean>> jobs=new ArrayList<>();
            for(int i=0;i<2;i++)jobs.add(pool.submit(()->{next.await();try{tx.execute(s->checkout.collect(scope,actor,b.id(),confirm("30","paynow",key())));return true;}catch(WalletException e){return false;}}));
            next.countDown();int success=0;for(var job:jobs)if(job.get(15,TimeUnit.SECONDS))success++;assertThat(success).isEqualTo(1);assertThat(bills.get(scope,b.id(),false).remainingCents()).isZero();
        } finally {pool.shutdownNow();}
    }

    @Test void oneExternalRefundReferenceCannotReleaseTwoDifferentRefunds() {
        var a=credit(open(),"50","paynow");var firstTopup=topupRepo.list(scope).getFirst();credit(a,"50","paynow");var secondTopup=topupRepo.list(scope).stream().filter(t->!t.id().equals(firstTopup.id())).findFirst().orElseThrow();
        var first=tx.execute(s->refunds.create(scope,actor,new RefundCreate("top_up",firstTopup.id(),"10","原单退回",key())));
        tx.execute(s->refunds.confirmPart(scope,actor,first.refund().id(),first.parts().getFirst().id(),"ONE-BANK-REFUND",key()));
        var second=tx.execute(s->refunds.create(scope,actor,new RefundCreate("top_up",secondTopup.id(),"10","原单退回",key())));
        assertThatThrownBy(()->tx.execute(s->refunds.confirmPart(scope,actor,second.refund().id(),second.parts().getFirst().id(),"ONE-BANK-REFUND",key()))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(9000);assertThat(accounts.account(scope,a.id(),false).reservedCents()).isEqualTo(1000);assertThat(refundRepo.parts(scope,second.refund().id()).getFirst().completed()).isFalse();
    }

    @Test void lateOrOverpaidOfflineReceiptsAreRecordedAndRefundedWithoutCreditingWallet() {
        var a=open();var t=create(a,"50","cash");tx.execute(s->topups.cancel(scope,actor,t.id(),key()));
        var exceptionService=new WalletCollectionExceptionService(accounts,topupRepo,bills,new WalletCollectionService(new WalletCollectionRepository(jdbc),collectionPort),new WalletCollectionRepository(jdbc),commands,access);
        var recorded=tx.execute(s->exceptionService.record(scope,actor,new CollectionException("top_up",t.id(),confirm("55","cash","late-cash"),"取消后发现超额实收",key())));
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isZero();assertThat(topupRepo.get(scope,t.id(),false).status()).isEqualTo("exception");
        var refund=tx.execute(s->refunds.create(scope,actor,new RefundCreate("collection_exception",recorded.id(),"55","退还迟到实收",key())));
        for(var part:refund.parts())tx.execute(s->refunds.confirmPart(scope,actor,refund.refund().id(),part.id(),"returned-cash",key()));
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isZero();assertThat(ledger.entries(scope,a.id(),0)).isEmpty();
        assertThat(topupRepo.get(scope,t.id(),false).status()).isEqualTo("cancelled");
        assertThatThrownBy(()->tx.execute(s->refunds.create(scope,actor,new RefundCreate("collection_exception",recorded.id(),"0.01","重复",key())))).isInstanceOf(WalletException.class);
    }

    @Test void sameIssuerSharesOneAccountAndOtherIssuersOrTenantsCannotUseIt() {
        var a=credit(open(),"50","cash");UUID sibling=UUID.randomUUID(),otherIssuer=UUID.randomUUID(),otherStore=UUID.randomUUID();
        jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'other','另一主体','active')",otherIssuer,tenant);
        for(UUID id:List.of(sibling,otherStore))jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,?,'门店','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",id,tenant,id.equals(sibling)?issuer:otherIssuer,id.toString());
        var siblingScope=new StoreScope(new TenantId(tenant),sibling);var otherScope=new StoreScope(new TenantId(tenant),otherStore);
        tx.execute(s->accounts.configure(siblingScope,issuer,List.of("cash"),null));tx.execute(s->accounts.configure(otherScope,otherIssuer,List.of("cash"),null));
        assertThat(accounts.customerAccounts(siblingScope,customer).getFirst().id()).isEqualTo(a.id());assertThat(accounts.account(siblingScope,a.id(),false).balanceCents()).isEqualTo(5000);
        var code=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id(),"topup"));
        var allStores=new CurrentActor(tenant,actorId,"staff",actor.roles(),actor.permissions(),Set.of(store,sibling,otherStore));
        assertThat(authorization.resolve(siblingScope,allStores,code.code(),"topup").id()).isEqualTo(a.id());
        var personal=tx.execute(s->identities(clock).issue(scope,principal));
        assertThat(identities(clock).resolve(siblingScope,allStores,personal.code()).id()).isEqualTo(customer);
        assertThatThrownBy(()->identities(clock).resolve(otherScope,allStores,personal.code())).isInstanceOf(WalletException.class);

        assertThatThrownBy(()->authorization.resolve(otherScope,allStores,code.code(),"topup")).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->authorization.resolve(siblingScope,actor,code.code(),"topup")).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->accounts.account(otherScope,a.id(),false)).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->accounts.account(new StoreScope(new TenantId(UUID.randomUUID()),store),a.id(),false)).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->jdbc.update("insert into wallet_program_stores(program_id,tenant_id,store_id,operating_entity_id) values(?,?,?,?)",a.programId(),tenant,otherStore,issuer)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void scanChargeSharesOnlyTheSameIssuerAndReturnsTheExactOtherStoreReceiptToItsOwner() {
        var a=credit(open(),"50","cash");var sibling=UUID.randomUUID();var otherStore=UUID.randomUUID();var otherIssuer=UUID.randomUUID();
        jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,'scan-other','另一主体','active')",otherIssuer,tenant);
        for(var id:List.of(sibling,otherStore))jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,?,?,'active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",id,tenant,id.equals(sibling)?issuer:otherIssuer,id.toString(),"门店");
        var siblingScope=new StoreScope(scope.tenantId(),sibling);var otherScope=new StoreScope(scope.tenantId(),otherStore);
        tx.execute(s->accounts.configure(siblingScope,issuer,List.of("cash"),null));tx.execute(s->accounts.configure(otherScope,otherIssuer,List.of("cash"),null));
        var allStores=new CurrentActor(tenant,actorId,"tenant_admin",actor.roles(),actor.permissions(),Set.of(store,sibling,otherStore));var code=chargeCode(a);
        assertThatThrownBy(()->tx.execute(s->scanner(clock).charge(otherScope,allStores,charge(a,"20",code.code())))).isInstanceOf(WalletException.class);
        noScanEffects(a,5000,0);
        var result=tx.execute(s->scanner(clock).charge(siblingScope,allStores,charge(a,"20",code.code())));
        assertThat(result.bill().storeId()).isEqualTo(sibling);assertThat(result.account().id()).isEqualTo(a.id());assertThat(result.account().balanceCents()).isEqualTo(3000);
        assertThat(scanner(clock).status(scope,principal,code.code()).bill()).isEqualTo(result.bill());
    }

    @Test void expiredQrAndQuotesFailButSuccessfulPaymentReplayKeepsOriginalResult() {
        var a=credit(open(),"50","cash");var b=bill(a,"20");var code=tx.execute(s->authorization.issuePaymentCode(scope,principal,a.id()));var quote=tx.execute(s->authorization.quote(scope,principal,b.id()));
        var later=new WalletAuthorizationService(accounts,ledger,commands,bills,auths,access,Clock.offset(clock,Duration.ofMinutes(2)),funding);
        assertThatThrownBy(()->tx.execute(s->later.bind(scope,actor,b.id(),code.code()))).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->tx.execute(s->later.pay(scope,principal,b.id(),new Pay(quote.id(),key())))).isInstanceOf(WalletException.class);
        var command=new Pay(quote.id(),key());var paid=tx.execute(s->authorization.pay(scope,principal,b.id(),command));
        Paid replay=tx.execute(s->later.pay(scope,principal,b.id(),command));assertThat(replay).isEqualTo(paid);assertThat(ledger.entries(scope,a.id(),0)).hasSize(2);
    }

    @Test void walletOnlyTenantConfiguresTheSameStorePaynowProfileWithoutPaymentGrantAndPreservesItsOtherSettings() {
        var profileRepo=new com.rpb.reservation.payment.persistence.JdbcPaymentMethodProfileRepository(jdbc);
        var profileService=new com.rpb.reservation.payment.application.PaymentMethodProfileService(profileRepo,new com.rpb.reservation.payment.provider.PayNowQrPayloadBuilder());
        var offline=new com.rpb.reservation.payment.application.OfflinePayNowCollectionService(profileService,new com.rpb.reservation.payment.provider.PayNowQrPayloadBuilder());
        var adapter=new com.rpb.reservation.wallet.integration.WalletOfflineCollectionAdapter(offline,new com.rpb.reservation.payment.application.OfflineReceiptRegistry(new com.rpb.reservation.payment.persistence.OfflineReceiptClaimRepository(jdbc)));
        var service=new WalletCollectionSettingsService(access,adapter,commands,gate);
        when(gate.evaluateCommercial(eq("payment"),any(),any())).thenReturn(AppGateDecision.deny("payment",tenant,store,null,AppGateDenyReason.TENANT_APP_NOT_ENABLED));
        jdbc.update("insert into payment_method_profiles(tenant_id,method,status,paynow_type,paynow_uen,merchant_name,config_json) values(?,'paynow','active','uen','TESTONLY01','Test Merchant','{\"keepQuickPay\":42}'::jsonb)",tenant);
        assertThat(adapter.settings(scope).inherited()).isTrue();assertThat(adapter.paynowConfigured(scope)).isFalse();
        var command=new CollectionSettingsUpdate(null,"active","uen",null,"TESTONLY01","Test Merchant",true,key());
        var saved=tx.execute(s->service.save(scope,actor,command));assertThat(saved.version()).isZero();assertThat(saved.inherited()).isFalse();assertThat(adapter.paynowConfigured(scope)).isTrue();
        assertThat(profileRepo.findStoreProfile(scope,"paynow").orElseThrow().configJson()).contains("keepQuickPay");
        assertThat(adapter.paynowQr(scope,100,UUID.randomUUID())).startsWith("000201");assertThat(jdbc.queryForObject("select count(*) from tenant_app_entitlements where tenant_id=? and app_key='payment'",Integer.class,tenant)).isZero();
        assertThat(adapter.canonicalAccount(scope,"paynow","TESTONLY01","LOCAL")).isEqualTo("paynow:uen:TESTONLY01");
        assertThatThrownBy(()->adapter.canonicalAccount(scope,"paynow","WRONG","LOCAL")).isInstanceOf(WalletException.class);
        jdbc.update("update payment_method_profiles set status='disabled' where tenant_id=?",tenant);
        assertThat(adapter.paynowConfigured(scope)).isFalse();assertThat(adapter.canonicalRecoveryAccount(scope,"paynow","OLDUEN01","LOCAL")).isEqualTo("paynow:uen:OLDUEN01");
        assertThatThrownBy(()->tx.execute(s->service.save(scope,actor,new CollectionSettingsUpdate(null,"active","uen",null,"TESTONLY01","Other",true,key())))).isInstanceOf(WalletException.class);
        when(gate.evaluateCommercial(eq("payment"),any(),any())).thenReturn(AppGateDecision.allow("payment",tenant,store,null));
        assertThatThrownBy(()->tx.execute(s->service.save(scope,actor,new CollectionSettingsUpdate(saved.version(),"active","uen",null,"TESTONLY01","Other",true,key())))).isInstanceOf(WalletException.class);
    }

    @Test void concurrentStoresUnderOneIssuerCannotSpendTheSameAvailableBalanceTwice() throws Exception {
        var a=credit(open(),"50","cash");UUID sibling=UUID.randomUUID();
        jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'concurrent','并发兄弟店','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",sibling,tenant,issuer);
        var siblingScope=new StoreScope(new TenantId(tenant),sibling);when(directory.store(siblingScope)).thenReturn(new StoreIdentity(tenant,sibling,issuer,"发行主体","并发兄弟店","test","Asia/Singapore","zh-CN",null));
        tx.execute(s->accounts.configure(siblingScope,issuer,List.of("cash"),null));
        var both=new CurrentActor(tenant,actorId,"tenant_admin",actor.roles(),actor.permissions(),Set.of(store,sibling));
        var first=bill(a,"40");var second=tx.execute(s->checkout.create(siblingScope,both,new BillCreate(a.id(),"40",key())));
        var q1=tx.execute(s->authorization.quote(scope,principal,first.id()));var q2=tx.execute(s->authorization.quote(siblingScope,principal,second.id()));
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> jobs=new ArrayList<>();for(var q:List.of(q1,q2))jobs.add(pool.submit(()->{start.await();var target=q.id().equals(q1.id())?scope:siblingScope;try{tx.execute(s->authorization.pay(target,principal,q.billId(),new Pay(q.id(),key())));return true;}catch(WalletException e){return false;}}));
            start.countDown();int success=0;for(var job:jobs)if(job.get(15,TimeUnit.SECONDS))success++;assertThat(success).isEqualTo(1);assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(1000);
        } finally {pool.shutdownNow();}
    }
    Topup gift(Account a,String principal,String gift,String policy) {
        var t=create(a,principal,"cash");
        jdbc.update("update wallet_topups set bonus_cents=?,deduction_policy=? where id=?",WalletMoney.cents(gift),policy,t.id());
        tx.execute(s->topups.confirm(scope,actor,t.id(),confirm(principal,"cash","")));
        return topupRepo.get(scope,t.id(),false);
    }
    void balances(UUID id,long p,long g,long rp,long rg) {
        var a=accounts.account(scope,id,false);assertThat(a.principalBalanceCents()).isEqualTo(p);assertThat(a.bonusBalanceCents()).isEqualTo(g);
        assertThat(a.principalReservedCents()).isEqualTo(rp);assertThat(a.bonusReservedCents()).isEqualTo(rg);assertThat(a.balanceCents()).isEqualTo(p+g);
    }
    @Test void campaignProportionalDebitAndPartialRefundRestoreOriginalComponents() {
        var a=open();gift(a,"100","10","proportional");var paid=pay(bill(a,"55"));
        assertThat(paid.bill().principalPaidCents()).isEqualTo(5000);assertThat(paid.bill().bonusPaidCents()).isEqualTo(500);balances(a.id(),5000,500,0,0);
        var r=tx.execute(s->refunds.create(scope,actor,new RefundCreate("bill",paid.bill().id(),"11","部分退货",key())));
        assertThat(r.refund().principalCents()).isEqualTo(1000);assertThat(r.refund().bonusCents()).isEqualTo(100);balances(a.id(),6000,600,0,0);
        tx.execute(s->refunds.create(scope,actor,new RefundCreate("bill",paid.bill().id(),"44","剩余退货",key())));balances(a.id(),10000,1000,0,0);
    }
    @Test void principalFirstAndFifoPreserveEachOriginalLotPolicy() {
        var a=open();gift(a,"100","10","principal_first");gift(a,"300","50","proportional");var paid=pay(bill(a,"165"));
        assertThat(paid.bill().principalPaidCents()).isEqualTo(14714);assertThat(paid.bill().bonusPaidCents()).isEqualTo(1786);balances(a.id(),25286,4214,0,0);
        tx.execute(s->refunds.create(scope,actor,new RefundCreate("bill",paid.bill().id(),"165","全部退货",key())));balances(a.id(),40000,6000,0,0);
    }
    @Test void promotionalExitFreezesBothComponentsCancellationAndCompletionAreAtomic() {
        var a=open();var t=gift(a,"100","10","proportional");pay(bill(a,"55"));
        assertThatThrownBy(()->tx.execute(s->refunds.create(scope,actor,new RefundCreate("top_up",t.id(),"10","不可任意退",key())))).isInstanceOf(WalletException.class);
        var command=new RefundCreate("top_up",t.id(),null,"退出充值",key(),"remaining_lot_exit");
        var r=tx.execute(s->refunds.create(scope,actor,command));assertThat(r.refund().amountCents()).isEqualTo(5000);assertThat(r.refund().walletCents()).isEqualTo(5500);balances(a.id(),5000,500,5000,500);
        tx.execute(s->refunds.cancel(scope,actor,r.refund().id(),key()));balances(a.id(),5000,500,0,0);
        var again=tx.execute(s->refunds.create(scope,actor,new RefundCreate("top_up",t.id(),null,"重新退出",key(),"remaining_lot_exit")));
        tx.execute(s->refunds.confirmPart(scope,actor,again.refund().id(),again.parts().getFirst().id(),"现金已退",key()));balances(a.id(),0,0,0,0);
        assertThat(topupRepo.get(scope,t.id(),false).refundedCents()).isEqualTo(5000);
    }
    @Test void originalBillRefundAfterLotExitRestoresOnlyConsumedRights() {
        var a=open();var t=gift(a,"100","10","proportional");var paid=pay(bill(a,"55"));
        var r=tx.execute(s->refunds.create(scope,actor,new RefundCreate("top_up",t.id(),null,"退出",key(),"remaining_lot_exit")));
        tx.execute(s->refunds.confirmPart(scope,actor,r.refund().id(),r.parts().getFirst().id(),"退款完成",key()));
        tx.execute(s->refunds.create(scope,actor,new RefundCreate("bill",paid.bill().id(),"55","原消费退回",key())));balances(a.id(),5000,500,0,0);
    }
    @Test void bonusOnlyExitDoesNotPretendToReturnCashAndDoesNotBlockConsumedBonusRefund() {
        var a=open();gift(a,"100","10","principal_first");var paid=pay(bill(a,"100"));balances(a.id(),0,1000,0,0);
        var lot=funding.lots(scope,accounts.account(scope,a.id(),false)).getFirst();
        tx.execute(s->funding.exitBonus(scope,accounts.account(scope,a.id(),true),lot.id(),"退出剩余赠送",actorId));balances(a.id(),0,0,0,0);
        tx.execute(s->refunds.create(scope,actor,new RefundCreate("bill",paid.bill().id(),"100","退消费本金",key())));balances(a.id(),10000,0,0,0);
        assertThat(jdbc.queryForObject("select count(*) from wallet_refunds where tenant_id=? and topup_id is not null",Integer.class,tenant)).isZero();
    }
    @Test void stalePreviewCannotReserveChangedComponentsAndOldCashCannotUseNewBonus() {
        var a=credit(open(),"10","cash");var old=topupRepo.list(scope,new WalletHistoryQuery(0,"all")).getFirst();gift(a,"100","10","proportional");var version=accounts.account(scope,a.id(),false).version();pay(bill(a,"10"));
        assertThatThrownBy(()->tx.execute(s->refunds.create(scope,actor,new RefundCreate("top_up",old.id(),"10","余额已变",key(),null,version)))).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->tx.execute(s->refunds.create(scope,actor,new RefundCreate("top_up",old.id(),"10","旧充值已消耗",key())))).isInstanceOf(WalletException.class);
        balances(a.id(),10000,1000,0,0);
    }

    @Test void mixedTenderRefundPreviewExactlyMatchesCommittedWalletAndCashSplit() {
        var a=open();gift(a,"100","10","proportional");var paid=pay(bill(a,"220"));
        tx.execute(s->checkout.collect(scope,actor,paid.bill().id(),confirm("110","cash","mixed-cash-receipt")));
        var operations=new WalletFundingOperations(access,accounts,topupRepo,bills,refundRepo,funding,commands);
        var preview=tx.execute(s->operations.refundPreview(scope,actor,new WalletFundingOperations.RefundPreviewRequest("bill",paid.bill().id(),"22",null)));
        assertThat(preview.walletCents()).isEqualTo(1100);assertThat(preview.principalReturnCents()).isEqualTo(1000);assertThat(preview.bonusReturnCents()).isEqualTo(100);assertThat(preview.externalRefundCents()).isEqualTo(1100);
        var r=tx.execute(s->refunds.create(scope,actor,new RefundCreate("bill",paid.bill().id(),"22","混合收款退回",key(),null,preview.accountVersion())));
        assertThat(r.refund().walletCents()).isEqualTo(preview.walletCents());assertThat(r.refund().principalCents()).isEqualTo(preview.principalReturnCents());assertThat(r.refund().bonusCents()).isEqualTo(preview.bonusReturnCents());assertThat(r.parts().stream().mapToLong(RefundPart::amountCents).sum()).isEqualTo(preview.externalRefundCents());
    }

}
