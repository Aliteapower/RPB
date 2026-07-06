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
import com.rpb.reservation.customerauth.application.port.out.CustomerEmailDeliveryPort;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
    private static final UUID AUTH_SECONDARY_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000984");
    private static final String PASSWORD_393930_HASH = "$2a$10$ktA3gOgzus6v0bsJqw53.OerYPoQT6oet7NDdkmNhYYZaKH9ix9Vy";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private CustomerEmailDeliveryPort customerEmailDeliveryPort;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::jdbcUrl);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
        registry.add("rpb.customer-auth.expose-dev-code", () -> "true");
    }

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @BeforeEach
    void setUp() {
        jdbc.update("delete from auth_user_sessions");
        jdbc.update("delete from auth_slider_captcha_challenges");
        jdbc.update("delete from customer_auth_sessions where tenant_id = ?", VALIDATION_TENANT_ID);
        jdbc.update("delete from customer_auth_identities where tenant_id = ?", VALIDATION_TENANT_ID);
        jdbc.update("delete from customer_auth_accounts where tenant_id = ?", VALIDATION_TENANT_ID);
        jdbc.update("delete from customer_email_login_codes where tenant_id = ?", VALIDATION_TENANT_ID);
        jdbc.update("delete from reservation_preassignments where tenant_id = ? and store_id = ?", VALIDATION_TENANT_ID, VALIDATION_STORE_ID);
        jdbc.update("delete from reservations where tenant_id = ? and store_id = ?", VALIDATION_TENANT_ID, VALIDATION_STORE_ID);
        jdbc.update("delete from idempotency_records where tenant_id = ? and store_id = ?", VALIDATION_TENANT_ID, VALIDATION_STORE_ID);
        jdbc.update("delete from audit_logs where tenant_id = ? and store_id = ?", VALIDATION_TENANT_ID, VALIDATION_STORE_ID);
        jdbc.update("delete from business_events where tenant_id = ? and store_id = ?", VALIDATION_TENANT_ID, VALIDATION_STORE_ID);
        jdbc.update("delete from state_transition_logs where tenant_id = ? and store_id = ?", VALIDATION_TENANT_ID, VALIDATION_STORE_ID);
        jdbc.update("delete from customers where tenant_id = ? and customer_code like 'C-PUB-%'", VALIDATION_TENANT_ID);
        jdbc.update("delete from auth_account_store_access where tenant_id = ? and store_id = ?", VALIDATION_TENANT_ID, AUTH_SECONDARY_STORE_ID);
        jdbc.update("update tenants set tenant_code = '20000000' where id = ?", VALIDATION_TENANT_ID);
        jdbc.update(
            "update auth_accounts set password_hash = ? where username in ('sysadmin', '20000000', '1000')",
            PASSWORD_393930_HASH
        );
        jdbc.update(
            "update auth_accounts set default_store_id = ? where username in ('sysadmin', '20000000', '1000')",
            VALIDATION_STORE_ID
        );
        jdbc.update("delete from store_public_booking_settings where tenant_id = ? and store_id = ?", VALIDATION_TENANT_ID, VALIDATION_STORE_ID);
        jdbc.update("delete from store_customer_email_settings where tenant_id = ? and store_id = ?", VALIDATION_TENANT_ID, VALIDATION_STORE_ID);
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
    void currentUserStoresReturnsAuthorizedActiveStoresWithDefaultFlag() throws Exception {
        upsertAuthSecondaryStore();
        grantStoreAccess("1000", AUTH_SECONDARY_STORE_ID);

        MvcResult login = login("1000", "393930")
            .andExpect(status().isOk())
            .andReturn();
        Cookie sessionCookie = login.getResponse().getCookie("RPB_SESSION");

        MvcResult result = mockMvc.perform(get("/api/v1/me/stores").cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();

        JsonNode stores = objectMapper.readTree(result.getResponse().getContentAsString()).path("stores");
        assertThat(stores).hasSize(2);
        JsonNode defaultStore = storeById(stores, VALIDATION_STORE_ID);
        assertThat(defaultStore.path("storeCode").asText()).isNotBlank();
        assertThat(defaultStore.path("storeName").asText()).isNotBlank();
        assertThat(defaultStore.path("status").asText()).isEqualTo("active");
        assertThat(defaultStore.path("locale").asText()).isNotBlank();
        assertThat(defaultStore.path("defaultStore").asBoolean()).isTrue();

        JsonNode secondaryStore = storeById(stores, AUTH_SECONDARY_STORE_ID);
        assertThat(secondaryStore.path("storeCode").asText()).isEqualTo("auth-secondary");
        assertThat(secondaryStore.path("storeName").asText()).isEqualTo("认证二店");
        assertThat(secondaryStore.path("status").asText()).isEqualTo("active");
        assertThat(secondaryStore.path("locale").asText()).isEqualTo("en-SG");
        assertThat(secondaryStore.path("defaultStore").asBoolean()).isFalse();
    }

    @Test
    void platformAdminCurrentStoresDoesNotExposeTenantStoreSwitchTargets() throws Exception {
        MvcResult login = login("sysadmin", "393930")
            .andExpect(status().isOk())
            .andReturn();
        Cookie sessionCookie = login.getResponse().getCookie("RPB_SESSION");

        mockMvc.perform(get("/api/v1/me/stores").cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.stores").isEmpty());
    }

    @Test
    void threeSeededLocalValidationAccountsCanLoginWithSliderCaptcha() throws Exception {
        assertLoginRoles("sysadmin", List.of("platform_admin", "tenant_admin"));
        assertLoginRoles("20000000", List.of("tenant_admin"));
        assertLoginRoles("1000", List.of("store_staff"));
    }

    @Test
    void platformHostOnlyAllowsPlatformAdminLoginEntry() throws Exception {
        login("platform.booking.yumstone.sg", null, "platform_admin", null, "sysadmin", "393930")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.user.username").value("sysadmin"))
            .andExpect(jsonPath("$.user.roles[0]").value("platform_admin"));

        login("platform.booking.yumstone.sg", null, "staff", "20000000", "1000", "393930")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void tenantHostUsesHostPrefixAsAuthoritativeTenantContext() throws Exception {
        login("20000000.booking.yumstone.sg", null, "tenant_admin", null, "20000000", "393930")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.user.username").value("20000000"))
            .andExpect(jsonPath("$.user.roles[0]").value("tenant_admin"));

        login("20000000.booking.yumstone.sg", null, "staff", "99999999", "1000", "393930")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void alphanumericTenantHostUsesHostPrefixAsAuthoritativeTenantContext() throws Exception {
        jdbc.update("update tenants set tenant_code = 'lsc106' where id = ?", VALIDATION_TENANT_ID);

        login("lsc106.booking.yumstone.sg", null, "staff", null, "1000", "393930")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.user.username").value("1000"))
            .andExpect(jsonPath("$.user.roles[0]").value("store_staff"));

        login("lsc106.booking.yumstone.sg", null, "staff", "20000000", "1000", "393930")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        login("lsc106.booking.yumstone.sg", null, "platform_admin", null, "sysadmin", "393930")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void forwardedHostIsUsedForLoginContextBehindReverseProxy() throws Exception {
        login("127.0.0.1:8080", "platform.booking.yumstone.sg", "staff", "20000000", "1000", "393930")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void tenantHostResolvesPublicBookingEntryWithoutStoreIdPath() throws Exception {
        jdbc.update("""
            insert into store_public_booking_settings (
                tenant_id, store_id, enabled, require_customer_login, default_quota_percent
            )
            values (?, ?, true, true, 100)
            """, VALIDATION_TENANT_ID, VALIDATION_STORE_ID);

        mockMvc.perform(get("/api/v1/public/booking-entry")
                .header("Host", "20000000.booking.yumstone.sg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenantCode").value("20000000"))
            .andExpect(jsonPath("$.storeId").value(VALIDATION_STORE_ID.toString()));

        mockMvc.perform(get("/api/v1/public/booking-entry")
                .header("Host", "booking.yumstone.sg"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("tenant_context_required"));
    }

    @Test
    void publicCustomerEmailLoginCreatesCustomerBeforeAccountInsideSameTransaction() throws Exception {
        enableCustomerEmailLogin();
        MvcResult loginResult = loginPublicCustomer("Guest@Example.COM", "Guest From Email");

        UUID customerId = UUID.fromString(objectMapper
            .readTree(loginResult.getResponse().getContentAsString())
            .path("principal")
            .path("customerId")
            .asText());

        assertThat(countWhere("""
            select count(*)
            from customers
            where tenant_id = ?
              and id = ?
              and phone_e164 is null
              and display_name = 'Guest From Email'
            """, VALIDATION_TENANT_ID, customerId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from customer_auth_accounts
            where tenant_id = ?
              and customer_id = ?
              and lower(email) = 'guest@example.com'
              and status = 'active'
            """, VALIDATION_TENANT_ID, customerId)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from customer_auth_sessions
            where tenant_id = ?
              and customer_id = ?
              and status = 'active'
              and expires_at > now()
            """, VALIDATION_TENANT_ID, customerId)).isEqualTo(1);
    }

    @Test
    void publicCustomerCanSubmitBookingPhoneAndPersistSameCustomerProfile() throws Exception {
        enableCustomerEmailLogin();
        enablePublicBooking();

        MvcResult loginResult = loginPublicCustomer("phone-booking@example.com", "Phone Booking Guest");
        Cookie customerSession = loginResult.getResponse().getCookie("RPB_CUSTOMER_SESSION");
        assertThat(customerSession).isNotNull();

        UUID customerId = UUID.fromString(objectMapper
            .readTree(loginResult.getResponse().getContentAsString())
            .path("principal")
            .path("customerId")
            .asText());
        BookingSlot bookingSlot = nextSingaporeLunchSlot();
        String phoneE164 = "+65927268647";
        String idempotencyKey = "public-phone-booking-test";

        mockMvc.perform(post("/api/v1/public/stores/{storeId}/booking/reservations", VALIDATION_STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .cookie(customerSession)
                .content("""
                    {
                      "partySize": 2,
                      "reservedStartAt": "%s",
                      "businessDate": "%s",
                      "phoneE164": "%s",
                      "note": "hello"
                    }
                    """.formatted(bookingSlot.startAt(), bookingSlot.businessDate(), phoneE164)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("confirmed"))
            .andExpect(jsonPath("$.partySize").value(2));

        assertThat(countWhere("""
            select count(*)
            from customers
            where tenant_id = ?
              and id = ?
              and phone_e164 = ?
              and display_name = 'Phone Booking Guest'
            """, VALIDATION_TENANT_ID, customerId, phoneE164)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from reservations reservation
            join customers customer on customer.id = reservation.customer_id
                and customer.tenant_id = reservation.tenant_id
            where reservation.tenant_id = ?
              and reservation.store_id = ?
              and reservation.customer_id = ?
              and reservation.source_channel = 'public_booking'
              and reservation.note = 'hello'
              and customer.phone_e164 = ?
            """, VALIDATION_TENANT_ID, VALIDATION_STORE_ID, customerId, phoneE164)).isEqualTo(1);
        assertThat(countWhere("""
            select count(*)
            from idempotency_records
            where tenant_id = ?
              and store_id = ?
              and idempotency_key = ?
              and source = 'public_booking'
              and status = 'completed'
            """, VALIDATION_TENANT_ID, VALIDATION_STORE_ID, idempotencyKey)).isEqualTo(1);
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
        return login(null, null, null, null, username, password);
    }

    private void upsertAuthSecondaryStore() {
        jdbc.update(
            """
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency
            )
            values (?, ?, 'auth-secondary', '认证二店', 'active',
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
            AUTH_SECONDARY_STORE_ID,
            VALIDATION_TENANT_ID
        );
    }

    private void grantStoreAccess(String username, UUID storeId) {
        jdbc.update(
            """
            insert into auth_account_store_access (account_id, tenant_id, store_id)
            select id, tenant_id, ?
            from auth_accounts
            where username = ?
              and tenant_id = ?
              and deleted_at is null
            on conflict (account_id, tenant_id, store_id)
            where deleted_at is null
            do nothing
            """,
            storeId,
            username,
            VALIDATION_TENANT_ID
        );
    }

    private JsonNode storeById(JsonNode stores, UUID storeId) {
        for (JsonNode store : stores) {
            if (storeId.toString().equals(store.path("storeId").asText())) {
                return store;
            }
        }
        throw new AssertionError("Missing store " + storeId);
    }

    private ResultActions login(
        String host,
        String forwardedHost,
        String loginEntry,
        String tenantCode,
        String username,
        String password
    ) throws Exception {
        SliderTarget target = createSliderTarget();
        String loginEntryJson = loginEntry == null ? "" : """
                ,"loginEntry":"%s"
                """.formatted(loginEntry);
        String tenantCodeJson = tenantCode == null ? "" : """
                ,"tenantCode":"%s"
                """.formatted(tenantCode);
        var request = post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"%s","password":"%s","captchaId":"%s","captchaX":%d%s%s}
                """.formatted(username, password, target.challengeId(), target.targetX(), loginEntryJson, tenantCodeJson));
        if (host != null) {
            request.header("Host", host);
        }
        if (forwardedHost != null) {
            request.header("X-Forwarded-Host", forwardedHost);
        }
        return mockMvc.perform(request);
    }

    private void enableCustomerEmailLogin() {
        jdbc.update("""
            insert into store_customer_email_settings (
                tenant_id, store_id, enabled, provider, from_email, from_name,
                smtp_host, smtp_port, smtp_username, smtp_password_secret, smtp_start_tls
            )
            values (?, ?, true, 'smtp', 'booking@example.com', 'Booking',
                    'smtp.example.com', 587, 'smtp-user', 'smtp-secret', true)
            """, VALIDATION_TENANT_ID, VALIDATION_STORE_ID);
    }

    private void enablePublicBooking() {
        jdbc.update("""
            insert into store_public_booking_settings (
                tenant_id, store_id, enabled, require_customer_login,
                default_quota_mode, default_quota_percent, min_lead_minutes, max_advance_days
            )
            values (?, ?, true, true, 'percentage', 100, 0, 30)
            """, VALIDATION_TENANT_ID, VALIDATION_STORE_ID);
    }

    private MvcResult loginPublicCustomer(String email, String displayName) throws Exception {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        MvcResult codeResult = mockMvc.perform(post("/api/v1/public/customer-auth/email-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"storeId":"%s","email":"%s"}
                    """.formatted(VALIDATION_STORE_ID, email)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.email").value(normalizedEmail))
            .andExpect(jsonPath("$.devCode").isNotEmpty())
            .andReturn();

        String devCode = objectMapper
            .readTree(codeResult.getResponse().getContentAsString())
            .path("devCode")
            .asText();

        return mockMvc.perform(post("/api/v1/public/customer-auth/email-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storeId":"%s",
                      "email":"%s",
                      "code":"%s",
                      "displayName":"%s"
                    }
                    """.formatted(VALIDATION_STORE_ID, normalizedEmail, devCode, displayName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.principal.email").value(normalizedEmail))
            .andExpect(jsonPath("$.principal.displayName").value(displayName))
            .andExpect(cookie().exists("RPB_CUSTOMER_SESSION"))
            .andReturn();
    }

    private static BookingSlot nextSingaporeLunchSlot() {
        ZoneId zoneId = ZoneId.of("Asia/Singapore");
        Instant now = Instant.now();
        LocalDate businessDate = LocalDate.now(zoneId);
        LocalDateTime candidate = LocalDateTime.of(businessDate, LocalTime.of(11, 0));
        if (!candidate.atZone(zoneId).toInstant().isAfter(now.plusSeconds(60))) {
            candidate = candidate.plusDays(1);
        }
        return new BookingSlot(candidate.toLocalDate(), candidate.atZone(zoneId).toInstant());
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

    private record BookingSlot(LocalDate businessDate, Instant startAt) {
    }

    private record SliderTarget(String challengeId, int targetX) {
    }
}
