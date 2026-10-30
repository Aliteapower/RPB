package com.rpb.reservation.platformbilling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.platform.application.*;
import com.rpb.reservation.platform.persistence.*;
import com.rpb.reservation.platformbilling.application.*;
import com.rpb.reservation.platformbilling.persistence.*;
import com.rpb.reservation.queue.application.DefaultQueueGroupProvisioningService;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoreBillingConsistencyPostgresTest {
    static final String SCHEMA="store_billing_test_"+UUID.randomUUID().toString().replace("-", "");
    static JdbcTemplate jdbc, root;
    static DataSourceTransactionManager tx;
    UUID tenant, entity, main, branch;
    JdbcProductSubscriptionRepository subscriptions;
    JdbcProductSubscriptionItemRepository items;
    ProductSubscriptionService billing;
    SelectedStoreBillingService wallet;
    PlatformTenantStructureService structure;
    final PlatformBillingOperator operator = new PlatformBillingOperator(null,"platform_admin");
    @BeforeAll static void database() throws Exception {
        var pointer=new HashMap<String,String>();
        for(var line:Files.readAllLines(Path.of("target/local-postgres-current.txt"))){var p=line.split("=",2);if(p.length==2)pointer.put(p[0],p[1]);}
        assertThat(Path.of(pointer.get("runtimeWorktree")).toAbsolutePath()).isEqualTo(Path.of("").toAbsolutePath());
        String url="jdbc:postgresql://127.0.0.1:"+Integer.parseInt(pointer.get("port"))+"/postgres?stringtype=unspecified";
        root=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));root.execute("create schema "+SCHEMA);
        var ds=new DriverManagerDataSource(url+"&currentSchema="+SCHEMA,"postgres","");jdbc=new JdbcTemplate(ds);tx=new DataSourceTransactionManager(ds);
        try(var files=Files.list(Path.of("src/main/resources/db/migration"))){for(var p:files.filter(p->p.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList())jdbc.execute(Files.readString(p));}
    }
    @AfterAll static void cleanup(){if(root!=null&&SCHEMA.matches("store_billing_test_[a-f0-9]{32}"))root.execute("drop schema if exists "+SCHEMA+" cascade");}
    @BeforeEach void setup(){
        tenant=UUID.randomUUID();entity=UUID.randomUUID();main=UUID.randomUUID();branch=UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Test','active')",tenant,"t-"+tenant);
        jdbc.update("insert into operating_entities(id,tenant_id,entity_code,display_name,status) values(?,?,?,'Original Entity','active')",entity,tenant,"e-"+entity);
        for(UUID s:List.of(main,branch))jdbc.update("insert into stores(id,tenant_id,operating_entity_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,?,'Original Store','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",s,tenant,entity,"s-"+s);
        subscriptions=new JdbcProductSubscriptionRepository(jdbc);items=new JdbcProductSubscriptionItemRepository(jdbc);
        var events=new JdbcProductSubscriptionEventRepository(jdbc);
        var prices=mock(PlatformProductLinePriceRepository.class);
        when(prices.findActivePrice(anyString(),anyString())).thenAnswer(c->Optional.of(new PlatformProductLinePrice(c.getArgument(0),c.getArgument(1),new BigDecimal("128.00"),"SGD","active",0)));
        var quotes=new SubscriptionQuoteService(prices);
        var clock=Clock.fixed(Instant.parse("2026-10-30T00:00:00Z"),ZoneOffset.UTC);
        var structures=new PlatformTenantStructureRepository(jdbc);
        var sync=mock(TenantProductEntitlementSyncGateway.class);
        billing=proxy(new ProductSubscriptionService(subscriptions,events,items,sync,quotes,new BillingPeriodCalculator(),clock));
        wallet=proxy(new SelectedStoreBillingService(subscriptions,events,items,sync,quotes,new SelectedBillingCommandRepository(jdbc,new ObjectMapper().findAndRegisterModules()),new SharedStoreIdentityQuery(structures),clock));
        structure=proxy(new PlatformTenantStructureService(new PlatformTenantRepository(jdbc),structures,mock(PlatformStoreAdminAccountRepository.class),mock(PasswordEncoder.class),mock(PlatformTenantAuditService.class),mock(PublicHostBindingService.class),mock(DefaultQueueGroupProvisioningService.class)));
    }
    @SuppressWarnings("unchecked") <T>T proxy(T value){var p=new ProxyFactory(value);p.setProxyTargetClass(true);p.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));return (T)p.getProxy();}
    ProductSubscriptionCommand command(String key,String app,Integer version,List<UUID> stores){return new ProductSubscriptionCommand(key,app,"monthly",null,null,null,"SGD",null,1,version,stores);}
    void status(UUID store,String status){structure.updateStore(tenant,store,new PlatformStoreMutationCommand(null,null,null,status,null,null,null,null,null,null,null),new PlatformOperator(null,"platform_admin"));}
    ProductSubscription purchased(){return billing.purchase(tenant,command("purchase","reservation_queue",null,null),operator).subscription();}
    @Test void disablingAndRestoringRecountsWithoutChangingPurchasedItemsOrEvents(){
        var s=purchased();var before=items.listByTenantId(tenant);String history=payload(s.id());
        status(branch,"inactive");
        assertThat(subscriptions.findByTenantIdAndId(tenant,s.id()).orElseThrow().amount()).isEqualByComparingTo("128");
        assertThat(items.listByTenantId(tenant)).containsExactlyElementsOf(before);
        status(branch,"active");
        assertThat(subscriptions.findByTenantIdAndId(tenant,s.id()).orElseThrow().amount()).isEqualByComparingTo("256");
        assertThat(items.listByTenantId(tenant)).containsExactlyElementsOf(before);
        assertThat(payload(s.id())).isEqualTo(history);
    }
    @Test void renewingWholeTenantPreservesInactivePurchasedItemAndQuotesOnlyActiveStore(){
        var s=purchased();var original=items.listByTenantId(tenant).stream().filter(i->i.storeId().equals(branch)).findFirst().orElseThrow();
        status(branch,"inactive");
        var current=subscriptions.findByTenantIdAndId(tenant,s.id()).orElseThrow();
        var result=billing.renew(tenant,s.id(),command("renew","reservation_queue",current.version(),null),operator);
        assertThat(result.quote().storeCount()).isEqualTo(1);
        assertThat(result.subscription().amount()).isEqualByComparingTo("128");
        assertThat(items.listByTenantId(tenant)).contains(original);
    }
    @Test void singleStoreRenewalRejectsInactiveStoreAndExcludesItFromAggregate(){
        var s=purchased();status(branch,"inactive");
        var all=items.listByTenantId(tenant);var inactive=all.stream().filter(i->i.storeId().equals(branch)).findFirst().orElseThrow();var active=all.stream().filter(i->i.storeId().equals(main)).findFirst().orElseThrow();
        assertThatThrownBy(()->billing.renewItem(tenant,s.id(),inactive.id(),command("denied","reservation_queue",inactive.version(),null),operator)).hasMessageContaining("SUBSCRIPTION_ITEM_NOT_FOUND");
        var result=billing.renewItem(tenant,s.id(),active.id(),command("renew-one","reservation_queue",active.version(),null),operator);
        assertThat(result.subscription().amount()).isEqualByComparingTo("128");
        assertThat(items.listByTenantId(tenant)).contains(inactive);
    }
    @Test void walletSelectedRenewalCannotIncludeInactiveItemsFromAnotherStore(){
        var s=wallet.purchase(tenant,command("wallet-purchase","wallet",null,List.of(main,branch)),operator).subscription();
        status(branch,"inactive");var current=subscriptions.findByTenantIdAndId(tenant,s.id()).orElseThrow();
        assertThatThrownBy(()->wallet.renew(tenant,s.id(),command("wallet-denied","wallet",current.version(),List.of(branch)),operator)).hasMessageContaining("REQUEST_INVALID");
        var result=wallet.renew(tenant,s.id(),command("wallet-renew","wallet",current.version(),List.of(main)),operator);
        assertThat(result.subscription().amount()).isEqualByComparingTo("128");
        assertThat(result.subscription().items()).hasSize(2);
    }
    @Test void eventSnapshotsKeepNamesAmountsAndPeriodsAfterMasterDataChanges() throws Exception {
        var s=purchased();var json=new ObjectMapper().readTree(payload(s.id()));
        assertThat(json.path("storeSnapshotVersion").asInt()).isEqualTo(1);
        assertThat(json.path("storeCount").asInt()).isEqualTo(2);
        assertThat(json.path("storeSnapshots").size()).isEqualTo(2);
        var snapshot=json.path("storeSnapshots").get(0);
        assertThat(snapshot.path("storeName").asText()).isEqualTo("Original Store");
        assertThat(snapshot.path("operatingEntityName").asText()).isEqualTo("Original Entity");
        assertThat(new BigDecimal(snapshot.path("amount").asText())).isEqualByComparingTo("128");
        assertThat(snapshot.path("periodEnd").asText()).isNotBlank();
        jdbc.update("update stores set display_name='Renamed' where tenant_id=?",tenant);
        jdbc.update("update operating_entities set display_name='Renamed Entity' where tenant_id=?",tenant);
        assertThat(new ObjectMapper().readTree(payload(s.id()))).isEqualTo(json);
        var history=new JdbcProductSubscriptionHistoryRepository(jdbc,new ProductSubscriptionHistorySnapshotReader(new ObjectMapper().findAndRegisterModules()));
        var historical=history.list(tenant,s.id()).getFirst();
        assertThat(historical.snapshotAvailable()).isTrue();
        assertThat(historical.stores()).hasSize(2);
        assertThat(historical.stores()).extracting(ProductSubscriptionHistoryEvent.StoreSnapshot::storeName).containsOnly("Original Store");
        assertThat(historical.stores()).extracting(ProductSubscriptionHistoryEvent.StoreSnapshot::operatingEntityName).containsOnly("Original Entity");
        assertThat(historical.stores().getFirst().amount()).isEqualByComparingTo("128");
        assertThat(history.list(UUID.randomUUID(),s.id())).isEmpty();
        assertThat(items.findByTenantSubscriptionAndId(UUID.randomUUID(),s.id(),s.items().getFirst().id())).isEmpty();
        assertThatThrownBy(()->billing.renewItem(UUID.randomUUID(),s.id(),s.items().getFirst().id(),command("cross","reservation_queue",0,null),operator)).hasMessageContaining("TENANT_NOT_FOUND");
    }
    @Test void deletingStoreStillCancelsItsItemsAndRefreshesAggregate(){
        var s=purchased();structure.deleteStore(tenant,branch,new PlatformOperator(null,"platform_admin"));
        assertThat(items.listByTenantId(tenant).stream().filter(i->i.storeId().equals(branch)).findFirst().orElseThrow().status()).isEqualTo("cancelled");
        assertThat(subscriptions.findByTenantIdAndId(tenant,s.id()).orElseThrow().amount()).isEqualByComparingTo("128");
    }
    @Test void concurrentPurchaseWaitsForStoreLifecycleAndUsesCommittedActiveStores() throws Exception {
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        var locked=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        try {
            var update=executor.submit(()->new org.springframework.transaction.support.TransactionTemplate(tx).execute(ignored->{
                subscriptions.lockTenantBilling(tenant);
                status(branch,"inactive");
                locked.countDown();
                try { if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("release timed out"); }
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return true;
            }));
            assertThat(locked.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var purchase=executor.submit(this::purchased);
            assertThatThrownBy(()->purchase.get(250,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            assertThat(update.get(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var result=purchase.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(result.amount()).isEqualByComparingTo("128");
            assertThat(result.items()).extracting(ProductSubscriptionItem::storeId).containsExactly(main);
        } finally {release.countDown();executor.shutdownNow();}
    }
    @Test void allProductsCanPurchaseSelectedStoresAndAddWithoutRenewingOthers() {
        for(String app:List.of("reservation_queue","workforce","daily_ledger","wine_storage","payment","wallet")) {
            var s=wallet.purchase(tenant,command("buy-"+app,app,null,List.of(main)),operator).subscription();
            assertThat(s.items()).extracting(ProductSubscriptionItem::storeId).containsExactly(main);
            var original=s.items().getFirst();String history=payload(s.id());
            var add=command("add-"+app,app,s.version(),List.of(branch));
            var result=wallet.addStores(tenant,s.id(),add,operator);
            assertThat(result.subscription().items()).hasSize(2).contains(original);
            assertThat(result.quote().storeCount()).isEqualTo(1);
            assertThat(result.quote().finalAmount()).isEqualByComparingTo("128");
            assertThat(payload(s.id())).isEqualTo(history);
            assertThat(wallet.addStores(tenant,s.id(),add,operator).replayed()).isTrue();
        }
    }
    @Test void selectedGenericBillingRejectsWrongScopeDuplicatesInactiveAndStaleVersion() {
        var s=wallet.purchase(tenant,command("buy-selected","workforce",null,List.of(main)),operator).subscription();
        assertThatThrownBy(()->wallet.addStores(tenant,s.id(),command("wrong-app","daily_ledger",s.version(),List.of(branch)),operator)).isInstanceOf(PlatformBillingServiceException.class);
        assertThatThrownBy(()->wallet.addStores(tenant,s.id(),command("same-store","workforce",s.version(),List.of(main)),operator)).hasMessageContaining("SUBSCRIPTION_CONFLICT");
        assertThatThrownBy(()->wallet.addStores(tenant,s.id(),command("cross-store","workforce",s.version(),List.of(UUID.randomUUID())),operator)).hasMessageContaining("REQUEST_INVALID");
        assertThatThrownBy(()->wallet.addStores(tenant,s.id(),command("stale","workforce",99,List.of(branch)),operator)).hasMessageContaining("VERSION_CONFLICT");
        status(branch,"inactive");
        assertThatThrownBy(()->wallet.addStores(tenant,s.id(),command("inactive","workforce",s.version(),List.of(branch)),operator)).hasMessageContaining("REQUEST_INVALID");
        assertThat(subscriptions.findByTenantIdAndId(tenant,s.id()).orElseThrow().version()).isEqualTo(s.version());
    }
    @Test void selectedPurchaseAndAddSyncOnlyPurchasedStoresAndPreserveClosedProduct() {
        var priceRepository=mock(PlatformProductLinePriceRepository.class);
        when(priceRepository.findActivePrice(anyString(),anyString())).thenAnswer(c->Optional.of(new PlatformProductLinePrice(c.getArgument(0),c.getArgument(1),new BigDecimal("128.00"),"SGD","active",0)));
        var service=proxy(new SelectedStoreBillingService(subscriptions,new JdbcProductSubscriptionEventRepository(jdbc),items,new JdbcTenantProductEntitlementSyncGateway(jdbc),new SubscriptionQuoteService(priceRepository),new SelectedBillingCommandRepository(jdbc,new ObjectMapper().findAndRegisterModules()),new SharedStoreIdentityQuery(new PlatformTenantStructureRepository(jdbc)),Clock.fixed(Instant.parse("2026-10-30T00:00:00Z"),ZoneOffset.UTC)));
        var s=service.purchase(tenant,command("sync-buy","reservation_queue",null,List.of(main)),operator).subscription();
        assertThat(jdbc.queryForObject("select count(*) from store_app_settings where tenant_id=? and app_key='reservation_queue' and is_enabled",Integer.class,tenant)).isEqualTo(1);
        jdbc.update("update store_app_settings set is_enabled=false,disabled_at=now() where tenant_id=? and store_id=? and app_key='reservation_queue'",tenant,main);
        var original=s.items().getFirst();String history=payload(s.id());
        var result=service.addStores(tenant,s.id(),command("sync-add","reservation_queue",s.version(),List.of(branch)),operator);
        assertThat(result.subscription().items()).contains(original);
        assertThat(jdbc.queryForObject("select is_enabled from store_app_settings where tenant_id=? and store_id=? and app_key='reservation_queue'",Boolean.class,tenant,main)).isFalse();
        assertThat(jdbc.queryForObject("select is_enabled from store_app_settings where tenant_id=? and store_id=? and app_key='reservation_queue'",Boolean.class,tenant,branch)).isTrue();
        assertThat(payload(s.id())).isEqualTo(history);
    }
    @Test void concurrentAddsWithSameVersionChargeOnce() throws Exception {
        var s=wallet.purchase(tenant,command("concurrent-buy","workforce",null,List.of(main)),operator).subscription();
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a=executor.submit(()->wallet.addStores(tenant,s.id(),command("concurrent-a","workforce",s.version(),List.of(branch)),operator));
            var b=executor.submit(()->wallet.addStores(tenant,s.id(),command("concurrent-b","workforce",s.version(),List.of(branch)),operator));
            int success=0,conflict=0;
            for(var future:List.of(a,b))try {future.get(10,java.util.concurrent.TimeUnit.SECONDS);success++;}catch(java.util.concurrent.ExecutionException e){assertThat(e.getCause()).hasMessageContaining("VERSION_CONFLICT");conflict++;}
            assertThat(success).isEqualTo(1);assertThat(conflict).isEqualTo(1);
            assertThat(items.listByTenantId(tenant)).hasSize(2).contains(s.items().getFirst());
            assertThat(jdbc.queryForObject("select count(*) from tenant_product_subscription_events where tenant_id=?",Integer.class,tenant)).isEqualTo(2);
        } finally {executor.shutdownNow();}
    }

    @Test void walletKeepsEntityCurrencyConstraintsWhileOtherProductsUseTheirOwnPrice() {
        jdbc.update("update stores set operating_entity_id=null where id=?",branch);
        assertThat(wallet.quote(tenant,command("generic-unassigned","workforce",null,List.of(branch))).storeCount()).isEqualTo(1);
        assertThatThrownBy(()->wallet.purchase(tenant,command("wallet-unassigned","wallet",null,List.of(branch)),operator)).hasMessageContaining("REQUEST_INVALID");
        var invalidCurrency=new ProductSubscriptionCommand("wallet-currency","wallet","monthly",null,null,null,"USD",null,1,null,List.of(main));
        assertThatThrownBy(()->wallet.purchase(tenant,invalidCurrency,operator)).hasMessageContaining("REQUEST_INVALID");
    }
    @Test void foreignTenantStoresAndSubscriptionIdsAreRejected() {
        var other=UUID.randomUUID();var foreign=UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'Other','active')",other,"t-"+other);
        jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'Other Store','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')",foreign,other,"s-"+foreign);
        var s=wallet.purchase(tenant,command("scope-buy","workforce",null,List.of(main)),operator).subscription();
        assertThatThrownBy(()->wallet.addStores(tenant,s.id(),command("foreign-store","workforce",s.version(),List.of(foreign)),operator)).hasMessageContaining("REQUEST_INVALID");
        assertThatThrownBy(()->wallet.addStores(other,s.id(),command("foreign-subscription","workforce",s.version(),List.of(foreign)),operator)).hasMessageContaining("SUBSCRIPTION_NOT_FOUND");
        assertThat(items.listByTenantId(tenant)).hasSize(1);assertThat(items.listByTenantId(other)).isEmpty();
    }

    String payload(UUID subscription){return jdbc.queryForObject("select event_payload::text from tenant_product_subscription_events where tenant_id=? and subscription_id=? order by created_at,id limit 1",String.class,tenant,subscription);}
}
