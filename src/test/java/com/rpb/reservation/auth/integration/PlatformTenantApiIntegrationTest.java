package com.rpb.reservation.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformTenantApiIntegrationTest {
    private static final AuthPostgresTestDatabase DATABASE = AuthPostgresTestDatabase.startWithValidationStore();
    private static final String PASSWORD_393930_HASH = "$2a$10$ktA3gOgzus6v0bsJqw53.OerYPoQT6oet7NDdkmNhYYZaKH9ix9Vy";
    private static final UUID SECONDARY_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000984");
    private static final UUID FOREIGN_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000985");
    private static final UUID FOREIGN_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000985");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::jdbcUrl);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
        registry.add("rpb.host-prefix.base-host", () -> "booking.yumstone.sg");
    }

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @BeforeEach
    void setUp() {
        jdbc.update("delete from auth_user_sessions");
        jdbc.update("delete from auth_slider_captcha_challenges");
        jdbc.update("delete from audit_logs where operation_code like 'platform.tenant.%'");
        jdbc.update("delete from auth_account_roles where account_id in (select id from auth_accounts where username like 'codex-%')");
        jdbc.update("delete from auth_account_permissions where account_id in (select id from auth_accounts where username like 'codex-%')");
        jdbc.update("delete from auth_account_store_access where account_id in (select id from auth_accounts where username like 'codex-%')");
        jdbc.update("delete from auth_account_store_access where store_id in (select id from stores where store_code like 'codex-%')");
        jdbc.update("delete from auth_accounts where username like 'codex-%'");
        jdbc.update("delete from tenant_product_subscriptions where app_key like 'codex-%'");
        jdbc.update("delete from tenant_app_entitlements where app_key like 'codex-%'");
        jdbc.update("delete from store_app_settings where app_key like 'codex-%'");
        jdbc.update("delete from platform_product_line_prices where app_key like 'codex-%'");
        jdbc.update("delete from platform_apps where app_key like 'codex-%'");
        jdbc.update("""
            update auth_accounts
            set default_store_id = null
            where default_store_id in (
                select id
                from stores
                where store_code like 'codex-%'
            )
            """);
        jdbc.update("delete from tenant_product_subscriptions where tenant_id in (select id from tenants where tenant_code like 'codex-%')");
        jdbc.update("delete from public_host_bindings where hostname like 'codex-%.booking.yumstone.sg'");
        jdbc.update("""
            delete from public_host_bindings
            where host_alias_id in (
                select id
                from tenant_host_aliases
                where alias_code like 'codex-%'
                   or tenant_id in (select id from tenants where tenant_code like 'codex-%')
            )
            """);
        jdbc.update("delete from tenant_host_aliases where alias_code like 'codex-%'");
        jdbc.update("delete from tenant_host_aliases where tenant_id in (select id from tenants where tenant_code like 'codex-%')");
        jdbc.update("delete from stores where store_code like 'codex-%'");
        jdbc.update("delete from stores where tenant_id in (select id from tenants where tenant_code like 'codex-%')");
        jdbc.update("delete from operating_entities where entity_code like 'codex-%'");
        jdbc.update("delete from operating_entities where tenant_id in (select id from tenants where tenant_code like 'codex-%')");
        jdbc.update("delete from tenants where tenant_code like 'codex-%'");
        jdbc.update("""
            update tenants
            set tenant_code = '20000000',
                display_name = '食刻租户',
                status = 'active',
                default_locale = 'zh-CN',
                deleted_at = null
            where id = ?
            """, AuthPostgresTestDatabase.VALIDATION_TENANT_ID);
        jdbc.update(
            "update auth_accounts set password_hash = ? where username in ('sysadmin', '20000000', '1000')",
            PASSWORD_393930_HASH
        );
        jdbc.update(
            """
            update auth_accounts
            set default_store_id = ?
            where tenant_id = ?
              and username in ('sysadmin', '20000000', '1000')
              and deleted_at is null
            """,
            AuthPostgresTestDatabase.VALIDATION_STORE_ID,
            AuthPostgresTestDatabase.VALIDATION_TENANT_ID
        );
        jdbc.update(
            """
            insert into auth_account_store_access (account_id, tenant_id, store_id)
            select account.id, account.tenant_id, ?
            from auth_accounts account
            where account.tenant_id = ?
              and account.username in ('sysadmin', '20000000', '1000')
              and account.deleted_at is null
              and not exists (
                  select 1
                  from auth_account_store_access existing
                  where existing.account_id = account.id
                    and existing.tenant_id = account.tenant_id
                    and existing.store_id = ?
                    and existing.deleted_at is null
              )
            """,
            AuthPostgresTestDatabase.VALIDATION_STORE_ID,
            AuthPostgresTestDatabase.VALIDATION_TENANT_ID,
            AuthPostgresTestDatabase.VALIDATION_STORE_ID
        );
    }

    @Test
    void platformAdminListsRepairsCreatesDeletesAndRestoresTenants() throws Exception {
        Cookie session = login("sysadmin");

        mockMvc.perform(get("/api/v1/platform/tenants").param("includeDeleted", "true").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenants[0].tenantCode").value("20000000"))
            .andExpect(jsonPath("$.tenants[0].deleted").value(false))
            .andExpect(jsonPath("$.page.limit").value(20))
            .andExpect(jsonPath("$.page.offset").value(0))
            .andExpect(jsonPath("$.page.total").isNumber());

        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}", AuthPostgresTestDatabase.VALIDATION_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantCode":"20000000",
                      "displayName":"食刻租户",
                      "status":"active",
                      "defaultLocale":"zh-CN",
                      "contactPhone":"021-393930",
                      "address":"上海市徐汇区示例路 1 号",
                      "principalName":"张店长"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenant.tenantCode").value("20000000"))
            .andExpect(jsonPath("$.tenant.displayName").value("食刻租户"))
            .andExpect(jsonPath("$.tenant.contactPhone").value("021-393930"))
            .andExpect(jsonPath("$.tenant.address").value("上海市徐汇区示例路 1 号"))
            .andExpect(jsonPath("$.tenant.principalName").value("张店长"));

        UUID createdTenantId = createTenant(session, "codex-tenant", "Codex 新租户");
        assertThat(countWhere("""
            select count(*)
            from tenant_host_aliases
            where tenant_id = ?
              and alias_code = 'codex-tenant'
              and alias_type = 'tenant'
              and status = 'active'
              and deleted_at is null
            """, createdTenantId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from public_host_bindings binding
            join tenant_host_aliases alias on alias.id = binding.host_alias_id
            where binding.tenant_id = ?
              and binding.hostname = 'codex-tenant.booking.yumstone.sg'
              and binding.host_type = 'tenant'
              and binding.tls_status = 'pending'
              and binding.deleted_at is null
              and alias.alias_code = 'codex-tenant'
              and alias.alias_type = 'tenant'
            """, createdTenantId)).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/platform/tenants/{tenantId}", createdTenantId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenant.deleted").value(true));
        assertThat(countWhere("select count(*) from tenants where id = ? and deleted_at is not null", createdTenantId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from tenant_host_aliases
            where tenant_id = ?
              and alias_code = 'codex-tenant'
              and alias_type = 'tenant'
              and status = 'archived'
              and deleted_at is not null
            """, createdTenantId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from public_host_bindings
            where tenant_id = ?
              and hostname = 'codex-tenant.booking.yumstone.sg'
              and tls_status = 'archived'
              and deleted_at is not null
            """, createdTenantId)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/platform/tenants/{tenantId}/restore", createdTenantId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenant.deleted").value(false));
        assertThat(countWhere("select count(*) from tenants where id = ? and deleted_at is null", createdTenantId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from tenant_host_aliases
            where tenant_id = ?
              and alias_code = 'codex-tenant'
              and alias_type = 'tenant'
              and status = 'active'
              and deleted_at is null
            """, createdTenantId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from public_host_bindings
            where tenant_id = ?
              and hostname = 'codex-tenant.booking.yumstone.sg'
              and tls_status = 'pending'
              and deleted_at is null
            """, createdTenantId)).isEqualTo(1);
    }

    @Test
    void platformTenantApiRequiresSessionAndPlatformAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tenants"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        Cookie staffSession = login("1000");
        mockMvc.perform(get("/api/v1/platform/tenants").cookie(staffSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void platformTenantApiRejectsDuplicateActiveTenantCode() throws Exception {
        Cookie session = login("sysadmin");
        createTenant(session, "codex-tenant", "Codex 新租户");

        mockMvc.perform(post("/api/v1/platform/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tenantCode":"codex-tenant","displayName":"重复租户","status":"active","initialPassword":"abc123"}
                    """)
                .cookie(session))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("TENANT_CODE_CONFLICT"));
    }

    @Test
    void platformTenantApiRejectsTenantCodeChangesOnUpdate() throws Exception {
        Cookie session = login("sysadmin");

        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}", AuthPostgresTestDatabase.VALIDATION_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantCode":"changed-code",
                      "displayName":"食刻租户",
                      "status":"active",
                      "defaultLocale":"zh-CN"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
    }

    @Test
    void platformAdminPagesSearchesTenantContactsAndMaintainsTenantAdminPassword() throws Exception {
        Cookie session = login("sysadmin");
        UUID alphaId = createTenant(session, "codex-alpha", "Codex 甲租户");
        createTenant(session, "codex-beta", "Codex 乙租户");

        mockMvc.perform(get("/api/v1/platform/tenants")
                .param("includeDeleted", "true")
                .param("keyword", "乙")
                .param("status", "active")
                .param("limit", "1")
                .param("offset", "0")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenants.length()").value(1))
            .andExpect(jsonPath("$.tenants[0].tenantCode").value("codex-beta"))
            .andExpect(jsonPath("$.tenants[0].contactPhone").value("021-000002"))
            .andExpect(jsonPath("$.tenants[0].address").value("上海市乙路 2 号"))
            .andExpect(jsonPath("$.tenants[0].principalName").value("乙负责人"))
            .andExpect(jsonPath("$.page.limit").value(1))
            .andExpect(jsonPath("$.page.offset").value(0))
            .andExpect(jsonPath("$.page.total").value(1));

        assertThat(countWhere("""
            select count(*)
            from auth_accounts account
            join auth_account_roles role on role.account_id = account.id
            where account.tenant_id = ?
              and account.username = 'codex-alpha'
              and account.actor_type = 'tenant_admin'
              and account.status = 'active'
              and role.role_code = 'tenant_admin'
              and account.deleted_at is null
              and role.deleted_at is null
            """, alphaId)).isEqualTo(1);

        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}", alphaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantCode":"codex-alpha",
                      "displayName":"Codex 甲租户修订",
                      "status":"active",
                      "defaultLocale":"zh-CN",
                      "contactPhone":"021-000003",
                      "address":"上海市甲路 3 号",
                      "principalName":"甲负责人",
                      "password":"QWE123"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenant.contactPhone").value("021-000003"))
            .andExpect(jsonPath("$.tenant.address").value("上海市甲路 3 号"))
            .andExpect(jsonPath("$.tenant.principalName").value("甲负责人"));

        login("codex-alpha", "qwe123");
    }

    @Test
    void creatingTenantBootstrapsDefaultStoreAndTenantAdminLoginScope() throws Exception {
        Cookie session = login("sysadmin");

        UUID tenantId = createTenant(session, "codex-login", "Codex 登录租户");
        UUID storeId = jdbc.queryForObject(
            """
            select id
            from stores
            where tenant_id = ?
              and store_code = ?
              and status = 'active'
              and deleted_at is null
            """,
            UUID.class,
            tenantId,
            "codex-login"
        );

        assertThat(storeId).isNotNull();
        assertThat(countWhere("""
            select count(*)
            from operating_entities entity
            join stores store
              on store.operating_entity_id = entity.id
             and store.tenant_id = entity.tenant_id
            where entity.tenant_id = ?
              and entity.entity_code = 'codex-login'
              and entity.display_name = 'Codex 登录租户'
              and entity.status = 'active'
              and entity.deleted_at is null
              and store.id = ?
              and store.deleted_at is null
            """, tenantId, storeId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from auth_accounts account
            join auth_account_store_access access on access.account_id = account.id
            where account.tenant_id = ?
              and account.username = 'codex-login'
              and account.actor_type = 'tenant_admin'
              and account.default_store_id = ?
              and access.tenant_id = ?
              and access.store_id = ?
              and account.deleted_at is null
              and access.deleted_at is null
            """, tenantId, storeId, tenantId, storeId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from tenant_host_aliases
            where tenant_id = ?
              and alias_code = 'codex-login'
              and alias_type = 'tenant'
              and default_store_id is null
              and status = 'active'
              and deleted_at is null
            """, tenantId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from public_host_bindings
            where tenant_id = ?
              and hostname = 'codex-login.booking.yumstone.sg'
              and host_prefix = 'codex-login'
              and host_type = 'tenant'
              and tls_status = 'pending'
              and deleted_at is null
            """, tenantId)).isEqualTo(1);

        login("codex-login", "abc123");
    }

    @Test
    void creatingGroupTenantBootstrapsDefaultOperatingEntityWithoutStore() throws Exception {
        Cookie session = login("sysadmin");

        MvcResult result = mockMvc.perform(post("/api/v1/platform/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantCode":"codex-group",
                      "displayName":"Codex 集团租户",
                      "status":"active",
                      "defaultLocale":"zh-CN",
                      "contactPhone":"+6590000001",
                      "address":"集团总部地址",
                      "principalName":"集团负责人",
                      "initialPassword":"abc123",
                      "onboardingMode":"group_multi_store"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenant.tenantCode").value("codex-group"))
            .andReturn();

        UUID tenantId = UUID.fromString(objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("tenant")
            .path("id")
            .asText());

        assertThat(countWhere("""
            select count(*)
            from operating_entities
            where tenant_id = ?
              and entity_code = 'codex-group'
              and display_name = 'Codex 集团租户'
              and status = 'active'
              and default_locale = 'zh-CN'
              and contact_phone = '+6590000001'
              and address = '集团总部地址'
              and principal_name = '集团负责人'
              and deleted_at is null
            """, tenantId)).isEqualTo(1);
        assertThat(countWhere("select count(*) from stores where tenant_id = ?", tenantId)).isZero();
        assertThat(countWhere("""
            select count(*)
            from auth_accounts account
            left join auth_account_store_access access
              on access.account_id = account.id
             and access.deleted_at is null
            where account.tenant_id = ?
              and account.username = 'codex-group'
              and account.actor_type = 'tenant_admin'
              and account.default_store_id is null
              and account.deleted_at is null
              and access.id is null
            """, tenantId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from tenant_host_aliases
            where tenant_id = ?
              and alias_code = 'codex-group'
              and alias_type = 'tenant'
              and default_store_id is null
              and status = 'active'
              and deleted_at is null
            """, tenantId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from public_host_bindings
            where tenant_id = ?
              and hostname = 'codex-group.booking.yumstone.sg'
              and host_prefix = 'codex-group'
              and host_type = 'tenant'
              and tls_status = 'pending'
              and deleted_at is null
            """, tenantId)).isEqualTo(1);

        login("codex-group", "abc123");
    }

    @Test
    void platformAdminCreatesBranchStoreManagerWithSeparatePassword() throws Exception {
        Cookie session = login("sysadmin");

        UUID tenantId = createGroupTenant(session, "codex-branch-admin", "Codex 分店账号集团", "abc123");
        UUID operatingEntityId = jdbc.queryForObject(
            """
            select id
            from operating_entities
            where tenant_id = ?
              and entity_code = 'codex-branch-admin'
              and deleted_at is null
            """,
            UUID.class,
            tenantId
        );

        MvcResult storeResult = mockMvc.perform(post(
                    "/api/v1/platform/tenants/{tenantId}/stores",
                    tenantId
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "operatingEntityId":"%s",
                      "storeCode":"codex-branch-a",
                      "storeName":"Codex 分店 A",
                      "status":"active",
                      "timezone":"Asia/Singapore",
                      "locale":"zh-CN",
                      "dateFormat":"DD-MM-YYYY",
                      "timeFormat":"HH:mm",
                      "currency":"SGD",
                      "adminUsername":"codex-branch-a-admin",
                      "adminPassword":"DEF456"
                    }
                    """.formatted(operatingEntityId))
                .cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.store.storeCode").value("codex-branch-a"))
            .andReturn();

        UUID storeId = UUID.fromString(objectMapper
            .readTree(storeResult.getResponse().getContentAsString())
            .path("store")
            .path("id")
            .asText());
        assertThat(countWhere("""
            select count(*)
            from auth_accounts account
            join auth_account_roles role on role.account_id = account.id
            join auth_account_store_access access on access.account_id = account.id
            where account.tenant_id = ?
              and account.username = 'codex-branch-a-admin'
              and account.actor_type = 'staff'
              and account.status = 'active'
              and account.default_store_id = ?
              and role.role_code = 'store_manager'
              and access.tenant_id = ?
              and access.store_id = ?
              and account.deleted_at is null
              and role.deleted_at is null
              and access.deleted_at is null
            """, tenantId, storeId, tenantId, storeId)).isEqualTo(1);

        login("codex-branch-admin", "abc123");
        expectLoginRejected("codex-branch-a-admin", "abc123");
        Cookie branchAdminSession = login("codex-branch-a-admin", "def456");

        MvcResult storesResult = mockMvc.perform(get("/api/v1/me/stores").cookie(branchAdminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        JsonNode stores = objectMapper.readTree(storesResult.getResponse().getContentAsString()).path("stores");
        assertThat(stringsByField(stores, "storeId")).containsExactly(storeId.toString());
        assertThat(storeById(stores, storeId).path("defaultStore").asBoolean()).isTrue();

        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}/stores/{storeId}", tenantId, storeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "adminPassword":"GHI789"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        assertThat(countWhere("""
            select count(*)
            from auth_accounts account
            join auth_account_roles role on role.account_id = account.id
            where account.tenant_id = ?
              and account.username = 'codex-branch-a-admin'
              and account.actor_type = 'staff'
              and role.role_code = 'store_manager'
              and account.deleted_at is null
              and role.deleted_at is null
            """, tenantId)).isEqualTo(1);
        expectLoginRejected("codex-branch-a-admin", "def456");
        login("codex-branch-a-admin", "ghi789");

        UUID alternateAliasId = UUID.randomUUID();
        jdbc.update(
            """
            insert into tenant_host_aliases (
                id, tenant_id, alias_code, alias_type, default_store_id, status
            )
            values (?, ?, 'codex-branch-a-alt', 'store', ?, 'active')
            """,
            alternateAliasId,
            tenantId,
            storeId
        );
        jdbc.update(
            """
            insert into public_host_bindings (
                host_alias_id, tenant_id, host_prefix, hostname, host_type, tls_status
            )
            values (?, ?, 'codex-branch-a-alt', 'codex-branch-a-alt.booking.yumstone.sg', 'store', 'pending')
            """,
            alternateAliasId,
            tenantId
        );

        UUID subscriptionId = UUID.randomUUID();
        jdbc.update(
            """
            insert into tenant_product_subscriptions (
                id, tenant_id, app_key, billing_cycle, status,
                current_period_start, current_period_end, amount, currency
            )
            values (?, ?, 'reservation_queue', 'monthly', 'active',
                    '2026-07-01T00:00:00Z', '2026-08-01T00:00:00Z', 10.00, 'SGD')
            """,
            subscriptionId,
            tenantId
        );
        jdbc.update(
            """
            insert into tenant_product_subscription_items (
                subscription_id, tenant_id, app_key, scope_type, store_id,
                quantity, unit_amount, amount, currency, status
            )
            values (?, ?, 'reservation_queue', 'store', ?, 1, 10.00, 10.00, 'SGD', 'active')
            """,
            subscriptionId,
            tenantId,
            storeId
        );

        mockMvc.perform(delete("/api/v1/platform/tenants/{tenantId}/stores/{storeId}", tenantId, storeId)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.store.id").value(storeId.toString()))
            .andExpect(jsonPath("$.store.deleted").value(true))
            .andExpect(jsonPath("$.store.status").value("inactive"));

        assertThat(countWhere("""
            select count(*)
            from stores
            where tenant_id = ?
              and id = ?
              and status = 'inactive'
              and deleted_at is not null
            """, tenantId, storeId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from auth_accounts
            where tenant_id = ?
              and username = 'codex-branch-a-admin'
              and actor_type = 'staff'
              and status = 'disabled'
              and deleted_at is not null
            """, tenantId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from auth_account_store_access
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """, tenantId, storeId)).isZero();
        assertThat(countWhere("""
            select count(*)
            from tenant_host_aliases
            where tenant_id = ?
              and alias_code = 'codex-branch-a'
              and alias_type = 'store'
              and default_store_id = ?
              and status = 'archived'
              and deleted_at is not null
            """, tenantId, storeId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from tenant_host_aliases
            where tenant_id = ?
              and default_store_id = ?
              and alias_type = 'store'
              and status = 'archived'
              and deleted_at is not null
            """, tenantId, storeId)).isEqualTo(2);
        assertThat(countWhere("""
            select count(*)
            from public_host_bindings
            where tenant_id = ?
              and hostname in (
                  'codex-branch-a.booking.yumstone.sg',
                  'codex-branch-a-alt.booking.yumstone.sg'
              )
              and tls_status = 'archived'
              and deleted_at is not null
            """, tenantId)).isEqualTo(2);
        assertThat(countWhere("""
            select count(*)
            from tenant_product_subscription_items
            where tenant_id = ?
              and store_id = ?
              and status = 'cancelled'
            """, tenantId, storeId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from audit_logs
            where target_type = 'store'
              and target_id = ?
              and operation_code = 'platform.tenant.store.delete'
            """, storeId)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/platform/tenants/{tenantId}/stores", tenantId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.stores.length()").value(0));
        expectLoginRejected("codex-branch-a-admin", "ghi789");
    }

    @Test
    void deletingStoreKeepsStoreManagerAccountWhenOtherActiveStoreAccessRemains() throws Exception {
        Cookie session = login("sysadmin");

        UUID tenantId = createGroupTenant(session, "codex-manager-scope", "Codex 多门店管理员集团", "abc123");
        UUID operatingEntityId = jdbc.queryForObject(
            """
            select id
            from operating_entities
            where tenant_id = ?
              and entity_code = 'codex-manager-scope'
              and deleted_at is null
            """,
            UUID.class,
            tenantId
        );
        UUID firstStoreId = createStore(
            session,
            tenantId,
            operatingEntityId,
            "codex-manager-a",
            "Codex 管理员 A 店",
            "codex-manager-a-admin",
            "DEF456"
        );
        UUID secondStoreId = createStore(
            session,
            tenantId,
            operatingEntityId,
            "codex-manager-b",
            "Codex 管理员 B 店",
            null,
            null
        );
        UUID managerAccountId = jdbc.queryForObject(
            """
            select id
            from auth_accounts
            where tenant_id = ?
              and username = 'codex-manager-a-admin'
              and actor_type = 'staff'
              and deleted_at is null
            """,
            UUID.class,
            tenantId
        );
        jdbc.update(
            """
            insert into auth_account_store_access (account_id, tenant_id, store_id)
            values (?, ?, ?)
            """,
            managerAccountId,
            tenantId,
            secondStoreId
        );

        mockMvc.perform(delete("/api/v1/platform/tenants/{tenantId}/stores/{storeId}", tenantId, firstStoreId)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        assertThat(countWhere("""
            select count(*)
            from auth_accounts
            where id = ?
              and status = 'active'
              and default_store_id = ?
              and deleted_at is null
            """, managerAccountId, secondStoreId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from auth_account_store_access
            where account_id = ?
              and tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """, managerAccountId, tenantId, secondStoreId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from auth_account_store_access
            where account_id = ?
              and tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """, managerAccountId, tenantId, firstStoreId)).isZero();

        Cookie managerSession = login("codex-manager-a-admin", "def456");
        MvcResult storesResult = mockMvc.perform(get("/api/v1/me/stores").cookie(managerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        JsonNode stores = objectMapper.readTree(storesResult.getResponse().getContentAsString()).path("stores");
        assertThat(stringsByField(stores, "storeId")).containsExactly(secondStoreId.toString());
        assertThat(storeById(stores, secondStoreId).path("defaultStore").asBoolean()).isTrue();
    }

    @Test
    void platformAdminCannotCreateBranchStoreManagerWithInvalidPassword() throws Exception {
        Cookie session = login("sysadmin");
        UUID tenantId = createGroupTenant(session, "codex-branch-invalid", "Codex 分店密码校验集团", "abc123");
        UUID operatingEntityId = jdbc.queryForObject(
            """
            select id
            from operating_entities
            where tenant_id = ?
              and entity_code = 'codex-branch-invalid'
              and deleted_at is null
            """,
            UUID.class,
            tenantId
        );

        mockMvc.perform(post(
                    "/api/v1/platform/tenants/{tenantId}/stores",
                    tenantId
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "operatingEntityId":"%s",
                      "storeCode":"codex-branch-b",
                      "storeName":"Codex 分店 B",
                      "status":"active",
                      "timezone":"Asia/Singapore",
                      "locale":"zh-CN",
                      "dateFormat":"DD-MM-YYYY",
                      "timeFormat":"HH:mm",
                      "currency":"SGD",
                      "adminUsername":"codex-branch-b-admin",
                      "adminPassword":"TOO-LONG"
                    }
                    """.formatted(operatingEntityId))
                .cookie(session))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
    }

    @Test
    void tenantStructureBackfillCreatesDefaultOperatingEntityForExistingGroupTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        jdbc.update(
            """
            insert into tenants (
                id, tenant_code, display_name, status,
                default_locale, contact_phone, address, principal_name
            )
            values (?, 'codex-branch-group', 'Codex 分店集团', 'active',
                    'zh-CN', '+6590000111', '集团地址 111', '集团管理员')
            """,
            tenantId
        );

        assertThat(countWhere("select count(*) from operating_entities where tenant_id = ?", tenantId)).isZero();
        assertThat(countWhere("select count(*) from stores where tenant_id = ?", tenantId)).isZero();

        replayTenantStructureDefaultOperatingEntityBackfillMigration();
        replayTenantStructureDefaultOperatingEntityBackfillMigration();

        assertThat(countWhere("""
            select count(*)
            from operating_entities
            where tenant_id = ?
              and entity_code = 'codex-branch-group'
              and display_name = 'Codex 分店集团'
              and status = 'active'
              and default_locale = 'zh-CN'
              and contact_phone = '+6590000111'
              and address = '集团地址 111'
              and principal_name = '集团管理员'
              and deleted_at is null
            """, tenantId)).isEqualTo(1);
        assertThat(countWhere("select count(*) from stores where tenant_id = ?", tenantId)).isZero();
    }

    @Test
    void tenantHostAliasBackfillPersistsExistingTenantCodesAsPrefixes() throws Exception {
        UUID tenantId = UUID.randomUUID();
        jdbc.update(
            """
            insert into tenants (
                id, tenant_code, display_name, status,
                default_locale, contact_phone, address, principal_name
            )
            values (?, 'codex-prefix-tenant', 'Codex 前缀租户', 'active',
                    'zh-CN', '+6590000222', '前缀地址 222', '前缀管理员')
            """,
            tenantId
        );

        assertThat(countWhere("select count(*) from tenant_host_aliases where tenant_id = ?", tenantId)).isZero();

        replayTenantHostAliasBackfillMigration();
        replayTenantHostAliasBackfillMigration();

        assertThat(countWhere("""
            select count(*)
            from tenant_host_aliases
            where tenant_id = ?
              and alias_code = 'codex-prefix-tenant'
              and alias_type = 'tenant'
              and default_store_id is null
              and status = 'active'
              and deleted_at is null
            """, tenantId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from tenant_host_aliases
            where tenant_id = ?
              and alias_type = 'store'
            """, tenantId)).isZero();
    }

    @Test
    void platformAdminMaintainsTenantAdminAuthorizedStores() throws Exception {
        upsertSecondaryStoreForTenantAdminAuthorization();
        Cookie session = login("sysadmin");

        MvcResult currentAccess = mockMvc.perform(get(
                "/api/v1/platform/tenants/{tenantId}/admin-store-access",
                AuthPostgresTestDatabase.VALIDATION_TENANT_ID
            ).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.stores.length()").value(2))
            .andExpect(jsonPath("$.defaultStoreId").value(AuthPostgresTestDatabase.VALIDATION_STORE_ID.toString()))
            .andReturn();
        JsonNode currentAccessBody = objectMapper.readTree(currentAccess.getResponse().getContentAsString());
        assertThat(stringsByField(currentAccessBody.path("stores"), "storeId"))
            .containsExactlyInAnyOrder(
                AuthPostgresTestDatabase.VALIDATION_STORE_ID.toString(),
                SECONDARY_STORE_ID.toString()
            );

        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}", AuthPostgresTestDatabase.VALIDATION_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantCode":"20000000",
                      "displayName":"食刻租户",
                      "status":"active",
                      "defaultLocale":"zh-CN",
                      "contactPhone":"021-393930",
                      "address":"上海市徐汇区示例路 1 号",
                      "principalName":"张店长",
                      "adminStoreIds":["%s","%s"],
                      "defaultAdminStoreId":"%s"
                    }
                    """.formatted(
                        AuthPostgresTestDatabase.VALIDATION_STORE_ID,
                        SECONDARY_STORE_ID,
                        SECONDARY_STORE_ID
                    ))
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        assertThat(countWhere("""
            select count(*)
            from auth_accounts account
            join auth_account_store_access access on access.account_id = account.id
            where account.tenant_id = ?
              and account.username = '20000000'
              and account.actor_type = 'tenant_admin'
              and account.default_store_id = ?
              and access.tenant_id = ?
              and access.store_id in (?, ?)
              and account.deleted_at is null
              and access.deleted_at is null
            """,
            AuthPostgresTestDatabase.VALIDATION_TENANT_ID,
            SECONDARY_STORE_ID,
            AuthPostgresTestDatabase.VALIDATION_TENANT_ID,
            AuthPostgresTestDatabase.VALIDATION_STORE_ID,
            SECONDARY_STORE_ID
        )).isEqualTo(2);

        Cookie tenantAdminSession = login("20000000");
        MvcResult storesResult = mockMvc.perform(get("/api/v1/me/stores").cookie(tenantAdminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        JsonNode stores = objectMapper.readTree(storesResult.getResponse().getContentAsString()).path("stores");
        assertThat(stringsByField(stores, "storeId"))
            .containsExactlyInAnyOrder(
                AuthPostgresTestDatabase.VALIDATION_STORE_ID.toString(),
                SECONDARY_STORE_ID.toString()
            );
        assertThat(storeById(stores, SECONDARY_STORE_ID).path("defaultStore").asBoolean()).isTrue();
    }

    @Test
    void platformAdminCreatesOperatingEntityStoreAndAuthorizesTenantAdminAcrossStores() throws Exception {
        Cookie session = login("sysadmin");

        MvcResult entityResult = mockMvc.perform(post(
                    "/api/v1/platform/tenants/{tenantId}/operating-entities",
                    AuthPostgresTestDatabase.VALIDATION_TENANT_ID
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "entityCode":"codex-lsc106-entity",
                      "displayName":"LSC106 经营主体",
                      "status":"active",
                      "defaultLocale":"en-SG",
                      "contactPhone":"+6590000106",
                      "address":"106 Orchard Road",
                      "principalName":"LSC106 Manager"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.operatingEntity.entityCode").value("codex-lsc106-entity"))
            .andReturn();
        UUID operatingEntityId = UUID.fromString(objectMapper
            .readTree(entityResult.getResponse().getContentAsString())
            .path("operatingEntity")
            .path("id")
            .asText());

        mockMvc.perform(get(
                    "/api/v1/platform/tenants/{tenantId}/operating-entities",
                    AuthPostgresTestDatabase.VALIDATION_TENANT_ID
                )
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.operatingEntities[0].entityCode").value("codex-lsc106-entity"));

        MvcResult storeResult = mockMvc.perform(post(
                    "/api/v1/platform/tenants/{tenantId}/stores",
                    AuthPostgresTestDatabase.VALIDATION_TENANT_ID
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "operatingEntityId":"%s",
                      "storeCode":"codex-lsc106",
                      "storeName":"LSC106 门店",
                      "status":"active",
                      "timezone":"Asia/Singapore",
                      "locale":"en-SG",
                      "dateFormat":"DD-MM-YYYY",
                      "timeFormat":"HH:mm",
                      "currency":"SGD"
                    }
                    """.formatted(operatingEntityId))
                .cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.store.storeCode").value("codex-lsc106"))
            .andExpect(jsonPath("$.store.operatingEntityName").value("LSC106 经营主体"))
            .andReturn();
        UUID storeId = UUID.fromString(objectMapper
            .readTree(storeResult.getResponse().getContentAsString())
            .path("store")
            .path("id")
            .asText());
        assertThat(countWhere("""
            select count(*)
            from tenant_host_aliases
            where tenant_id = ?
              and alias_code = 'codex-lsc106'
              and alias_type = 'store'
              and default_store_id = ?
              and status = 'active'
              and deleted_at is null
            """, AuthPostgresTestDatabase.VALIDATION_TENANT_ID, storeId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from public_host_bindings binding
            join tenant_host_aliases alias on alias.id = binding.host_alias_id
            where binding.tenant_id = ?
              and binding.hostname = 'codex-lsc106.booking.yumstone.sg'
              and binding.host_prefix = 'codex-lsc106'
              and binding.host_type = 'store'
              and binding.tls_status = 'pending'
              and binding.deleted_at is null
              and alias.default_store_id = ?
            """, AuthPostgresTestDatabase.VALIDATION_TENANT_ID, storeId)).isEqualTo(1);

        MvcResult accessResult = mockMvc.perform(get(
                    "/api/v1/platform/tenants/{tenantId}/admin-store-access",
                    AuthPostgresTestDatabase.VALIDATION_TENANT_ID
                )
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        JsonNode accessStores = objectMapper
            .readTree(accessResult.getResponse().getContentAsString())
            .path("stores");
        assertThat(storeById(accessStores, storeId).path("operatingEntityName").asText())
            .isEqualTo("LSC106 经营主体");

        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}", AuthPostgresTestDatabase.VALIDATION_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantCode":"20000000",
                      "displayName":"食刻租户",
                      "status":"active",
                      "defaultLocale":"zh-CN",
                      "adminStoreIds":["%s","%s"],
                      "defaultAdminStoreId":"%s"
                    }
                    """.formatted(
                        AuthPostgresTestDatabase.VALIDATION_STORE_ID,
                        storeId,
                        storeId
                    ))
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        Cookie tenantAdminSession = login("20000000");
        MvcResult storesResult = mockMvc.perform(get("/api/v1/me/stores").cookie(tenantAdminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        JsonNode stores = objectMapper.readTree(storesResult.getResponse().getContentAsString()).path("stores");
        assertThat(stringsByField(stores, "storeId"))
            .contains(AuthPostgresTestDatabase.VALIDATION_STORE_ID.toString(), storeId.toString());
        JsonNode createdStore = storeById(stores, storeId);
        assertThat(createdStore.path("tenantCode").asText()).isEqualTo("20000000");
        assertThat(createdStore.path("operatingEntityId").asText()).isEqualTo(operatingEntityId.toString());
        assertThat(createdStore.path("operatingEntityName").asText()).isEqualTo("LSC106 经营主体");
        assertThat(createdStore.path("defaultStore").asBoolean()).isTrue();

        String appKey = "codex-store-delete-billing";
        ensurePlatformApp(appKey);
        UUID subscriptionId = UUID.randomUUID();
        UUID defaultStoreItemId = UUID.randomUUID();
        UUID deletedStoreItemId = UUID.randomUUID();
        jdbc.update(
            """
            insert into tenant_product_subscriptions (
                id, tenant_id, app_key, billing_cycle, status,
                current_period_start, current_period_end, amount, currency
            )
            values (?, ?, ?, 'monthly', 'active',
                    '2026-07-01T00:00:00Z', '2026-08-01T00:00:00Z', 20.00, 'SGD')
            """,
            subscriptionId,
            AuthPostgresTestDatabase.VALIDATION_TENANT_ID,
            appKey
        );
        jdbc.update(
            """
            insert into tenant_product_subscription_items (
                id, subscription_id, tenant_id, app_key, scope_type, store_id,
                billing_cycle, current_period_start, current_period_end,
                quantity, unit_amount, amount, currency, status
            )
            values
                (?, ?, ?, ?, 'store', ?, 'monthly',
                 '2026-07-01T00:00:00Z', '2026-08-01T00:00:00Z', 1, 10.00, 10.00, 'SGD', 'active'),
                (?, ?, ?, ?, 'store', ?, 'monthly',
                 '2026-07-01T00:00:00Z', '2026-08-01T00:00:00Z', 1, 10.00, 10.00, 'SGD', 'active')
            """,
            defaultStoreItemId,
            subscriptionId,
            AuthPostgresTestDatabase.VALIDATION_TENANT_ID,
            appKey,
            AuthPostgresTestDatabase.VALIDATION_STORE_ID,
            deletedStoreItemId,
            subscriptionId,
            AuthPostgresTestDatabase.VALIDATION_TENANT_ID,
            appKey,
            storeId
        );

        mockMvc.perform(delete(
                    "/api/v1/platform/tenants/{tenantId}/stores/{storeId}",
                    AuthPostgresTestDatabase.VALIDATION_TENANT_ID,
                    storeId
                )
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.store.deleted").value(true));
        assertThat(countWhere("""
            select count(*)
            from tenant_product_subscription_items
            where id = ?
              and status = 'cancelled'
            """, deletedStoreItemId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            """
            select amount
            from tenant_product_subscriptions
            where id = ?
            """,
            java.math.BigDecimal.class,
            subscriptionId
        )).isEqualByComparingTo("10.00");
        mockMvc.perform(post(
                    "/api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/items/{itemId}/renew",
                    AuthPostgresTestDatabase.VALIDATION_TENANT_ID,
                    subscriptionId,
                    deletedStoreItemId
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey":"renew-deleted-store-item",
                      "appKey":"%s",
                      "billingCycle":"monthly",
                      "durationCount":1,
                      "currency":"SGD",
                      "paymentNote":"should be rejected",
                      "version":1
                    }
                    """.formatted(appKey))
                .cookie(session))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("SUBSCRIPTION_ITEM_NOT_FOUND"));
        assertThat(countWhere("""
            select count(*)
            from auth_accounts account
            join auth_account_store_access access on access.account_id = account.id
            where account.tenant_id = ?
              and account.username = '20000000'
              and account.actor_type = 'tenant_admin'
              and account.default_store_id = ?
              and access.tenant_id = ?
              and access.store_id = ?
              and account.deleted_at is null
              and access.deleted_at is null
            """,
            AuthPostgresTestDatabase.VALIDATION_TENANT_ID,
            AuthPostgresTestDatabase.VALIDATION_STORE_ID,
            AuthPostgresTestDatabase.VALIDATION_TENANT_ID,
            AuthPostgresTestDatabase.VALIDATION_STORE_ID
        )).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from auth_account_store_access
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """, AuthPostgresTestDatabase.VALIDATION_TENANT_ID, storeId)).isZero();
        assertThat(countWhere("""
            select count(*)
            from tenant_host_aliases
            where tenant_id = ?
              and alias_code = 'codex-lsc106'
              and default_store_id = ?
              and status = 'archived'
              and deleted_at is not null
            """, AuthPostgresTestDatabase.VALIDATION_TENANT_ID, storeId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from public_host_bindings
            where tenant_id = ?
              and hostname = 'codex-lsc106.booking.yumstone.sg'
              and tls_status = 'archived'
              and deleted_at is not null
            """, AuthPostgresTestDatabase.VALIDATION_TENANT_ID)).isEqualTo(1);

        Cookie tenantAdminAfterDeleteSession = login("20000000");
        MvcResult storesAfterDeleteResult = mockMvc.perform(get("/api/v1/me/stores").cookie(tenantAdminAfterDeleteSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        JsonNode storesAfterDelete = objectMapper
            .readTree(storesAfterDeleteResult.getResponse().getContentAsString())
            .path("stores");
        assertThat(stringsByField(storesAfterDelete, "storeId"))
            .containsExactly(AuthPostgresTestDatabase.VALIDATION_STORE_ID.toString());
        assertThat(storeById(storesAfterDelete, AuthPostgresTestDatabase.VALIDATION_STORE_ID)
            .path("defaultStore")
            .asBoolean()).isTrue();
    }

    @Test
    void platformAdminCannotGrantTenantAdminForeignStoreAccess() throws Exception {
        upsertForeignTenantStore();
        Cookie session = login("sysadmin");

        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}", AuthPostgresTestDatabase.VALIDATION_TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantCode":"20000000",
                      "displayName":"食刻租户",
                      "status":"active",
                      "defaultLocale":"zh-CN",
                      "adminStoreIds":["%s","%s"],
                      "defaultAdminStoreId":"%s"
                    }
                    """.formatted(
                        AuthPostgresTestDatabase.VALIDATION_STORE_ID,
                        FOREIGN_STORE_ID,
                        AuthPostgresTestDatabase.VALIDATION_STORE_ID
                    ))
                .cookie(session))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));

        mockMvc.perform(delete(
                    "/api/v1/platform/tenants/{tenantId}/stores/{storeId}",
                    AuthPostgresTestDatabase.VALIDATION_TENANT_ID,
                    FOREIGN_STORE_ID
                )
                .cookie(session))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("STORE_NOT_FOUND"));
        assertThat(countWhere("""
            select count(*)
            from stores
            where tenant_id = ?
              and id = ?
              and status = 'active'
              and deleted_at is null
            """, FOREIGN_TENANT_ID, FOREIGN_STORE_ID)).isEqualTo(1);
    }

    @Test
    void tenantOnboardingBackfillMigrationRepairsExistingTenantAdminWithoutStoreScope() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        jdbc.update(
            """
            insert into tenants (
                id, tenant_code, display_name, status,
                default_locale, contact_phone, address
            )
            values (?, 'codex-backfill', 'Codex 回填租户', 'active', 'zh-CN', '021-000008', '上海市回填路 8 号')
            """,
            tenantId
        );
        jdbc.update(
            """
            insert into auth_accounts (
                id, tenant_id, username, display_name, actor_type,
                status, password_hash, password_algo
            )
            values (?, ?, 'codex-backfill', 'Codex 回填租户', 'tenant_admin',
                'active', ?, 'bcrypt-lowercase-v1')
            """,
            accountId,
            tenantId,
            PASSWORD_393930_HASH
        );

        assertThat(countWhere("select count(*) from stores where tenant_id = ?", tenantId)).isZero();

        replayTenantOnboardingBackfillMigration();
        replayTenantOnboardingBackfillMigration();

        UUID storeId = jdbc.queryForObject(
            """
            select id
            from stores
            where tenant_id = ?
              and store_code = 'codex-backfill'
              and status = 'active'
              and share_contact_phone = '021-000008'
              and share_address = '上海市回填路 8 号'
              and deleted_at is null
            """,
            UUID.class,
            tenantId
        );
        assertThat(storeId).isNotNull();
        assertThat(countWhere("select count(*) from stores where tenant_id = ? and deleted_at is null", tenantId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from auth_accounts account
            join auth_account_store_access access on access.account_id = account.id
            where account.id = ?
              and account.default_store_id = ?
              and access.tenant_id = ?
              and access.store_id = ?
              and access.deleted_at is null
            """, accountId, storeId, tenantId, storeId)).isEqualTo(1);
    }

    @Test
    void tenantSubscriptionZeroAmountBackfillUsesActiveProductLinePrice() throws Exception {
        Cookie session = login("sysadmin");
        UUID tenantId = createTenant(session, "codex-price", "Codex 价格租户");
        jdbc.update(
            """
            update platform_product_line_prices
            set amount = 128.00,
                currency = 'SGD',
                status = 'active'
            where app_key = 'reservation_queue'
              and billing_cycle = 'monthly'
            """
        );
        jdbc.update(
            """
            insert into tenant_product_subscriptions (
                tenant_id, app_key, billing_cycle, status,
                current_period_start, current_period_end, amount, currency
            )
            values (
                ?, 'reservation_queue', 'monthly', 'active',
                '2026-07-01T00:00:00Z', '2026-09-01T00:00:00Z', 0, 'SGD'
            )
            """,
            tenantId
        );

        replayTenantSubscriptionZeroAmountBackfillMigration();
        replayTenantSubscriptionZeroAmountBackfillMigration();

        assertThat(jdbc.queryForObject(
            """
            select amount
            from tenant_product_subscriptions
            where tenant_id = ?
              and app_key = 'reservation_queue'
            """,
            java.math.BigDecimal.class,
            tenantId
        )).isEqualByComparingTo("256.00");
    }

    @Test
    void platformTenantCrudWritesAuditLogs() throws Exception {
        Cookie session = login("sysadmin");
        UUID sysadminActorId = jdbc.queryForObject(
            "select id from auth_accounts where username = 'sysadmin'",
            UUID.class
        );
        UUID createdTenantId = createTenant(session, "codex-audit", "Codex 审计租户");

        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}", createdTenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantCode":"codex-audit",
                      "displayName":"Codex 审计租户修订",
                      "status":"active",
                      "defaultLocale":"zh-CN",
                      "contactPhone":"021-000009",
                      "address":"上海市审计路 9 号",
                      "principalName":"审计负责人",
                      "password":"QWE123"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/platform/tenants/{tenantId}", createdTenantId).cookie(session))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/platform/tenants/{tenantId}/restore", createdTenantId).cookie(session))
            .andExpect(status().isOk());

        assertThat(jdbc.queryForList("""
            select operation_code
            from audit_logs
            where target_type = 'tenant'
              and target_id = ?
              and actor_id = ?
              and actor_type = 'platform_admin'
              and source = 'staff'
              and tenant_id is null
              and store_id is null
            order by occurred_at, created_at
            """, String.class, createdTenantId, sysadminActorId)).containsExactly(
                "platform.tenant.create",
                "platform.tenant.update",
                "platform.tenant.delete",
                "platform.tenant.restore"
            );
        assertThat(auditMetadataValue(createdTenantId, "platform.tenant.create", "tenantCode")).isEqualTo("codex-audit");
        assertThat(auditMetadataValue(createdTenantId, "platform.tenant.create", "passwordChanged")).isEqualTo("false");
        assertThat(auditMetadata(createdTenantId, "platform.tenant.create")).doesNotContain("abc123");
        assertThat(auditMetadata(createdTenantId, "platform.tenant.update"))
            .contains("\"displayName\"")
            .contains("\"contactPhone\"")
            .doesNotContain("QWE123")
            .doesNotContain("qwe123");
        assertThat(auditMetadataValue(createdTenantId, "platform.tenant.update", "passwordChanged")).isEqualTo("true");
    }

    private UUID createTenant(Cookie session, String tenantCode, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantCode":"%s",
                      "displayName":"%s",
                      "status":"active",
                      "defaultLocale":"zh-CN",
                      "contactPhone":"%s",
                      "address":"%s",
                      "principalName":"%s",
                      "initialPassword":"abc123"
                    }
                    """.formatted(
                        tenantCode,
                        displayName,
                        tenantCode.endsWith("beta") ? "021-000002" : "021-000001",
                        tenantCode.endsWith("beta") ? "上海市乙路 2 号" : "上海市甲路 1 号",
                        tenantCode.endsWith("beta") ? "乙负责人" : "甲负责人"
                    ))
                .cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenant.tenantCode").value(tenantCode))
            .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).path("tenant").path("id").asText();
        return UUID.fromString(id);
    }

    private UUID createGroupTenant(
        Cookie session,
        String tenantCode,
        String displayName,
        String initialPassword
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantCode":"%s",
                      "displayName":"%s",
                      "status":"active",
                      "defaultLocale":"zh-CN",
                      "contactPhone":"+6590000999",
                      "address":"集团地址 999",
                      "principalName":"集团负责人",
                      "initialPassword":"%s",
                      "onboardingMode":"group_multi_store"
                    }
                    """.formatted(tenantCode, displayName, initialPassword))
                .cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenant.tenantCode").value(tenantCode))
            .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).path("tenant").path("id").asText();
        return UUID.fromString(id);
    }

    private UUID createStore(
        Cookie session,
        UUID tenantId,
        UUID operatingEntityId,
        String storeCode,
        String storeName,
        String adminUsername,
        String adminPassword
    ) throws Exception {
        String adminFields = adminPassword == null
            ? ""
            : """
              ,
              "adminUsername":"%s",
              "adminPassword":"%s"
            """.formatted(adminUsername, adminPassword);
        MvcResult result = mockMvc.perform(post("/api/v1/platform/tenants/{tenantId}/stores", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "operatingEntityId":"%s",
                      "storeCode":"%s",
                      "storeName":"%s",
                      "status":"active",
                      "timezone":"Asia/Singapore",
                      "locale":"zh-CN",
                      "dateFormat":"DD-MM-YYYY",
                      "timeFormat":"HH:mm",
                      "currency":"SGD"%s
                    }
                    """.formatted(operatingEntityId, storeCode, storeName, adminFields))
                .cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.store.storeCode").value(storeCode))
            .andReturn();
        return UUID.fromString(objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("store")
            .path("id")
            .asText());
    }

    private void ensurePlatformApp(String appKey) {
        jdbc.update(
            """
            insert into platform_apps (
                app_key, app_name, status, default_entry_route, description, sort_order, config_json
            )
            values (?, ?, 'active', '/stores/:storeId/staff', 'Codex integration test app.', 990, '{}'::jsonb)
            on conflict (app_key) do update
            set app_name = excluded.app_name,
                status = excluded.status,
                default_entry_route = excluded.default_entry_route,
                description = excluded.description,
                updated_at = now()
            """,
            appKey,
            appKey
        );
    }

    private Cookie login(String username) throws Exception {
        return login(username, "393930");
    }

    private Cookie login(String username, String password) throws Exception {
        SliderTarget target = createSliderTarget();
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s","captchaId":"%s","captchaX":%d}
                    """.formatted(username, password, target.challengeId(), target.targetX())))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("RPB_SESSION"))
            .andReturn();
        return login.getResponse().getCookie("RPB_SESSION");
    }

    private void expectLoginRejected(String username, String password) throws Exception {
        SliderTarget target = createSliderTarget();
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s","captchaId":"%s","captchaX":%d}
                    """.formatted(username, password, target.challengeId(), target.targetX())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    private SliderTarget createSliderTarget() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/captcha/slider"))
            .andExpect(status().isOk())
            .andReturn();

        String challengeId = objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("challenge")
            .path("challengeId")
            .asText();
        int targetX = jdbc.queryForObject(
            "select target_x from auth_slider_captcha_challenges where id = ?",
            Integer.class,
            UUID.fromString(challengeId)
        );
        return new SliderTarget(challengeId, targetX);
    }

    private int countWhere(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private JsonNode storeById(JsonNode stores, UUID storeId) {
        for (JsonNode store : stores) {
            if (storeId.toString().equals(store.path("storeId").asText())) {
                return store;
            }
        }
        throw new AssertionError("store not found: " + storeId);
    }

    private static java.util.List<String> stringsByField(JsonNode array, String fieldName) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
            .map(node -> node.path(fieldName).asText())
            .toList();
    }

    private void upsertSecondaryStoreForTenantAdminAuthorization() {
        jdbc.update(
            """
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency
            )
            values (?, ?, 'lsc106', 'LSC106 门店', 'active',
                    'Asia/Singapore', 'en-SG', 'DD-MM-YYYY', 'HH:mm', 'SGD')
            on conflict (id) do update
            set store_code = excluded.store_code,
                display_name = excluded.display_name,
                status = excluded.status,
                locale = excluded.locale,
                deleted_at = null,
                updated_at = now(),
                version = stores.version + 1
            """,
            SECONDARY_STORE_ID,
            AuthPostgresTestDatabase.VALIDATION_TENANT_ID
        );
    }

    private void upsertForeignTenantStore() {
        jdbc.update(
            """
            insert into tenants (id, tenant_code, display_name, status, default_locale)
            values (?, 'codex-foreign', 'Codex 外部租户', 'active', 'zh-CN')
            on conflict (id) do update
            set tenant_code = excluded.tenant_code,
                display_name = excluded.display_name,
                status = excluded.status,
                deleted_at = null,
                updated_at = now(),
                version = tenants.version + 1
            """,
            FOREIGN_TENANT_ID
        );
        jdbc.update(
            """
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency
            )
            values (?, ?, 'foreign-store', '外部租户门店', 'active',
                    'Asia/Singapore', 'zh-CN', 'DD-MM-YYYY', 'HH:mm', 'SGD')
            on conflict (id) do update
            set store_code = excluded.store_code,
                display_name = excluded.display_name,
                status = excluded.status,
                deleted_at = null,
                updated_at = now(),
                version = stores.version + 1
            """,
            FOREIGN_STORE_ID,
            FOREIGN_TENANT_ID
        );
    }

    private void replayTenantOnboardingBackfillMigration() throws Exception {
        jdbc.execute(Files.readString(
            Path.of("src/main/resources/db/migration/V022__tenant_onboarding_default_store_backfill.sql"),
            StandardCharsets.UTF_8
        ));
    }

    private void replayTenantSubscriptionZeroAmountBackfillMigration() throws Exception {
        jdbc.execute(Files.readString(
            Path.of("src/main/resources/db/migration/V023__tenant_subscription_zero_amount_price_backfill.sql"),
            StandardCharsets.UTF_8
        ));
    }

    private void replayTenantStructureDefaultOperatingEntityBackfillMigration() throws Exception {
        jdbc.execute(Files.readString(
            Path.of("src/main/resources/db/migration/V038__tenant_default_operating_entity_backfill.sql"),
            StandardCharsets.UTF_8
        ));
    }

    private void replayTenantHostAliasBackfillMigration() throws Exception {
        jdbc.execute(Files.readString(
            Path.of("src/main/resources/db/migration/V039__tenant_host_alias_backfill.sql"),
            StandardCharsets.UTF_8
        ));
    }

    private String auditMetadata(UUID tenantId, String operationCode) {
        return jdbc.queryForObject(
            """
            select metadata::text
            from audit_logs
            where target_id = ?
              and operation_code = ?
            """,
            String.class,
            tenantId,
            operationCode
        );
    }

    private String auditMetadataValue(UUID tenantId, String operationCode, String key) {
        return jdbc.queryForObject(
            """
            select metadata ->> ?
            from audit_logs
            where target_id = ?
              and operation_code = ?
            """,
            String.class,
            key,
            tenantId,
            operationCode
        );
    }

    private record SliderTarget(String challengeId, int targetX) {
    }
}
