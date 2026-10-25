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
    Map<String,String> folder(String category,String direction){var q=new HashMap<>(all());q.put("category",category);q.put("direction",direction);return q;}
    static byte[] proofImage(int size){try{var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(size,size,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);return bytes.toByteArray();}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    @Test void customerBillProofPreservesPartialPaymentAndRespectsExpiredProductQrRules() {
        var a=open();var b=bill(a,"20");
        var ocr=mock(com.rpb.reservation.payment.application.PaymentProofOcrAdapter.class);
        when(ocr.extract(any(),any())).thenReturn(new com.rpb.reservation.payment.application.PaymentProofOcrFields(null,new java.math.BigDecimal("10.00"),null,"ocbc",true,java.math.BigDecimal.ONE,"Payment successful\nMessage: "+WalletPayNowReference.forSource(b.id())+"\nTransaction ID: BILL-CUSTOMER-123\nUEN: 202012345A", "{}"));
        when(collectionPort.settings(scope)).thenReturn(new CollectionSettings(1,false,"enabled","uen",null,"202012345A","Shop"));
        when(collectionPort.canonicalAccount(any(),any(),any(),any())).thenReturn("paynow:uen:202012345A");
        when(collectionPort.paynowQr(any(),anyLong(),any())).thenReturn("QR");
        var repo=new com.rpb.reservation.wallet.proof.WalletCollectionProofRepository(jdbc,json);var inspector=new com.rpb.reservation.payment.application.PaymentProofImageInspector(ocr,null);
        var service=new com.rpb.reservation.wallet.proof.WalletCustomerProofService(repo,access,accounts,topupRepo,bills,collectionPort,commands,inspector);
        var staff=new com.rpb.reservation.wallet.proof.WalletCollectionProofService(repo,access,topupRepo,bills,collectionPort,inspector,json);
        var collection=new WalletCollectionService(new WalletCollectionRepository(jdbc),collectionPort,staff);
        var staffBills=new WalletCheckoutService(accounts,commands,bills,collection,access,auths,clock);
        var pendingTopup=create(a,"10","paynow");
        when(gate.evaluateCommercial(any(),any(),any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.TENANT_APP_EXPIRED));
        assertThatThrownBy(()->service.paynow(scope,principal,"bill",b.id(),"paynow")).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->service.paynow(scope,principal,"topup",pendingTopup.id(),"paynow")).isInstanceOf(WalletException.class);
        // An existing receipt can still be declared; this does not solicit or credit new funds.
        var e=tx.execute(st->service.submit(scope,principal,"bill",b.id(),"paynow","10",key(),"receipt.png","image/png",proofImage(5)));
        assertThat(e.status()).isEqualTo("waiting_confirmation");assertThat(bills.get(scope,b.id(),false).externalPaidCents()).isZero();
        assertThatThrownBy(()->tx.execute(st->staffBills.collect(scope,actor,b.id(),new CollectionConfirm("paynow","10","202012345A","BILL-CUSTOMER-123","T1","checked",true,key(),e.id())))).isInstanceOf(WalletException.class);
        assertThat(repo.get(scope,e.id(),false).collectionId()).isNull();
        when(gate.evaluateCommercial(any(),any(),any())).thenReturn(AppGateDecision.allow("wallet",tenant,store,null));
        tx.execute(st->staffBills.collect(scope,actor,b.id(),confirm("10","cash",key())));
        when(gate.evaluateCommercial(any(),any(),any())).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.TENANT_APP_EXPIRED));
        assertThat(service.paynow(scope,principal,"bill",b.id(),"paynow").get("payload")).isEqualTo("QR");
        tx.execute(st->staffBills.collect(scope,actor,b.id(),new CollectionConfirm("paynow","10","202012345A","BILL-CUSTOMER-123","T1","checked",true,key(),e.id())));
        assertThat(bills.get(scope,b.id(),false).remainingCents()).isZero();assertThat(accounts.account(scope,a.id(),false).balanceCents()).isZero();
        assertThat(service.list(scope,principal,"bill",b.id()).getFirst().status()).isEqualTo("confirmed");
        assertThatThrownBy(()->service.context(scope,principal,"bill",b.id(),"paynow")).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->tx.execute(st->service.submit(scope,principal,"bill",b.id(),"paynow","10",key(),"receipt.png","image/png",proofImage(5)))).isInstanceOf(WalletException.class);
        var closed=bill(a,"10");tx.execute(st->{bills.close(scope,closed.id());return null;});
        assertThatThrownBy(()->tx.execute(st->service.submit(scope,principal,"bill",closed.id(),"paynow","10",key(),"receipt.png","image/png",proofImage(5)))).isInstanceOf(WalletException.class);
    }

    @Test void customerProofStaysPendingPrivateAndCanOnlyBeConfirmedByStaff() {
        var a=open();var topup=create(a,"10","paynow");
        var ocr=mock(com.rpb.reservation.payment.application.PaymentProofOcrAdapter.class);
        when(ocr.extract(any(),any())).thenReturn(new com.rpb.reservation.payment.application.PaymentProofOcrFields(null,new java.math.BigDecimal("10.00"),null,"ocbc",true,java.math.BigDecimal.ONE,"Payment successful\nMessage: "+WalletPayNowReference.forSource(topup.id())+"\nTransaction ID: CUSTOMER-123\nUEN: 202012345A", "{}"));
        when(collectionPort.settings(scope)).thenReturn(new CollectionSettings(1,false,"enabled","uen",null,"202012345A","Shop"));
        when(collectionPort.canonicalAccount(any(),any(),any(),any())).thenReturn("paynow:uen:202012345A");
        when(collectionPort.paynowQr(any(),anyLong(),any())).thenReturn("QR");
        var repo=new com.rpb.reservation.wallet.proof.WalletCollectionProofRepository(jdbc,json);
        var inspector=new com.rpb.reservation.payment.application.PaymentProofImageInspector(ocr,null);
        var service=new com.rpb.reservation.wallet.proof.WalletCustomerProofService(repo,access,accounts,topupRepo,bills,collectionPort,commands,inspector);
        var staff=new com.rpb.reservation.wallet.proof.WalletCollectionProofService(repo,access,topupRepo,bills,collectionPort,inspector,json);
        byte[] bytes=proofImage(4);String uploadKey=key();
        var evidence=tx.execute(st->service.submit(scope,principal,"topup",topup.id(),"paynow","10",uploadKey,"receipt.png","image/png",bytes));
        assertThat(evidence.status()).isEqualTo("waiting_confirmation");
        assertThat(json.valueToTree(evidence).has("confirmation")).isFalse();assertThat(json.valueToTree(evidence).has("extracted")).isFalse();
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isZero();
        assertThat(topupRepo.get(scope,topup.id(),false).customerDeclaredPaidAt()).isNotNull();
        assertThat(topupRepo.get(scope,topup.id(),false).status()).isEqualTo("pending_collection");
        assertThat(jdbc.queryForObject("select count(*) from wallet_collections where tenant_id=?",Long.class,tenant)).isZero();
        assertThat(staff.submitted(scope,actor,"topup",topup.id())).extracting("id").containsExactly(evidence.id());
        var viewer=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of("wallet.view"),Set.of(store));
        assertThat(staff.list(scope,viewer,"topup",topup.id())).extracting("id").containsExactly(evidence.id());
        assertThat(staff.list(scope,viewer,"topup",topup.id()).getFirst().confirmation()).isEmpty();
        assertThat(staff.image(scope,viewer,evidence.id()).bytes()).containsExactly(bytes);
        assertThatThrownBy(()->staff.list(scope,null,"topup",topup.id())).isInstanceOf(WalletException.class);
        var noRights=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of(store));
        assertThatThrownBy(()->staff.list(scope,noRights,"topup",topup.id())).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->staff.image(scope,noRights,evidence.id())).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->staff.list(scope,viewer,"topup",UUID.randomUUID())).isInstanceOf(WalletException.class);
        var foreignTenant=new StoreScope(new TenantId(UUID.randomUUID()),store);
        var foreignStore=new StoreScope(new TenantId(tenant),UUID.randomUUID());
        for(var foreign:List.of(foreignTenant,foreignStore)){
            assertThat(repo.list(foreign,"topup",topup.id())).isEmpty();
            assertThatThrownBy(()->repo.get(foreign,evidence.id(),false)).isInstanceOf(WalletException.class);
            assertThatThrownBy(()->staff.list(foreign,viewer,"topup",topup.id())).isInstanceOf(WalletException.class);
            assertThatThrownBy(()->staff.image(foreign,viewer,evidence.id())).isInstanceOf(WalletException.class);
        }
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from wallet_collections where tenant_id=?",Long.class,tenant)).isZero();
        assertThat(service.image(scope,principal,evidence.id()).bytes()).containsExactly(bytes);
        var stranger=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),UUID.randomUUID(),"other@example.com","Other");
        assertThatThrownBy(()->service.image(scope,stranger,evidence.id())).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->service.list(scope,stranger,"topup",topup.id())).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->tx.execute(st->service.submit(scope,stranger,"topup",topup.id(),"paynow","10",uploadKey,"receipt.png","image/png",bytes))).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->service.context(new StoreScope(new TenantId(UUID.randomUUID()),store),principal,"topup",topup.id(),"paynow")).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->service.context(new StoreScope(new TenantId(tenant),UUID.randomUUID()),principal,"topup",topup.id(),"paynow")).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->tx.execute(st->service.submit(scope,principal,"topup",topup.id(),"paynow","9",uploadKey,"receipt.png","image/png",bytes))).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->tx.execute(st->service.submit(scope,principal,"topup",topup.id(),"paynow","10",key(),"receipt.png","image/png",new byte[]{1,2}))).isInstanceOf(WalletException.class);
        var employeeProof=tx.execute(st->staff.scan(scope,actor,"topup",topup.id(),"paynow","10",key(),"receipt.png","image/png",proofImage(3)));
        assertThatThrownBy(()->service.image(scope,principal,employeeProof.id())).isInstanceOf(WalletException.class);
        assertThat(service.list(scope,principal,"topup",topup.id())).hasSize(1);
        // Staff scan drafts stay private to the confirmation workflow, not transaction history.
        assertThat(staff.list(scope,viewer,"topup",topup.id())).extracting("id").containsExactly(evidence.id());
        var pendingBill=bill(a,"10");
        var billEvidence=tx.execute(st->service.submit(scope,principal,"bill",pendingBill.id(),"paynow","10",key(),"receipt.png","image/png",proofImage(6)));
        assertThat(staff.list(scope,viewer,"bill",pendingBill.id())).extracting("id").containsExactly(billEvidence.id());
        assertThat(staff.image(scope,viewer,billEvidence.id()).bytes()).containsExactly(proofImage(6));
        assertThat(bills.get(scope,pendingBill.id(),false).externalPaidCents()).isZero();
        var collections=new WalletCollectionService(new WalletCollectionRepository(jdbc),collectionPort,staff);
        var staffTopups=new WalletTopupService(accounts,ledger,commands,topupRepo,collections,access,funding,marketing);
        tx.execute(st->staffTopups.confirm(scope,actor,topup.id(),new CollectionConfirm("paynow","10","202012345A","CUSTOMER-123","T1","private employee note",true,key(),evidence.id())));
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(1000);
        assertThat(service.list(scope,principal,"topup",topup.id()).getFirst().status()).isEqualTo("confirmed");
        assertThat(staff.submitted(scope,actor,"topup",topup.id())).isEmpty();
        assertThat(staff.list(scope,viewer,"topup",topup.id())).extracting("id").containsExactly(evidence.id());
        assertThat(staff.list(scope,viewer,"topup",topup.id()).getFirst().confirmation()).containsEntry("verified",true);
        assertThat(tx.execute(st->service.submit(scope,principal,"topup",topup.id(),"paynow","10",uploadKey,"receipt.png","image/png",bytes)).id()).isEqualTo(evidence.id());
        assertThatThrownBy(()->tx.execute(st->service.submit(scope,principal,"topup",topup.id(),"paynow","10",key(),"receipt.png","image/png",bytes))).isInstanceOf(WalletException.class);
        assertThat(jdbc.queryForObject("select count(*) from wallet_audit where tenant_id=? and action='customer.paynow.proof.submit'",Long.class,tenant)).isEqualTo(2);
    }

    @Test void paynowProofScanDoesNotCreditAndConfirmationBindsOnlyMatchingEvidence() {
        var a=open();var topup=create(a,"10","paynow");
        var ocr=mock(com.rpb.reservation.payment.application.PaymentProofOcrAdapter.class);
        String reference=com.rpb.reservation.wallet.domain.WalletPayNowReference.forSource(topup.id());
        when(ocr.extract(any(),any())).thenReturn(new com.rpb.reservation.payment.application.PaymentProofOcrFields(null,new java.math.BigDecimal("10.00"),null,"ocbc",true,java.math.BigDecimal.ONE,"Payment successful\nMessage: "+reference+"\nTransaction ID: BANK-123\nUEN: 202012345A", "{}"));
        when(collectionPort.settings(scope)).thenReturn(new CollectionSettings(1,false,"enabled","uen",null,"202012345A","Shop"));
        when(collectionPort.canonicalAccount(any(),any(),any(),any())).thenReturn("paynow:uen:202012345A");
        var repo=new com.rpb.reservation.wallet.proof.WalletCollectionProofRepository(jdbc,json);
        var proofs=new com.rpb.reservation.wallet.proof.WalletCollectionProofService(repo,access,topupRepo,bills,collectionPort,new com.rpb.reservation.payment.application.PaymentProofImageInspector(ocr,null),json);
        byte[] image=proofImage(2);
        String scanKey=key();var p=tx.execute(s->proofs.scan(scope,actor,"topup",topup.id(),"paynow","10",scanKey,"receipt.png","image/png",image));
        assertThat(p.checks().get("reference")).isEqualTo("match");assertThat(p.bankReference()).isEqualTo("BANK-123");
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isZero();assertThat(jdbc.queryForObject("select count(*) from wallet_collections where tenant_id=?",Long.class,tenant)).isZero();
        assertThat(tx.execute(s->proofs.scan(scope,actor,"topup",topup.id(),"paynow","10",scanKey,"receipt.png","image/png",image)).id()).isEqualTo(p.id());
        assertThatThrownBy(()->tx.execute(s->proofs.scan(scope,actor,"topup",topup.id(),"paynow","9",scanKey,"receipt.png","image/png",image))).isInstanceOf(WalletException.class);
        var collections=new WalletCollectionService(new WalletCollectionRepository(jdbc),collectionPort,proofs);
        var service=new WalletTopupService(accounts,ledger,commands,topupRepo,collections,access,funding,marketing);
        var differentSource=create(a,"10","paynow");
        var crossed=new CollectionConfirm("paynow","10","202012345A","BANK-123","T1","wrong source",true,key(),p.id());
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,actor,differentSource.id(),crossed))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_PROOF_CONFLICT");
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isZero();
        var otherStoreScope=new StoreScope(new TenantId(tenant),UUID.randomUUID());
        assertThatThrownBy(()->repo.get(otherStoreScope,p.id(),false)).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_NOT_FOUND");
        var wrong=new CollectionConfirm("paynow","10","202012345A","OTHER-REF","T1","checked",true,key(),p.id());
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,actor,topup.id(),wrong))).isInstanceOf(WalletException.class);assertThat(accounts.account(scope,a.id(),false).balanceCents()).isZero();
        var confirmation=new CollectionConfirm("paynow","10","202012345A","BANK-123","T1","merchant bank checked",true,key(),p.id());
        tx.execute(s->service.confirm(scope,actor,topup.id(),confirmation));tx.execute(s->service.confirm(scope,actor,topup.id(),confirmation));
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(1000);
        assertThat(proofs.list(scope,actor,"topup",topup.id())).singleElement().satisfies(e->assertThat(e.confirmation().get("bankReference")).isEqualTo("BANK-123"));
        var alien=new CurrentActor(UUID.randomUUID(),actorId,"staff",Set.of(),actor.permissions(),Set.of(store));assertThatThrownBy(()->proofs.image(scope,alien,p.id())).isInstanceOf(WalletException.class);
        assertThat(proofs.image(scope,actor,p.id()).bytes()).isEqualTo(image);
        var another=create(accounts.account(scope,a.id(),false),"10","paynow");
        assertThatThrownBy(()->tx.execute(s->proofs.scan(scope,actor,"topup",another.id(),"paynow","10",scanKey,"receipt.png","image/png",image))).isInstanceOf(WalletException.class);
        var missingActor=new CurrentActor(tenant,actorId,"staff",Set.of(),Set.of("wallet.points.view"),Set.of(store));
        assertThatThrownBy(()->proofs.context(scope,missingActor,"topup",another.id(),"paynow")).isInstanceOf(WalletException.class);
        assertThatThrownBy(()->tx.execute(s->proofs.scan(scope,actor,"topup",another.id(),"paynow","10",key(),"fake.png","image/png",new byte[]{1,2,3}))).isInstanceOf(WalletException.class);
        when(ocr.extract(any(),any())).thenReturn(new com.rpb.reservation.payment.application.PaymentProofOcrFields(null,new java.math.BigDecimal("9.00"),null,"ocbc",true,java.math.BigDecimal.ONE,"Payment successful\nMessage: "+com.rpb.reservation.wallet.domain.WalletPayNowReference.forSource(another.id())+"\nUEN: 202012345A", "{}"));
        var mismatch=tx.execute(s->proofs.scan(scope,actor,"topup",another.id(),"paynow","10",key(),"receipt.png","image/png",image));
        assertThat(mismatch.checks().get("amount")).isEqualTo("mismatch");
        var mismatched=new CollectionConfirm("paynow","10","202012345A","BANK-456","T1","checked",true,key(),mismatch.id());
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,actor,another.id(),mismatched))).isInstanceOf(WalletException.class);
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(1000);
        // Missing OCR fields allow manual checking; reusing the same image for a second credit still rolls back.
        when(ocr.extract(any(),any())).thenReturn(new com.rpb.reservation.payment.application.PaymentProofOcrFields(null,null,null,null,false,java.math.BigDecimal.ZERO,"Unreadable receipt", "{}"));
        var missing=tx.execute(s->proofs.scan(scope,actor,"topup",another.id(),"paynow","10",key(),"receipt.png","image/png",image));
        var reused=new CollectionConfirm("paynow","10","202012345A","BANK-789","T1","checked",true,key(),missing.id());
        assertThatThrownBy(()->tx.execute(s->service.confirm(scope,actor,another.id(),reused))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_PROOF_CONFLICT");
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(1000);
        var manual=tx.execute(s->proofs.scan(scope,actor,"topup",another.id(),"paynow","10",key(),"different.png","image/png",proofImage(3)));
        tx.execute(s->service.confirm(scope,actor,another.id(),new CollectionConfirm("paynow","10","202012345A","MANUAL-REF","T1","checked missing fields against bank",true,key(),manual.id())));
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(2000);
        var unavailable=create(accounts.account(scope,a.id(),false),"10","paynow");
        when(ocr.extract(any(),any())).thenThrow(new com.rpb.reservation.payment.application.PaymentServiceException(com.rpb.reservation.payment.application.PaymentServiceErrorCode.PAYMENT_OCR_UNAVAILABLE));
        var manualAfterOcrFailure=tx.execute(s->proofs.scan(scope,actor,"topup",unavailable.id(),"paynow","10",key(),"new.png","image/png",proofImage(4)));
        assertThat(manualAfterOcrFailure.extracted().get("ocrStatus")).isEqualTo("unavailable");assertThat(manualAfterOcrFailure.checks().values()).containsOnly("missing");
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(2000);
        tx.execute(s->service.confirm(scope,actor,unavailable.id(),new CollectionConfirm("paynow","10","202012345A","MANUAL-OCR-DOWN","T1","checked original image and bank",true,key(),manualAfterOcrFailure.id())));
        assertThat(accounts.account(scope,a.id(),false).balanceCents()).isEqualTo(3000);


    }
    @Test void consumptionAndTopupFoldersNeverMixAndDirectionsFollowViewer() {
        var a=credit(open(),"100","cash");var b=bill(a,"25");pay(b);
        tx.execute(s->refunds.create(scope,actor,new RefundCreate("bill",b.id(),"5","部分退款",key())));
        var consumption=list(folder("consumption","all"));assertThat(consumption.totalCount()).isEqualTo(2);assertThat(consumption.items()).extracting("sourceType").containsExactlyInAnyOrder("bill","refund");assertThat(consumption.summary().topupPrincipalCents()).isZero();
        var income=list(folder("consumption","in"));assertThat(income.items()).singleElement().satisfies(i->{assertThat(i.type()).isEqualTo("payment");assertThat(i.direction()).isEqualTo("in");});assertThat(income.summary().consumptionCents()).isEqualTo(2500);
        var expense=list(folder("consumption","out"));assertThat(expense.items()).singleElement().extracting("sourceType").isEqualTo("refund");assertThat(expense.summary().refundCents()).isEqualTo(500);
        var customerIncome=tx.execute(s->query().list(scope,null,principal,folder("consumption","in")));assertThat(customerIncome.items()).singleElement().extracting("sourceType").isEqualTo("refund");
        var topups=list(folder("topup","in"));assertThat(topups.items()).singleElement().extracting("type").isEqualTo("topup");assertThat(topups.summary().topupPrincipalCents()).isEqualTo(10000);assertThat(topups.summary().consumptionCents()).isZero();
        assertThat(list(folder("topup","out")).items()).isEmpty();
        var pageQuery=folder("consumption","all");pageQuery.put("limit","1");var page=list(pageQuery);assertThat(page.summary().refundCents()).isEqualTo(500);assertThat(page.nextCursor()).isNotNull();
        pageQuery.put("cursor",page.nextCursor());pageQuery.put("direction","in");assertThatThrownBy(()->list(pageQuery)).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_REQUEST_INVALID");
        pageQuery.put("direction","all");pageQuery.put("category","topup");assertThatThrownBy(()->list(pageQuery)).isInstanceOf(WalletException.class);
        var incompatible=folder("points","all");incompatible.put("type","topup");assertThatThrownBy(()->list(incompatible)).isInstanceOf(WalletException.class);
    }
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
        var fundingActor=actor;
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
        var incoming=list(folder("points","in"));assertThat(incoming.totalCount()).isEqualTo(2);assertThat(incoming.summary().pointsGranted()).isEqualTo(20);assertThat(incoming.summary().pointsReversalIn()).isEqualTo(5);assertThat(incoming.summary().pointsDeducted()).isZero();
        var outgoing=list(folder("points","out"));assertThat(outgoing.totalCount()).isEqualTo(2);assertThat(outgoing.summary().pointsDeducted()).isEqualTo(5);assertThat(outgoing.summary().pointsReversalOut()).isEqualTo(20);
        var auto=folder("auto","all");var onlyPoints=new CurrentActor(tenant,actorId,"staff",Set.of(),Set.of("wallet.points.view"),Set.of(store));assertThat(query().list(scope,onlyPoints,null,auto).category()).isEqualTo("points");
        var reference=new HashMap<>(all());reference.put("reference","REF-POINTS");assertThat(list(reference).items()).singleElement().extracting("id").isEqualTo(grant.entry().id());
        UUID independent=tx.execute(s->pointsService.createProgram(tenant,issuer));var otherBinding=new com.rpb.reservation.loyalty.application.PointsViews.Binding(tenant,issuer,null,store,independent);
        tx.execute(s->pointsService.mutate(otherBinding,actorId,customer,"manual_grant",new com.rpb.reservation.loyalty.application.PointsViews.Mutation(99,"其他产品积分",null,null,null,null,key())));
        assertThat(list(all()).totalCount()).isEqualTo(4);
        var onlyMoney=new CurrentActor(tenant,actorId,"staff",Set.of(),Set.of("wallet.view"),Set.of(store));assertThat(query().list(scope,onlyMoney,null,all()).items()).isEmpty();
        var permissions=new HashSet<>(fundingActor.permissions());permissions.add("wallet.points.view");
        actor=new CurrentActor(tenant,actorId,"tenant_admin",Set.of("tenant_admin"),permissions,Set.of(store));
        var a=credit(open(),"100","cash");pay(bill(a,"25"));
        var moneyQuery=folder("money","all");moneyQuery.put("limit","1");
        var money=tx.execute(s->query().list(scope,null,principal,moneyQuery));
        assertThat(money.category()).isEqualTo("money");assertThat(money.totalCount()).isEqualTo(2);
        assertThat(money.summary().topupPrincipalCents()).isEqualTo(10000);assertThat(money.summary().consumptionCents()).isEqualTo(2500);assertThat(money.summary().pointsGranted()).isZero();
        assertThat(money.items()).allSatisfy(i->assertThat(i.sourceType()).isNotEqualTo("points"));assertThat(money.nextCursor()).isNotNull();
        moneyQuery.put("cursor",money.nextCursor());
        assertThat(query().list(scope,null,principal,moneyQuery).items()).singleElement().satisfies(i->assertThat(i.sourceType()).isNotEqualTo("points"));
        moneyQuery.put("category","all");assertThatThrownBy(()->query().list(scope,null,principal,moneyQuery)).isInstanceOf(WalletException.class);
        assertThat(query().list(scope,null,principal,folder("all","all")).totalCount()).isEqualTo(6);
        assertThat(query().list(scope,null,principal,folder("points","all")).totalCount()).isEqualTo(4);
        assertThatThrownBy(()->query().list(scope,onlyPoints,null,folder("money","all"))).isInstanceOf(WalletException.class).extracting("code").isEqualTo("WALLET_FORBIDDEN");
        var invalid=folder("money","all");invalid.put("type","points_grant");assertThatThrownBy(()->list(invalid)).isInstanceOf(WalletException.class);
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
    @Test void defaultSummariesAreLanguageNeutralButExplicitConsumptionDescriptionIsPreserved() {
        var a=open();var b=bill(a,"3");var topup=create(a,"10","cash");
        assertThat(query().detail(scope,actor,null,"bill",b.id()).transaction().summary()).isNull();
        assertThat(query().detail(scope,null,principal,"topup",topup.id()).transaction().summary()).isNull();
        assertThat(list(all()).items()).allSatisfy(i->assertThat(i.summary()).isNull());
        jdbc.update("insert into wallet_customer_identity_codes(tenant_id,customer_id,program_id,code_hash,expires_at,payment_bill_id,payment_store_id,payment_description) values(?,?,?,?,now()+interval '1 hour',?,?,?)",tenant,customer,a.programId(),"b".repeat(64),b.id(),store,"消费");
        assertThat(query().detail(scope,null,principal,"bill",b.id()).transaction().summary()).isEqualTo("消费");
        assertThat(list(all()).items().stream().filter(i->i.id().equals(b.id())).findFirst().orElseThrow().summary()).isEqualTo("消费");
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
        var directionQuery=folder("topup","out");var customerExit=query().list(scope,null,principal,directionQuery);assertThat(customerExit.items()).singleElement().satisfies(i->{assertThat(i.sourceType()).isEqualTo("refund");assertThat(i.direction()).isEqualTo("out");});
        assertThat(list(folder("consumption","all")).items()).isEmpty();
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
