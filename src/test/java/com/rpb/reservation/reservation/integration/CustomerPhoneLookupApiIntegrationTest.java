package com.rpb.reservation.reservation.integration;

import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.ACTOR_ID;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.STORE_ID;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class CustomerPhoneLookupApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/customers/phone-lookup";
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000001501");
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestCurrentActorProvider actorProvider;

    private ReservationCreateIntegrationFixture fixture;

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
        fixture = new ReservationCreateIntegrationFixture(jdbc);
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of("customer.lookup")));
        insertLookupCustomer();
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void returnsExactActiveCustomerByPhoneWithinTenant() throws Exception {
        mockMvc.perform(get(ENDPOINT, STORE_ID).param("phoneE164", "+6591234567"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.found").value(true))
            .andExpect(jsonPath("$.customer.customerId").value(CUSTOMER_ID.toString()))
            .andExpect(jsonPath("$.customer.displayName").value("王先生"))
            .andExpect(jsonPath("$.customer.nickname").value("先生"))
            .andExpect(jsonPath("$.customer.phoneE164").value("+6591234567"));

        assertReadOnly();
    }

    @Test
    void returnsNotFoundWithoutLeakingCandidates() throws Exception {
        mockMvc.perform(get(ENDPOINT, STORE_ID).param("phoneE164", "+6598765432"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.found").value(false));

        assertReadOnly();
    }

    @Test
    void returnsCustomerNameAndSalutationAfterReservationAutoCreatesCustomer() throws Exception {
        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of("customer.lookup", "reservation.create")));
        String reservedStartAt = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES).toString();

        mockMvc.perform(post("/api/v1/stores/{storeId}/reservations", STORE_ID)
                .header("Idempotency-Key", "customer-lookup-auto-fill-seed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 2,
                      "reservedStartAt": "%s",
                      "customerName": "李女士",
                      "customerNickname": "女士",
                      "phoneE164": "+6597654321"
                    }
                    """.formatted(reservedStartAt)))
            .andExpect(status().isCreated());

        mockMvc.perform(get(ENDPOINT, STORE_ID).param("phoneE164", "+6597654321"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.found").value(true))
            .andExpect(jsonPath("$.customer.displayName").value("李女士"))
            .andExpect(jsonPath("$.customer.nickname").value("女士"))
            .andExpect(jsonPath("$.customer.phoneE164").value("+6597654321"));
    }

    @Test
    void reservationCreateUpdatesRecognizedCustomerNameForLaterPhoneLookup() throws Exception {
        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of("customer.lookup", "reservation.create")));
        String reservedStartAt = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES).toString();

        mockMvc.perform(post("/api/v1/stores/{storeId}/reservations", STORE_ID)
                .header("Idempotency-Key", "customer-lookup-profile-refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 2,
                      "reservedStartAt": "%s",
                      "customerId": "%s",
                      "customerName": "王女士",
                      "customerNickname": "女士",
                      "phoneE164": "+6591234567"
                    }
                    """.formatted(reservedStartAt, CUSTOMER_ID)))
            .andExpect(status().isCreated());

        mockMvc.perform(get(ENDPOINT, STORE_ID).param("phoneE164", "+6591234567"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.found").value(true))
            .andExpect(jsonPath("$.customer.customerId").value(CUSTOMER_ID.toString()))
            .andExpect(jsonPath("$.customer.displayName").value("王女士"))
            .andExpect(jsonPath("$.customer.nickname").value("女士"))
            .andExpect(jsonPath("$.customer.phoneE164").value("+6591234567"));
    }

    @Test
    void rejectsInvalidPhoneBeforeLookup() throws Exception {
        mockMvc.perform(get(ENDPOINT, STORE_ID).param("phoneE164", "91234567"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_PHONE_E164"))
            .andExpect(jsonPath("$.error.messageKey").value("customer.phone_lookup.invalid_phone_e164"));

        assertReadOnly();
    }

    @Test
    void requiresCustomerLookupPermission() throws Exception {
        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of("reservation.create")));

        mockMvc.perform(get(ENDPOINT, STORE_ID).param("phoneE164", "+6591234567"))
            .andExpect(status().isForbidden());

        assertThat(fixture.countWhere("""
            select count(*) from app_gate_audit_logs
            where tenant_id = ?
              and store_id = ?
              and app_key = 'reservation_queue'
              and action = 'APP_GATE_DENIED'
              and operator_user_id = ?
              and after_json ->> 'requiredPermission' = 'customer.lookup'
            """, TENANT_ID, STORE_ID, ACTOR_ID)).isEqualTo(1);
    }

    private void insertLookupCustomer() {
        jdbc.update(
            """
            insert into customers (
                id, tenant_id, customer_code, customer_type, display_name,
                nickname, phone_e164, status
            )
            values (?, ?, 'C-LOOKUP', 'regular', '王先生', '先生', '+6591234567', 'active')
            """,
            CUSTOMER_ID,
            TENANT_ID
        );
    }

    private void assertReadOnly() {
        assertThat(fixture.count("reservations")).isEqualTo(0);
        assertThat(fixture.count("queue_tickets")).isEqualTo(0);
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
    }

    private static CurrentActor actor(Set<UUID> storeIds, Set<String> roles, Set<String> permissions) {
        return CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            roles.contains("customer") ? "customer" : "staff",
            roles,
            permissions,
            storeIds
        );
    }

    @TestConfiguration
    static class TestSecurityConfiguration {
        @Bean
        @Primary
        TestCurrentActorProvider testCurrentActorProvider() {
            return new TestCurrentActorProvider();
        }
    }
}
