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

        mockMvc.perform(delete("/api/v1/platform/tenants/{tenantId}", createdTenantId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenant.deleted").value(true));
        assertThat(countWhere("select count(*) from tenants where id = ? and deleted_at is not null", createdTenantId)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/platform/tenants/{tenantId}/restore", createdTenantId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenant.deleted").value(false));
        assertThat(countWhere("select count(*) from tenants where id = ? and deleted_at is null", createdTenantId)).isEqualTo(1);
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

        login("codex-login", "abc123");
    }

    @Test
    void creatingGroupTenantDoesNotBootstrapStoresUntilStructureIsConfigured() throws Exception {
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

        assertThat(countWhere("select count(*) from operating_entities where tenant_id = ?", tenantId)).isZero();
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
