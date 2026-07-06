package com.rpb.reservation.auth.integration;

import static com.rpb.reservation.auth.integration.AuthPostgresTestDatabase.VALIDATION_STORE_ID;
import static com.rpb.reservation.auth.integration.AuthPostgresTestDatabase.VALIDATION_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantAdminApiIntegrationTest {
    private static final AuthPostgresTestDatabase DATABASE = AuthPostgresTestDatabase.startWithValidationStore();
    private static final String PASSWORD_393930_HASH = "$2a$10$ktA3gOgzus6v0bsJqw53.OerYPoQT6oet7NDdkmNhYYZaKH9ix9Vy";
    private static final UUID SECONDARY_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000984");

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
        jdbc.update("delete from audit_logs where operation_code = 'tenant_admin.account.self_update'");
        jdbc.update("delete from auth_account_roles where account_id in (select id from auth_accounts where username like 'codex-%')");
        jdbc.update("delete from auth_account_permissions where account_id in (select id from auth_accounts where username like 'codex-%')");
        jdbc.update("delete from auth_account_store_access where account_id in (select id from auth_accounts where username like 'codex-%')");
        jdbc.update("delete from auth_accounts where username like 'codex-%'");
        jdbc.update("delete from dining_tables where table_code like 'CX%'");
        jdbc.update("delete from store_areas where area_code like 'CX%' or display_name like 'Codex%'");
        jdbc.update("""
            delete from customers
            where tenant_id = ?
              and (
                  customer_code like 'C-CODEX-%'
                  or display_name like 'Codex%'
                  or phone_e164 like '+659888%'
                  or email like 'codex-customer-%@example.test'
              )
            """, VALIDATION_TENANT_ID);
        jdbc.update("""
            update stores
            set share_display_name = null,
                share_address = null,
                google_map_url = null,
                share_contact_phone = null,
                share_email = null,
                whatsapp_business_phone_e164 = null,
                reservation_share_note = null,
                reservation_share_template = null
            where id = ?
              and tenant_id = ?
            """, VALIDATION_STORE_ID, VALIDATION_TENANT_ID);
        jdbc.update(
            """
            update auth_accounts
            set password_hash = ?,
                display_name = case username
                    when '20000000' then '租户管理员'
                    when '1000' then '租户员工'
                    else display_name
                end,
                contact_phone = case username when '20000000' then null else contact_phone end,
                email = case username when '20000000' then null else email end,
                status = 'active',
                failed_login_count = 0,
                locked_until_at = null,
                updated_at = now(),
                version = version + 1
            where username in ('sysadmin', '20000000', '1000')
            """,
            PASSWORD_393930_HASH
        );
        jdbc.update("""
            update stores
            set share_email = null,
                whatsapp_business_phone_e164 = null
            where id = ?
              and tenant_id = ?
            """, VALIDATION_STORE_ID, VALIDATION_TENANT_ID);
    }

    @Test
    void tenantAdminMaintainsStaffTablesAndSettingsInsideOwnStore() throws Exception {
        Cookie session = login("20000000");

        mockMvc.perform(get(basePath() + "/staff")
                .param("keyword", "1000")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.staff[0].employeeNo").value("1000"))
            .andExpect(jsonPath("$.staff[0].accountType").value("staff"))
            .andExpect(jsonPath("$.staff[0].self").value(false))
            .andExpect(jsonPath("$.staff[0].statusEditable").value(true));

        mockMvc.perform(get(basePath() + "/settings").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.settings.dateFormat").value("DD-MM-YYYY"));

        UUID staffId = createStaff(session, "codex-1001", "Codex 员工");

        mockMvc.perform(get(basePath() + "/staff/{staffId}", staffId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.staff.employeeNo").value("codex-1001"));

        mockMvc.perform(patch(basePath() + "/staff/{staffId}", staffId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"Codex 员工修订",
                      "phone":"13800001001",
                      "email":"codex-1001-new@example.test",
                      "status":"active",
                      "password":"QWE123"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.staff.employeeNo").value("codex-1001"))
            .andExpect(jsonPath("$.staff.name").value("Codex 员工修订"))
            .andExpect(jsonPath("$.staff.phone").value("13800001001"))
            .andExpect(jsonPath("$.staff.email").value("codex-1001-new@example.test"));

        login("codex-1001", "qwe123");

        UUID tableId = createTable(session);
        mockMvc.perform(get(basePath() + "/tables/{tableId}", tableId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.table.tableCode").value("CX01"));

        mockMvc.perform(patch(basePath() + "/tables/{tableId}", tableId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "areaName":"Codex 包间",
                      "tableCode":"CX02",
                      "capacity":6,
                      "enabled":false,
                      "areaSortOrder":20,
                      "tableSortOrder":3
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.table.tableCode").value("CX02"))
            .andExpect(jsonPath("$.table.areaName").value("Codex 包间"))
            .andExpect(jsonPath("$.table.capacity").value(6))
            .andExpect(jsonPath("$.table.enabled").value(false))
            .andExpect(jsonPath("$.table.areaSortOrder").value(20))
            .andExpect(jsonPath("$.table.tableSortOrder").value(3));

        assertThat(countWhere("""
            select count(*)
            from dining_tables table_record
            join store_areas area on area.id = table_record.area_id
            where table_record.id = ?
              and table_record.tenant_id = ?
              and table_record.store_id = ?
              and table_record.status = 'inactive'
              and table_record.sort_order = 3
              and area.sort_order = 20
              and area.display_name = 'Codex 包间'
            """, tableId, VALIDATION_TENANT_ID, VALIDATION_STORE_ID)).isEqualTo(1);

        mockMvc.perform(patch(basePath() + "/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storeName":"食刻门店",
                      "timezone":"Asia/Shanghai",
                      "locale":"zh-CN",
                      "dateFormat":"yyyy-MM-dd",
                      "timeFormat":"HH:mm",
                      "currency":"CNY",
                      "reservationHoldMinutes":18,
                      "queueCallHoldMinutes":4,
                      "expectedDiningMinutes":88
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.settings.storeName").value("食刻门店"))
            .andExpect(jsonPath("$.settings.dateFormat").value("DD-MM-YYYY"))
            .andExpect(jsonPath("$.settings.reservationHoldMinutes").value(18))
            .andExpect(jsonPath("$.settings.queueCallHoldMinutes").value(4))
            .andExpect(jsonPath("$.settings.expectedDiningMinutes").value(88));

        assertThat(countWhere("""
            select count(*)
            from stores store
            join store_policies policy on policy.tenant_id = store.tenant_id and policy.store_id = store.id
            where store.id = ?
              and store.tenant_id = ?
              and store.display_name = '食刻门店'
              and store.date_format = 'DD-MM-YYYY'
              and store.currency = 'CNY'
              and policy.reservation_hold_minutes = 18
              and policy.queue_call_hold_minutes = 4
              and policy.expected_dining_minutes = 88
              and policy.effective_to_at is null
              and policy.deleted_at is null
            """, VALIDATION_STORE_ID, VALIDATION_TENANT_ID)).isEqualTo(1);
    }

    @Test
    void tenantAdminMaintainsOnlyOwnAdministratorAccountFromStaffManagement() throws Exception {
        Cookie session = login("20000000");
        UUID tenantAdminAccountId = accountId("20000000");

        mockMvc.perform(get(basePath() + "/staff/me").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.staff.id").value(tenantAdminAccountId.toString()))
            .andExpect(jsonPath("$.staff.employeeNo").value("20000000"))
            .andExpect(jsonPath("$.staff.name").value("租户管理员"))
            .andExpect(jsonPath("$.staff.accountType").value("tenant_admin"))
            .andExpect(jsonPath("$.staff.self").value(true))
            .andExpect(jsonPath("$.staff.editable").value(true))
            .andExpect(jsonPath("$.staff.statusEditable").value(false));

        mockMvc.perform(patch(basePath() + "/staff/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"租户负责人",
                      "phone":"+6598765432",
                      "email":"tenant-admin@example.test",
                      "password":"ADM123"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.staff.id").value(tenantAdminAccountId.toString()))
            .andExpect(jsonPath("$.staff.employeeNo").value("20000000"))
            .andExpect(jsonPath("$.staff.name").value("租户负责人"))
            .andExpect(jsonPath("$.staff.phone").value("+6598765432"))
            .andExpect(jsonPath("$.staff.email").value("tenant-admin@example.test"))
            .andExpect(jsonPath("$.staff.accountType").value("tenant_admin"))
            .andExpect(jsonPath("$.staff.self").value(true))
            .andExpect(jsonPath("$.staff.statusEditable").value(false));

        login("20000000", "adm123");

        assertThat(countWhere("""
            select count(*)
            from auth_accounts
            where id = ?
              and tenant_id = ?
              and username = '20000000'
              and actor_type = 'tenant_admin'
              and display_name = '租户负责人'
              and contact_phone = '+6598765432'
              and email = 'tenant-admin@example.test'
              and status = 'active'
            """, tenantAdminAccountId, VALIDATION_TENANT_ID)).isEqualTo(1);

        assertThat(countWhere("""
            select count(*)
            from audit_logs
            where tenant_id = ?
              and store_id = ?
              and operation_code = 'tenant_admin.account.self_update'
              and target_type = 'auth_account'
              and target_id = ?
              and actor_type = 'tenant_admin'
              and actor_id = ?
              and source = 'staff'
              and metadata ->> 'passwordChanged' = 'true'
              and jsonb_exists(metadata -> 'changedFields', 'password')
            """, VALIDATION_TENANT_ID, VALIDATION_STORE_ID, tenantAdminAccountId, tenantAdminAccountId)).isEqualTo(1);

        mockMvc.perform(patch(basePath() + "/staff/{staffId}", tenantAdminAccountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"不应被普通员工接口修改",
                      "status":"disabled",
                      "password":"BAD123"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("STAFF_NOT_FOUND"));
    }

    @Test
    void tenantAdminMaintainsReservationShareProfileAndRejectsUnknownTemplateVariable() throws Exception {
        Cookie session = login("20000000");

        mockMvc.perform(get(basePath() + "/share-profile").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.shareProfile.storeDisplayName").isNotEmpty())
            .andExpect(jsonPath("$.shareProfile.reservationShareTemplate").value(org.hamcrest.Matchers.containsString("尊敬的 {{contactName}} {{guestSalutation}}")))
            .andExpect(jsonPath("$.shareProfile.defaultReservationShareTemplate").value(org.hamcrest.Matchers.containsString("{{reservationNo}}")))
            .andExpect(jsonPath("$.shareProfile.shareEmail").value(""))
            .andExpect(jsonPath("$.shareProfile.whatsappBusinessPhoneE164").value(""))
            .andExpect(jsonPath("$.shareProfile.usesDefaultReservationShareTemplate").value(true))
            .andExpect(jsonPath("$.shareProfile.availableVariables[0]").exists());

        mockMvc.perform(patch(basePath() + "/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName":"食刻租户",
                      "defaultLocale":"zh-CN",
                      "contactPhone":"021-393930",
                      "address":"上海市徐汇区示例路 1 号",
                      "principalName":"张店长"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk());

        mockMvc.perform(patch(basePath() + "/share-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "shareDisplayName":"食刻订位中心",
                      "googleMapUrl":"https://maps.app.goo.gl/rpb",
                      "shareEmail":"booking@example.test",
                      "whatsappBusinessPhoneE164":"+6588880000",
                      "reservationShareNote":"请提前 10 分钟到店",
                      "reservationShareTemplate":"门店：{{storeName}}\\n编号：{{reservationNo}}\\n地图：{{googleMapUrl}}"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shareProfile.shareDisplayName").value("食刻订位中心"))
            .andExpect(jsonPath("$.shareProfile.shareAddress").value("上海市徐汇区示例路 1 号"))
            .andExpect(jsonPath("$.shareProfile.googleMapUrl").value("https://maps.app.goo.gl/rpb"))
            .andExpect(jsonPath("$.shareProfile.shareEmail").value("booking@example.test"))
            .andExpect(jsonPath("$.shareProfile.whatsappBusinessPhoneE164").value("+6588880000"))
            .andExpect(jsonPath("$.shareProfile.shareContactPhone").value("021-393930"))
            .andExpect(jsonPath("$.shareProfile.reservationShareNote").value("请提前 10 分钟到店"))
            .andExpect(jsonPath("$.shareProfile.usesDefaultReservationShareTemplate").value(false));

        mockMvc.perform(get("/api/v1/public/stores/{storeId}/booking/context", VALIDATION_STORE_ID)
                .param("businessDate", "2026-06-29"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.store.googleMapUrl").value("https://maps.app.goo.gl/rpb"))
            .andExpect(jsonPath("$.store.shareContactPhone").value("021-393930"))
            .andExpect(jsonPath("$.store.shareEmail").value("booking@example.test"))
            .andExpect(jsonPath("$.store.whatsappBusinessPhoneE164").value("+6588880000"));

        mockMvc.perform(post(basePath() + "/share-profile/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "shareDisplayName":"食刻订位中心",
                      "googleMapUrl":"https://maps.app.goo.gl/rpb",
                      "shareEmail":"booking@example.test",
                      "whatsappBusinessPhoneE164":"+6588880000",
                      "reservationShareNote":"请提前 10 分钟到店",
                      "reservationShareTemplate":"门店：{{storeName}}\\n编号：{{reservationNo}}\\n地图：{{googleMapUrl}}"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.preview.shareText").value(org.hamcrest.Matchers.containsString("门店：食刻订位中心")))
            .andExpect(jsonPath("$.preview.shareText").value(org.hamcrest.Matchers.containsString("地图：https://maps.app.goo.gl/rpb")));

        mockMvc.perform(patch(basePath() + "/share-profile/template")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reservationShareTemplate":"桌位：{{tableCode}}\\n保留：{{holdMinutes}}分钟\\n电话：{{storePhone}}"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shareProfile.shareDisplayName").value("食刻订位中心"))
            .andExpect(jsonPath("$.shareProfile.shareAddress").value("上海市徐汇区示例路 1 号"))
            .andExpect(jsonPath("$.shareProfile.googleMapUrl").value("https://maps.app.goo.gl/rpb"))
            .andExpect(jsonPath("$.shareProfile.shareEmail").value("booking@example.test"))
            .andExpect(jsonPath("$.shareProfile.whatsappBusinessPhoneE164").value("+6588880000"))
            .andExpect(jsonPath("$.shareProfile.shareContactPhone").value("021-393930"))
            .andExpect(jsonPath("$.shareProfile.reservationShareNote").value("请提前 10 分钟到店"))
            .andExpect(jsonPath("$.shareProfile.reservationShareTemplate").value(org.hamcrest.Matchers.containsString("{{tableCode}}")))
            .andExpect(jsonPath("$.shareProfile.usesDefaultReservationShareTemplate").value(false));

        mockMvc.perform(post(basePath() + "/share-profile/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reservationShareTemplate":"地址：{{storeAddress}}\\n地图：{{googleMapUrl}}\\n电话：{{storePhone}}\\n提示：{{arrivalNote}}\\n桌位：{{tableCode}}\\n保留：{{holdMinutes}}分钟"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.preview.shareText").value(org.hamcrest.Matchers.containsString("地址：上海市徐汇区示例路 1 号")))
            .andExpect(jsonPath("$.preview.shareText").value(org.hamcrest.Matchers.containsString("地图：https://maps.app.goo.gl/rpb")))
            .andExpect(jsonPath("$.preview.shareText").value(org.hamcrest.Matchers.containsString("电话：021-393930")))
            .andExpect(jsonPath("$.preview.shareText").value(org.hamcrest.Matchers.containsString("提示：请提前 10 分钟到店")))
            .andExpect(jsonPath("$.preview.shareText").value(org.hamcrest.Matchers.containsString("桌位：A01")))
            .andExpect(jsonPath("$.preview.shareText").value(org.hamcrest.Matchers.containsString("保留：15分钟")));

        mockMvc.perform(patch(basePath() + "/share-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "whatsappBusinessPhoneE164":"6588880000"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));

        mockMvc.perform(patch(basePath() + "/share-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reservationShareTemplate":"{{unsupportedVariable}}"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("TEMPLATE_UNKNOWN_VARIABLE"));

        mockMvc.perform(post(basePath() + "/share-profile/default-template").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.shareProfile.usesDefaultReservationShareTemplate").value(true))
            .andExpect(jsonPath("$.shareProfile.reservationShareTemplate").value(org.hamcrest.Matchers.containsString("尊敬的 {{contactName}} {{guestSalutation}}")));

        assertThat(countWhere("""
            select count(*)
            from stores
            where id = ?
              and tenant_id = ?
              and share_display_name = '食刻订位中心'
              and share_address = '上海市徐汇区示例路 1 号'
              and google_map_url = 'https://maps.app.goo.gl/rpb'
              and share_contact_phone = '021-393930'
              and share_email = 'booking@example.test'
              and whatsapp_business_phone_e164 = '+6588880000'
              and reservation_share_note = '请提前 10 分钟到店'
              and reservation_share_template like '%尊敬的 {{contactName}} {{guestSalutation}}%'
              and reservation_share_template like '%{{reservationNo}}%'
            """, VALIDATION_STORE_ID, VALIDATION_TENANT_ID)).isEqualTo(1);
    }

    @Test
    void tenantAdminNormalizesSingaporeLocalPhoneForReservationShareProfile() throws Exception {
        Cookie session = login("20000000");

        mockMvc.perform(patch(basePath() + "/share-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "shareDisplayName":"食刻订位中心",
                      "whatsappBusinessPhoneE164":"68681234"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.shareProfile.whatsappBusinessPhoneE164").value("+6568681234"));

        assertThat(countWhere("""
            select count(*)
            from stores
            where id = ?
              and tenant_id = ?
              and whatsapp_business_phone_e164 = '+6568681234'
            """, VALIDATION_STORE_ID, VALIDATION_TENANT_ID)).isEqualTo(1);
    }

    @Test
    void tenantAdminMaintainsOwnTenantProfileAndLogo() throws Exception {
        Cookie session = login("20000000");
        upsertSecondaryStoreForProfileSync();

        mockMvc.perform(get(basePath() + "/profile").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.profile.tenantCode").value("20000000"))
            .andExpect(jsonPath("$.profile.status").value("active"));

        mockMvc.perform(patch(basePath() + "/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName":"食刻租户",
                      "defaultLocale":"zh-CN",
                      "contactPhone":"021-393930",
                      "address":"上海市徐汇区示例路 1 号",
                      "principalName":"张店长"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile.displayName").value("食刻租户"))
            .andExpect(jsonPath("$.profile.contactPhone").value("021-393930"))
            .andExpect(jsonPath("$.profile.address").value("上海市徐汇区示例路 1 号"))
            .andExpect(jsonPath("$.profile.principalName").value("张店长"));

        assertThat(countWhere("""
            select count(*)
            from tenants
            where id = ?
              and tenant_code = '20000000'
              and status = 'active'
              and display_name = '食刻租户'
              and default_locale = 'zh-CN'
              and contact_phone = '021-393930'
              and address = '上海市徐汇区示例路 1 号'
              and principal_name = '张店长'
            """, VALIDATION_TENANT_ID)).isEqualTo(1);

        assertThat(countWhere("""
            select count(*)
            from stores
            where tenant_id = ?
              and id in (?, ?)
              and share_address = '上海市徐汇区示例路 1 号'
              and share_contact_phone = '021-393930'
            """, VALIDATION_TENANT_ID, VALIDATION_STORE_ID, SECONDARY_STORE_ID)).isEqualTo(2);

        MockMultipartFile logo = new MockMultipartFile(
            "file",
            "tenant-logo.png",
            "image/png",
            pngHeader()
        );
        mockMvc.perform(multipart(basePath() + "/profile/logo")
                .file(logo)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile.logoMediaUrl").isNotEmpty());

        UUID logoAssetId = jdbc.queryForObject(
            "select logo_media_asset_id from tenants where id = ?",
            UUID.class,
            VALIDATION_TENANT_ID
        );
        assertThat(logoAssetId).isNotNull();

        mockMvc.perform(get(basePath() + "/profile/logo/media/{assetId}", logoAssetId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("image/png"));

        mockMvc.perform(delete(basePath() + "/profile/logo").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile.logoMediaUrl").doesNotExist());

        UUID clearedLogoAssetId = jdbc.queryForObject(
            "select logo_media_asset_id from tenants where id = ?",
            UUID.class,
            VALIDATION_TENANT_ID
        );
        assertThat(clearedLogoAssetId).isNull();
    }

    @Test
    void tenantAdminListsTablesByAreaAndChildSortOrder() throws Exception {
        Cookie session = login("20000000");

        createTable(session, "Codex 二楼", "CX20", 4, true, 20, 1);
        createTable(session, "Codex 大厅", "CX12", 2, true, 10, 20);
        createTable(session, "Codex 大厅", "CX11", 2, true, 10, 10);

        mockMvc.perform(get(basePath() + "/tables")
                .param("keyword", "CX")
                .param("limit", "10")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tables[0].tableCode").value("CX11"))
            .andExpect(jsonPath("$.tables[0].areaSortOrder").value(10))
            .andExpect(jsonPath("$.tables[0].tableSortOrder").value(10))
            .andExpect(jsonPath("$.tables[1].tableCode").value("CX12"))
            .andExpect(jsonPath("$.tables[2].tableCode").value("CX20"))
            .andExpect(jsonPath("$.tables[2].areaSortOrder").value(20))
            .andExpect(jsonPath("$.tables[2].tableSortOrder").value(1));
    }

    @Test
    void tenantAdminCreatesNewAreasAndTablesAtEndWhenSortOrderIsMissing() throws Exception {
        Cookie session = login("20000000");

        createTable(session, "Codex 大厅", "CX40", 4, true, 30, 10);

        int maxAreaSortBeforeNewArea = maxAreaSortOrder();
        UUID appendedTableId = createTableWithoutSortOrder(session, "Codex 大厅", "CX41", 4, true);
        UUID newAreaTableId = createTableWithoutSortOrder(session, "Codex 二楼", "CX50", 6, true);

        assertThat(countWhere("""
            select count(*)
            from dining_tables table_record
            join store_areas area on area.id = table_record.area_id
            where table_record.id = ?
              and table_record.tenant_id = ?
              and table_record.store_id = ?
              and table_record.sort_order = 11
              and area.sort_order = 30
              and area.display_name = 'Codex 大厅'
            """, appendedTableId, VALIDATION_TENANT_ID, VALIDATION_STORE_ID)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from dining_tables table_record
            join store_areas area on area.id = table_record.area_id
            where table_record.id = ?
              and table_record.tenant_id = ?
              and table_record.store_id = ?
              and table_record.sort_order = 0
              and area.sort_order = ?
              and area.display_name = 'Codex 二楼'
            """, newAreaTableId, VALIDATION_TENANT_ID, VALIDATION_STORE_ID, maxAreaSortBeforeNewArea + 1)).isEqualTo(1);
    }

    @Test
    void tenantAdminAllowsSortOnlyPatchWhenBusyTableKeepsBusinessFields() throws Exception {
        Cookie session = login("20000000");
        UUID tableId = createTable(session, "Codex 大厅", "CX60", 4, true, 10, 1);
        jdbc.update(
            "update dining_tables set status = 'occupied' where id = ? and tenant_id = ? and store_id = ?",
            tableId,
            VALIDATION_TENANT_ID,
            VALIDATION_STORE_ID
        );

        mockMvc.perform(patch(basePath() + "/tables/{tableId}", tableId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "areaName":"Codex 大厅",
                      "tableCode":"CX60",
                      "capacity":4,
                      "enabled":true,
                      "areaSortOrder":20,
                      "tableSortOrder":9
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.table.status").value("occupied"))
            .andExpect(jsonPath("$.table.areaSortOrder").value(20))
            .andExpect(jsonPath("$.table.tableSortOrder").value(9));

        mockMvc.perform(patch(basePath() + "/tables/{tableId}", tableId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "areaName":"Codex 大厅",
                      "tableCode":"CX60",
                      "capacity":6,
                      "enabled":true,
                      "areaSortOrder":20,
                      "tableSortOrder":9
                    }
                    """)
                .cookie(session))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TABLE_IN_USE"));
    }

    @Test
    void tenantAdminExportsAndImportsTablesByOverwritingExistingTableCode() throws Exception {
        Cookie session = login("20000000");
        createTable(session, "Codex 大厅", "CX01", 4, true, 10, 10);

        mockMvc.perform(get(basePath() + "/tables/export").cookie(session))
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(result -> assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("tenant-admin-tables.xlsx"));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "tables.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            tableImportWorkbook()
        );

        mockMvc.perform(multipart(basePath() + "/tables/import")
                .file(file)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.imported.totalRows").value(2))
            .andExpect(jsonPath("$.imported.updated").value(1))
            .andExpect(jsonPath("$.imported.created").value(1));

        mockMvc.perform(get(basePath() + "/tables")
                .param("keyword", "CX")
                .param("limit", "10")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tables[0].tableCode").value("CX01"))
            .andExpect(jsonPath("$.tables[0].areaName").value("Codex 包间"))
            .andExpect(jsonPath("$.tables[0].capacity").value(6))
            .andExpect(jsonPath("$.tables[0].enabled").value(false))
            .andExpect(jsonPath("$.tables[0].areaSortOrder").value(1))
            .andExpect(jsonPath("$.tables[0].tableSortOrder").value(1))
            .andExpect(jsonPath("$.tables[1].tableCode").value("CX99"))
            .andExpect(jsonPath("$.tables[1].areaName").value("Codex 包间"))
            .andExpect(jsonPath("$.tables[1].capacity").value(8))
            .andExpect(jsonPath("$.tables[1].enabled").value(true))
            .andExpect(jsonPath("$.tables[1].areaSortOrder").value(1))
            .andExpect(jsonPath("$.tables[1].tableSortOrder").value(2));
    }

    @Test
    void tenantAdminImportPreservesBusyTableStatusWhenReimportingEnabledTable() throws Exception {
        Cookie session = login("20000000");
        UUID tableId = createTable(session, "Codex 大厅", "CX30", 4, true, 10, 1);
        jdbc.update(
            "update dining_tables set status = 'occupied' where id = ? and tenant_id = ? and store_id = ?",
            tableId,
            VALIDATION_TENANT_ID,
            VALIDATION_STORE_ID
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "tables.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            busyTableImportWorkbook()
        );

        mockMvc.perform(multipart(basePath() + "/tables/import")
                .file(file)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.imported.totalRows").value(1))
            .andExpect(jsonPath("$.imported.updated").value(1));

        assertThat(countWhere("""
            select count(*)
            from dining_tables table_record
            join store_areas area on area.id = table_record.area_id
            where table_record.id = ?
              and table_record.tenant_id = ?
              and table_record.store_id = ?
              and table_record.status = 'occupied'
              and table_record.sort_order = 9
              and area.sort_order = 5
              and table_record.capacity_max = 4
              and area.display_name = 'Codex 大厅'
            """, tableId, VALIDATION_TENANT_ID, VALIDATION_STORE_ID)).isEqualTo(1);
    }

    @Test
    void tenantAdminApiRejectsStaffAndStoreScopeMismatch() throws Exception {
        Cookie staffSession = login("1000");
        mockMvc.perform(get(basePath() + "/staff").cookie(staffSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get(basePath() + "/staff/me").cookie(staffSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        Cookie adminSession = login("20000000");
        mockMvc.perform(get("/api/v1/stores/{storeId}/tenant-admin/staff", UUID.randomUUID()).cookie(adminSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        mockMvc.perform(get("/api/v1/stores/{storeId}/tenant-admin/staff/me", UUID.randomUUID()).cookie(adminSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));
    }

    @Test
    void tenantAdminApiRejectsDuplicateStaffAndTableCodes() throws Exception {
        Cookie session = login("20000000");
        createStaff(session, "codex-1001", "Codex 员工");

        mockMvc.perform(post(basePath() + "/staff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employeeNo":"codex-1001",
                      "name":"重复员工",
                      "phone":"13800001002",
                      "email":"duplicate@example.test",
                      "password":"abc123"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("STAFF_CODE_CONFLICT"));

        createTable(session);
        mockMvc.perform(post(basePath() + "/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "areaName":"Codex 大厅",
                      "tableCode":"CX01",
                      "capacity":4,
                      "enabled":true
                    }
                    """)
                .cookie(session))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TABLE_CODE_CONFLICT"));
    }

    @Test
    void tenantAdminMaintainsCustomersWithOptionalPhoneAndArchive() throws Exception {
        Cookie session = login("20000000");

        MvcResult created = mockMvc.perform(post(basePath() + "/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName":"Codex 王小明",
                      "nickname":"先生",
                      "email":"codex-customer-001@example.test"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.customer.displayName").value("Codex 王小明"))
            .andExpect(jsonPath("$.customer.nickname").value("先生"))
            .andExpect(jsonPath("$.customer.phoneE164").doesNotExist())
            .andExpect(jsonPath("$.customer.email").value("codex-customer-001@example.test"))
            .andReturn();
        UUID customerId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
            .path("customer")
            .path("id")
            .asText());

        mockMvc.perform(get(basePath() + "/customers")
                .param("keyword", "王小明")
                .param("limit", "10")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.customers[0].id").value(customerId.toString()))
            .andExpect(jsonPath("$.customers[0].nickname").value("先生"))
            .andExpect(jsonPath("$.page.total").value(1));

        mockMvc.perform(get(basePath() + "/customers/{customerId}", customerId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customer.id").value(customerId.toString()));

        mockMvc.perform(patch(basePath() + "/customers/{customerId}", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName":"Codex 王小明",
                      "nickname":"先生",
                      "phoneE164":"+6598880001",
                      "email":"codex-customer-001-updated@example.test"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customer.phoneE164").value("+6598880001"))
            .andExpect(jsonPath("$.customer.email").value("codex-customer-001-updated@example.test"));

        mockMvc.perform(post(basePath() + "/customers/{customerId}/archive", customerId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get(basePath() + "/customers")
                .param("keyword", "王小明")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customers").isEmpty())
            .andExpect(jsonPath("$.page.total").value(0));

        assertThat(countWhere("""
            select count(*)
            from customers
            where id = ?
              and tenant_id = ?
              and status = 'archived'
              and deleted_at is not null
            """, customerId, VALIDATION_TENANT_ID)).isEqualTo(1);
    }

    @Test
    void tenantAdminRejectsDuplicateActiveCustomerPhone() throws Exception {
        Cookie session = login("20000000");
        createCustomer(session, "Codex 重复顾客", "女士", "+6598880002", "codex-customer-002@example.test");

        mockMvc.perform(post(basePath() + "/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName":"Codex 另一位顾客",
                      "nickname":"先生",
                      "phoneE164":"+6598880002",
                      "email":"codex-customer-003@example.test"
                    }
                    """)
                .cookie(session))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CUSTOMER_PHONE_CONFLICT"));
    }

    private UUID createStaff(Cookie session, String employeeNo, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(basePath() + "/staff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employeeNo":"%s",
                      "name":"%s",
                      "phone":"13800001001",
                      "email":"codex-1001@example.test",
                      "password":"abc123"
                    }
                    """.formatted(employeeNo, name))
                .cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.staff.employeeNo").value(employeeNo))
            .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("staff").path("id").asText());
    }

    private UUID createCustomer(
        Cookie session,
        String displayName,
        String nickname,
        String phoneE164,
        String email
    ) throws Exception {
        MvcResult result = mockMvc.perform(post(basePath() + "/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName":"%s",
                      "nickname":"%s",
                      "phoneE164":"%s",
                      "email":"%s"
                    }
                    """.formatted(displayName, nickname, phoneE164, email))
                .cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.customer.displayName").value(displayName))
            .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("customer").path("id").asText());
    }

    private UUID createTable(Cookie session) throws Exception {
        return createTable(session, "Codex 大厅", "CX01", 4, true, 0, 0);
    }

    private UUID createTable(
        Cookie session,
        String areaName,
        String tableCode,
        int capacity,
        boolean enabled,
        int areaSortOrder,
        int tableSortOrder
    ) throws Exception {
        MvcResult result = mockMvc.perform(post(basePath() + "/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "areaName":"%s",
                      "tableCode":"%s",
                      "capacity":%d,
                      "enabled":%s,
                      "areaSortOrder":%d,
                      "tableSortOrder":%d
                    }
                    """.formatted(areaName, tableCode, capacity, enabled, areaSortOrder, tableSortOrder))
                .cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.table.tableCode").value(tableCode))
            .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("table").path("id").asText());
    }

    private UUID createTableWithoutSortOrder(
        Cookie session,
        String areaName,
        String tableCode,
        int capacity,
        boolean enabled
    ) throws Exception {
        MvcResult result = mockMvc.perform(post(basePath() + "/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "areaName":"%s",
                      "tableCode":"%s",
                      "capacity":%d,
                      "enabled":%s
                    }
                    """.formatted(areaName, tableCode, capacity, enabled))
                .cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.table.tableCode").value(tableCode))
            .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("table").path("id").asText());
    }

    private int maxAreaSortOrder() {
        Integer maxSortOrder = jdbc.queryForObject(
            """
            select coalesce(max(sort_order), 0)
            from store_areas
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """,
            Integer.class,
            VALIDATION_TENANT_ID,
            VALIDATION_STORE_ID
        );
        return maxSortOrder == null ? 0 : maxSortOrder;
    }

    private void upsertSecondaryStoreForProfileSync() {
        jdbc.update(
            """
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency,
                share_address, share_contact_phone
            )
            values (?, ?, 'profile-sync-store', '同步验证门店', 'active',
                    'Asia/Singapore', 'zh-CN', 'DD-MM-YYYY', 'HH:mm', 'SGD',
                    '旧地址', '旧电话')
            on conflict (id) do update
            set share_address = '旧地址',
                share_contact_phone = '旧电话',
                deleted_at = null,
                updated_at = now(),
                version = stores.version + 1
            """,
            SECONDARY_STORE_ID,
            VALIDATION_TENANT_ID
        );
    }

    private byte[] tableImportWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("tables");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("大类排序");
            header.createCell(1).setCellValue("桌号排序");
            header.createCell(2).setCellValue("分区组");
            header.createCell(3).setCellValue("桌号");
            header.createCell(4).setCellValue("人数");
            header.createCell(5).setCellValue("启用");

            Row existing = sheet.createRow(1);
            existing.createCell(0).setCellValue(1);
            existing.createCell(1).setCellValue(1);
            existing.createCell(2).setCellValue("Codex 包间");
            existing.createCell(3).setCellValue("CX01");
            existing.createCell(4).setCellValue(6);
            existing.createCell(5).setCellValue("停用");

            Row created = sheet.createRow(2);
            created.createCell(0).setCellValue(1);
            created.createCell(1).setCellValue(2);
            created.createCell(2).setCellValue("Codex 包间");
            created.createCell(3).setCellValue("CX99");
            created.createCell(4).setCellValue(8);
            created.createCell(5).setCellValue("启用");

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] busyTableImportWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("tables");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("大类排序");
            header.createCell(1).setCellValue("桌号排序");
            header.createCell(2).setCellValue("分区组");
            header.createCell(3).setCellValue("桌号");
            header.createCell(4).setCellValue("人数");
            header.createCell(5).setCellValue("启用");

            Row existing = sheet.createRow(1);
            existing.createCell(0).setCellValue(5);
            existing.createCell(1).setCellValue(9);
            existing.createCell(2).setCellValue("Codex 大厅");
            existing.createCell(3).setCellValue("CX30");
            existing.createCell(4).setCellValue(4);
            existing.createCell(5).setCellValue("启用");

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] pngHeader() {
        return new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x0d
        };
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

    private String basePath() {
        return "/api/v1/stores/" + VALIDATION_STORE_ID + "/tenant-admin";
    }

    private int countWhere(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private UUID accountId(String username) {
        return jdbc.queryForObject(
            "select id from auth_accounts where username = ? and deleted_at is null",
            UUID.class,
            username
        );
    }

    private record SliderTarget(String challengeId, int targetX) {
    }
}
