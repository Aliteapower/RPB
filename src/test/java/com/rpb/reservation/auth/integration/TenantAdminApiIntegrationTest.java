package com.rpb.reservation.auth.integration;

import static com.rpb.reservation.auth.integration.AuthPostgresTestDatabase.VALIDATION_STORE_ID;
import static com.rpb.reservation.auth.integration.AuthPostgresTestDatabase.VALIDATION_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
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
        jdbc.update("delete from auth_account_roles where account_id in (select id from auth_accounts where username like 'codex-%')");
        jdbc.update("delete from auth_account_permissions where account_id in (select id from auth_accounts where username like 'codex-%')");
        jdbc.update("delete from auth_account_store_access where account_id in (select id from auth_accounts where username like 'codex-%')");
        jdbc.update("delete from auth_accounts where username like 'codex-%'");
        jdbc.update("delete from dining_tables where table_code like 'CX%'");
        jdbc.update("delete from store_areas where area_code like 'CX%' or display_name like 'Codex%'");
        jdbc.update(
            "update auth_accounts set password_hash = ? where username in ('sysadmin', '20000000', '1000')",
            PASSWORD_393930_HASH
        );
    }

    @Test
    void tenantAdminMaintainsStaffTablesAndSettingsInsideOwnStore() throws Exception {
        Cookie session = login("20000000");

        mockMvc.perform(get(basePath() + "/staff")
                .param("keyword", "1000")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.staff[0].employeeNo").value("1000"));

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
              and store.currency = 'CNY'
              and policy.reservation_hold_minutes = 18
              and policy.queue_call_hold_minutes = 4
              and policy.expected_dining_minutes = 88
              and policy.effective_to_at is null
              and policy.deleted_at is null
            """, VALIDATION_STORE_ID, VALIDATION_TENANT_ID)).isEqualTo(1);
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
    void tenantAdminApiRejectsStaffAndStoreScopeMismatch() throws Exception {
        Cookie staffSession = login("1000");
        mockMvc.perform(get(basePath() + "/staff").cookie(staffSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        Cookie adminSession = login("20000000");
        mockMvc.perform(get("/api/v1/stores/{storeId}/tenant-admin/staff", UUID.randomUUID()).cookie(adminSession))
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

    private record SliderTarget(String challengeId, int targetX) {
    }
}
