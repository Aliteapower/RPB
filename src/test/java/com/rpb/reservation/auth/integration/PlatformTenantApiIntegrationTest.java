package com.rpb.reservation.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
        jdbc.update("delete from auth_accounts where username like 'codex-%'");
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
