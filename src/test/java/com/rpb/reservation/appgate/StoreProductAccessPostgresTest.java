package com.rpb.reservation.appgate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.AppGateDenyReason;
import com.rpb.reservation.appgate.guard.*;
import com.rpb.reservation.appgate.persistence.entity.*;
import com.rpb.reservation.appgate.persistence.repository.*;
import com.rpb.reservation.platformbilling.application.*;
import com.rpb.reservation.platformbilling.persistence.*;
import com.rpb.reservation.walkin.api.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual PostgreSQL migrations, billing commands and authorization SQL in a disposable owned schema. */
class StoreProductAccessPostgresTest {
    static final String SCHEMA = "product_access_test_" + UUID.randomUUID().toString().replace("-", "");
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-22T04:00:00Z");
    static JdbcTemplate root, jdbc;
    static TransactionTemplate tx;
    UUID tenant, store, sibling, unpaid, foreignTenant, foreignStore;
    ProductSubscriptionService billing;
    AppGateService gate;
    JdbcStoreAppSubscriptionRepository access;
    CurrentActor actor;

    @BeforeAll static void database() throws Exception {
        Map<String, String> pointer = new HashMap<>();
        for (String line : Files.readAllLines(Path.of("target/local-postgres-current.txt"))) {
            String[] pair = line.split("=", 2);
            if (pair.length == 2) pointer.put(pair[0], pair[1]);
        }
        assertThat(pointer.get("runtimeWorktree").replace('\\', '/')).isEqualToIgnoringCase(Path.of("").toAbsolutePath().toString().replace('\\', '/'));
        int port = Integer.parseInt(pointer.get("port"));
        String url = "jdbc:postgresql://127.0.0.1:" + port + "/postgres?stringtype=unspecified";
        root = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", ""));
        root.execute("create schema " + SCHEMA);
        var ds = new DriverManagerDataSource(url + "&currentSchema=" + SCHEMA, "postgres", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        try (var paths = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (var file : paths.filter(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList()) {
                jdbc.execute(Files.readString(file));
            }
        }
    }

    @AfterAll static void cleanup() {
        if (root != null && SCHEMA.matches("product_access_test_[a-f0-9]{32}")) root.execute("drop schema if exists " + SCHEMA + " cascade");
    }

    @BeforeEach void setup() {
        tenant = addTenant(); foreignTenant = addTenant();
        store = addStore(tenant); sibling = addStore(tenant); foreignStore = addStore(foreignTenant);
        access = new JdbcStoreAppSubscriptionRepository(jdbc);
        billing = new ProductSubscriptionService(new JdbcProductSubscriptionRepository(jdbc),
            new JdbcProductSubscriptionEventRepository(jdbc), new JdbcProductSubscriptionItemRepository(jdbc),
            new JdbcTenantProductEntitlementSyncGateway(jdbc),
            new SubscriptionQuoteService(new JdbcPlatformProductLinePriceRepository(jdbc,
                new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc))),
            new BillingPeriodCalculator(), Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        var apps = mock(PlatformAppJpaRepository.class);
        var entitlements = mock(TenantAppEntitlementJpaRepository.class);
        var settings = mock(StoreAppSettingJpaRepository.class);
        var app = PlatformAppEntity.of(UUID.randomUUID(), "daily_ledger", "营业记录", "active", "/stores/:storeId/daily-ledger", null, 40, "{}");
        when(apps.findByAppKey("daily_ledger")).thenReturn(Optional.of(app));
        when(apps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active")).thenReturn(List.of(app));
        when(entitlements.findAllByTenantId(tenant)).thenAnswer(call -> tenantEntitlements());
        when(entitlements.findByTenantIdAndAppKey(tenant, "daily_ledger")).thenAnswer(call -> tenantEntitlements().stream().findFirst());
        when(settings.findByTenantIdAndStoreIdAndAppKey(any(), any(), any())).thenAnswer(call -> storeSettings(call.getArgument(0), call.getArgument(1)).stream().findFirst());
        when(settings.findAllByTenantIdAndStoreId(any(), any())).thenAnswer(call -> storeSettings(call.getArgument(0), call.getArgument(1)));
        gate = new AppGateService(apps, entitlements, settings, access, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        actor = CurrentActor.storeStaff(tenant, UUID.randomUUID(), "staff", Set.of("store_staff"), Set.of("daily_ledger.view"), Set.of(store, sibling));
    }

    UUID addTenant() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tenants(id,tenant_code,display_name,status) values(?,?,'test','active')", id, "test-" + id);
        return id;
    }

    UUID addStore(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into stores(id,tenant_id,store_code,display_name,status,timezone,locale,date_format,time_format,currency) values(?,?,?,'test','active','Asia/Singapore','zh-CN','DD-MM-YYYY','HH:mm','SGD')", id, owner, "s-" + id);
        return id;
    }

    List<TenantAppEntitlementEntity> tenantEntitlements() {
        return jdbc.query("select * from tenant_app_entitlements where tenant_id=? and app_key='daily_ledger'", (rs, n) ->
            TenantAppEntitlementEntity.of(rs.getObject("id", UUID.class), tenant, "daily_ledger", rs.getString("status"),
                rs.getObject("valid_from", OffsetDateTime.class), rs.getObject("valid_until", OffsetDateTime.class), "{}", null, null), tenant);
    }

    List<StoreAppSettingEntity> storeSettings(UUID owner, UUID id) {
        return jdbc.query("select * from store_app_settings where tenant_id=? and store_id=? and app_key='daily_ledger'", (rs, n) ->
            StoreAppSettingEntity.of(rs.getObject("id", UUID.class), owner, id, "daily_ledger", rs.getBoolean("is_enabled"), rs.getBoolean("entry_visible"), "{}", null, null, null), owner, id);
    }

    ProductSubscription purchase() {
        return tx.execute(status -> billing.purchase(tenant, new ProductSubscriptionCommand(UUID.randomUUID().toString(),
            "daily_ledger", "monthly", null, null, null, "SGD", "local test", 1, null),
            new PlatformBillingOperator(null, "platform_admin")).subscription());
    }

    @Test void expiredStoreIs403UntilActualSingleStoreRenewalWhileSiblingStaysUntouched() throws Exception {
        var subscription = purchase();
        jdbc.update("update tenant_product_subscription_items set current_period_start=?,current_period_end=? where tenant_id=? and store_id=?", NOW.minusMonths(1), NOW.minusSeconds(1), tenant, store);
        var before = jdbc.queryForMap("select * from tenant_product_subscription_items where tenant_id=? and store_id=?", tenant, sibling);
        assertThat(access.findByTenantAndStore(tenant, store).get("daily_ledger").denialAt(NOW)).isEqualTo(AppGateDenyReason.STORE_APP_EXPIRED);
        var provider = mock(CurrentActorProvider.class); when(provider.currentActor()).thenReturn(Optional.of(actor));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestProductController()).addInterceptors(new AppGateInterceptor(gate,
            mock(AppGateDenialAuditService.class), provider, new AppGateApiErrorMapper(), new ObjectMapper())).build();
        mvc.perform(get("/api/v1/stores/" + store + "/test-product")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("STORE_APP_EXPIRED"));
        mvc.perform(get("/api/v1/stores/" + sibling + "/test-product")).andExpect(status().isOk());
        assertThat(gate.visibleApps(actor, store)).isEmpty(); assertThat(gate.visibleApps(actor, sibling)).hasSize(1);
        UUID itemId = jdbc.queryForObject("select id from tenant_product_subscription_items where tenant_id=? and store_id=?", UUID.class, tenant, store);
        tx.execute(status -> billing.renewItem(tenant, subscription.id(), itemId, new ProductSubscriptionCommand(
            "renew-expired-store", "daily_ledger", "monthly", null, null, null, "SGD", "local test", 1, 0), new PlatformBillingOperator(null, "platform_admin")));
        mvc.perform(get("/api/v1/stores/" + store + "/test-product")).andExpect(status().isOk());
        assertThat(gate.visibleApps(actor, store)).hasSize(1);
        assertThat(jdbc.queryForMap("select * from tenant_product_subscription_items where tenant_id=? and store_id=?", tenant, sibling)).isEqualTo(before);
    }

    @Test void missingRowCannotBorrowSiblingEntitlementAndQueriesNeverCrossTenants() {
        purchase(); unpaid = addStore(tenant);
        assertThat(access.findByTenantAndStore(tenant, unpaid).get("daily_ledger").denialAt(NOW)).isEqualTo(AppGateDenyReason.STORE_APP_NOT_ENABLED);
        assertThat(access.findByTenantAndStore(foreignTenant, store)).isEmpty();
        assertThat(access.findByTenantAndStore(tenant, foreignStore).get("daily_ledger").denialAt(NOW)).isEqualTo(AppGateDenyReason.STORE_APP_NOT_ENABLED);
        jdbc.update("update stores set status='inactive' where id=? and tenant_id=?", store, tenant);
        assertThat(access.findByTenantAndStore(tenant, store).get("daily_ledger").denialAt(NOW)).isEqualTo(AppGateDenyReason.STORE_APP_NOT_ENABLED);
    }

    @Test void nullItemEndUsesSubscriptionEndAndLegacyGrantsKeepTheirOriginalSemantics() {
        var subscription = purchase();
        jdbc.update("update tenant_product_subscription_items set current_period_end=null where tenant_id=?", tenant);
        assertThat(access.findByTenantAndStore(tenant, store).get("daily_ledger").validUntil()).isEqualTo(subscription.currentPeriodEnd());
        jdbc.update("update tenant_product_subscriptions set billing_cycle='legacy_grant',amount=0,current_period_end=null where id=?", subscription.id());
        assertThat(access.findByTenantAndStore(tenant, store)).isEmpty();
        assertThat(access.findByTenantAndStore(tenant, addStore(tenant))).isEmpty();
    }

    @Test void earlySingleStoreRenewalDoesNotDisableCurrentAccess() {
        var purchased = purchase();
        var item = purchased.items().stream().filter(row -> row.storeId().equals(store)).findFirst().orElseThrow();
        tx.execute(status -> billing.renewItem(tenant, purchased.id(), item.id(), new ProductSubscriptionCommand(
            "renew-before-expiry", "daily_ledger", "monthly", null, null, null, "SGD", "local test", 1, 0), new PlatformBillingOperator(null, "platform_admin")));
        assertThat(gate.evaluate(new AppGateAccessRequest("daily_ledger", tenant, store, "daily_ledger.view", actor)).allowed()).isTrue();
        assertThat(gate.visibleApps(actor, store)).hasSize(1);
    }

    @Test void productReadsCurrentUnifiedStoreProfileWithoutDuplicatingOrOverwritingLegacyData() {
        var profiles = new com.rpb.reservation.tenantadmin.application.TenantAdminProfileService(
            new com.rpb.reservation.tenantadmin.persistence.TenantAdminProfileRepository(jdbc),
            mock(com.rpb.reservation.queuedisplay.application.CallScreenMediaService.class));
        var branding = new com.rpb.reservation.dailyledger.application.LedgerBrandingService(profiles, new ObjectMapper());
        var scope = new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(tenant), store);
        var otherScope = new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(tenant), sibling);
        var legacy = new ObjectMapper().createObjectNode().put("store_name", "旧名称").put("store_name_en", "private").put("logo_data", "old-logo");
        Long tenantCount = jdbc.queryForObject("select count(*) from tenants", Long.class);
        tx.execute(status -> profiles.updateProfile(scope, new com.rpb.reservation.tenantadmin.application.TenantAdminProfileCommand(
            "统一更新名称", "zh-CN", "+6599999999", "新地址", null)));
        assertThat(branding.resolve(scope, legacy).path("store_name").asText()).isEqualTo("统一更新名称");
        assertThat(branding.resolve(otherScope, legacy).path("store_name").asText()).isEqualTo("test");
        assertThat(branding.resolve(scope, legacy).path("logo_data").asText()).isEmpty();
        assertThat(branding.resolve(scope, legacy).path("store_name_en").asText()).isEmpty();
        assertThat(legacy.path("logo_data").asText()).isEqualTo("old-logo");
        assertThat(jdbc.queryForObject("select count(*) from tenants", Long.class)).isEqualTo(tenantCount);
        var wrongScope = new com.rpb.reservation.common.scope.StoreScope(new com.rpb.reservation.tenant.value.TenantId(foreignTenant), store);
        assertThatThrownBy(() -> profiles.getProfile(wrongScope)).isInstanceOf(com.rpb.reservation.tenantadmin.application.TenantAdminServiceException.class);
    }

    @RestController static class TestProductController {
        @GetMapping("/api/v1/stores/{storeId}/test-product")
        @RequireAppGate(appKey = "daily_ledger", permission = "daily_ledger.view")
        public String read(@PathVariable UUID storeId) { return "ok"; }
    }
}
