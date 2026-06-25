package com.rpb.reservation.auth.integration;

import static com.rpb.reservation.auth.integration.AuthPostgresTestDatabase.VALIDATION_STORE_ID;
import static com.rpb.reservation.auth.integration.AuthPostgresTestDatabase.VALIDATION_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {
    private static final AuthPostgresTestDatabase DATABASE = AuthPostgresTestDatabase.startWithValidationStore();
    private static final UUID AREA_ID = UUID.fromString("60000000-0000-0000-0000-000000000983");
    private static final UUID TABLE_ID = UUID.fromString("70000000-0000-0000-0000-000000000983");
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
        jdbc.update(
            "update auth_accounts set password_hash = ? where username in ('sysadmin', '20000000', '1000')",
            PASSWORD_393930_HASH
        );
        jdbc.update("delete from dining_tables where id = ?", TABLE_ID);
        jdbc.update("delete from store_areas where id = ?", AREA_ID);
        jdbc.update("""
            insert into store_areas (id, tenant_id, store_id, area_code, display_name, status, sort_order)
            values (?, ?, ?, 'AUTH', '认证验证区', 'active', 1)
            """, AREA_ID, VALIDATION_TENANT_ID, VALIDATION_STORE_ID);
        jdbc.update("""
            insert into dining_tables (
                id, tenant_id, store_id, area_id, table_code, display_name,
                capacity_min, capacity_max, status, is_combinable
            )
            values (?, ?, ?, ?, 'AUTH1', '认证验证桌', 1, 4, 'available', true)
            """, TABLE_ID, VALIDATION_TENANT_ID, VALIDATION_STORE_ID, AREA_ID);
    }

    @Test
    void staffAccountCompletesSliderLoginMeBusinessApiAndLogoutThroughSessionCookie() throws Exception {
        MvcResult login = login("1000", "393930")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.user.username").value("1000"))
            .andExpect(cookie().exists("RPB_SESSION"))
            .andReturn();

        Cookie sessionCookie = login.getResponse().getCookie("RPB_SESSION");
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        assertThat(sessionCookie.getPath()).isEqualTo("/");
        assertThat(countWhere("""
            select count(*)
            from auth_user_sessions session
            join auth_accounts account on account.id = session.account_id
            where account.username = '1000'
              and session.status = 'active'
              and session.expires_at > now()
            """)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.user.username").value("1000"))
            .andExpect(jsonPath("$.user.defaultStoreId").value(VALIDATION_STORE_ID.toString()));

        mockMvc.perform(get("/api/v1/stores/{storeId}/tables", VALIDATION_STORE_ID)
                .param("includeGroups", "true")
                .cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resources[0].code").value("AUTH1"));

        mockMvc.perform(post("/api/v1/auth/logout").cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(cookie().maxAge("RPB_SESSION", 0));

        assertThat(countWhere("""
            select count(*)
            from auth_user_sessions session
            join auth_accounts account on account.id = session.account_id
            where account.username = '1000'
              and session.status = 'active'
            """)).isEqualTo(0);

        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void threeSeededLocalValidationAccountsCanLoginWithSliderCaptcha() throws Exception {
        assertLoginRoles("sysadmin", List.of("platform_admin", "tenant_admin"));
        assertLoginRoles("20000000", List.of("tenant_admin"));
        assertLoginRoles("1000", List.of("store_staff"));
    }

    @Test
    void rejectsMissingOrMismatchedSliderBeforeCreatingSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"1000","password":"393930"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("CAPTCHA_REQUIRED"));

        SliderTarget target = createSliderTarget();
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"1000","password":"393930","captchaId":"%s","captchaX":%d}
                    """.formatted(target.challengeId(), target.targetX() + 25)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("CAPTCHA_MISMATCH"));

        assertThat(countWhere("select count(*) from auth_user_sessions")).isEqualTo(0);
    }

    @Test
    void passwordPolicyRequiresSixAlnumAndMatchesLettersCaseInsensitively() throws Exception {
        SliderTarget invalidTarget = createSliderTarget();
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"1000","password":"39393","captchaId":"%s","captchaX":%d}
                    """.formatted(invalidTarget.challengeId(), invalidTarget.targetX())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("PASSWORD_POLICY_VIOLATION"));

        jdbc.update(
            "update auth_accounts set password_hash = ? where username = '1000'",
            new BCryptPasswordEncoder().encode("abc123")
        );

        login("1000", "ABC123")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.username").value("1000"));
    }

    private void assertLoginRoles(String username, List<String> expectedRoles) throws Exception {
        MvcResult result = login(username, "393930")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.user.username").value(username))
            .andReturn();
        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString()).path("user");
        assertThat(strings(user.path("roles"))).containsAll(expectedRoles);
        assertThat(strings(user.path("storeIds"))).contains(VALIDATION_STORE_ID.toString());
    }

    private ResultActions login(String username, String password) throws Exception {
        SliderTarget target = createSliderTarget();
        return mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"%s","password":"%s","captchaId":"%s","captchaX":%d}
                """.formatted(username, password, target.challengeId(), target.targetX())));
    }

    private SliderTarget createSliderTarget() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/captcha/slider"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.challenge.challengeId").isNotEmpty())
            .andExpect(jsonPath("$.challenge.type").value("image_slider"))
            .andExpect(jsonPath("$.challenge.backgroundImage").isNotEmpty())
            .andExpect(jsonPath("$.challenge.pieceImage").isNotEmpty())
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

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private record SliderTarget(String challengeId, int targetX) {
    }
}
