package com.rpb.reservation.queue.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.queue.application.command.SkipQueueTicketCommand;
import com.rpb.reservation.queue.application.service.QueueSkipApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
class QueueSkipApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000985");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000985");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000985");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000985");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000985");
    private static final UUID QUEUE_GROUP_ID = UUID.fromString("92000000-0000-0000-0000-000000000985");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000985");
    private static final UUID OTHER_QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000986");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");
    private static final Instant START_AT = Instant.parse("2030-06-20T03:00:00Z");
    private static final Instant END_AT = Instant.parse("2030-06-20T04:30:00Z");
    private static final Instant HOLD_UNTIL_AT = Instant.parse("2030-06-20T03:15:00Z");
    private static final Instant CALLED_AT = Instant.parse("2030-06-20T03:30:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2030-06-20T03:33:00Z");
    private static final Instant SKIPPED_AT = Instant.parse("2030-06-20T03:45:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestCurrentActorProvider actorProvider;

    private Fixture fixture;

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
        fixture = new Fixture(jdbc);
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.skip"), Set.of(STORE_ID)));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void skipsCalledQueueTicketThroughApiAndWritesEvidenceToPostgres() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-SKIP-0985", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, null, 12, 1);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "skip-success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(SKIPPED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.queueTicketId").value(QUEUE_TICKET_ID.toString()))
            .andExpect(jsonPath("$.queueTicketNumber").value(12))
            .andExpect(jsonPath("$.queueTicketStatus").value("skipped"))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-SKIP-0985"))
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.skippedAt").value(SKIPPED_AT.toString()))
            .andExpect(jsonPath("$.alreadySkipped").value(false))
            .andExpect(jsonPath("$.events[0]").value("queue_ticket.skipped"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertSuccessfulSkipDatabaseState("skip-success", QUEUE_TICKET_ID);
        assertBoundaryNoDownstreamSlices();
    }

    @Test
    void completedReplayDoesNotDuplicateEvidence() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-SKIP-REPLAY", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, null, 12, 1);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "skip-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(SKIPPED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "skip-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(SKIPPED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueTicketStatus").value("skipped"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true))
            .andExpect(jsonPath("$.events").isEmpty());

        assertThat(fixture.countWhere("select count(*) from queue_tickets where status = 'skipped'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'queue_ticket.skipped'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from state_transition_logs where transition_code = 'queue_ticket.skip'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'queue.skip'")).isEqualTo(1);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
        assertBoundaryNoDownstreamSlices();
    }

    @Test
    void alreadySkippedWithCompleteEvidenceReturnsAlreadySkippedWithoutDuplicateEvidence() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-SKIP-ALREADY", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "skipped", CALLED_AT, EXPIRES_AT, SKIPPED_AT, 12, 1);
        fixture.skipEvidence(QUEUE_TICKET_ID);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "skip-already")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(SKIPPED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueTicketStatus").value("skipped"))
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.skippedAt").value(SKIPPED_AT.toString()))
            .andExpect(jsonPath("$.alreadySkipped").value(true))
            .andExpect(jsonPath("$.events").isEmpty())
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'queue_ticket.skipped'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from state_transition_logs where transition_code = 'queue_ticket.skip'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'queue.skip'")).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", "skip-already"))
            .isEqualTo("completed");
        assertBoundaryNoDownstreamSlices();
    }

    @Test
    void idempotencyStatesReturnStableErrorsWithoutMutation() throws Exception {
        assertIdempotencyFailure("skip-in-progress", "started", "IDEMPOTENCY_IN_PROGRESS", "started", hash(QUEUE_TICKET_ID, SKIPPED_AT));
        assertIdempotencyFailure("skip-failed", "failed", "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY", "failed", hash(QUEUE_TICKET_ID, SKIPPED_AT));
        assertIdempotencyFailure("skip-hash-conflict", "completed", "IDEMPOTENCY_CONFLICT", "conflict", "different-request-hash");
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequestWithoutMutation() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-SKIP-MISSING-IDEM", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, null, 12, 1);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(SKIPPED_AT)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"));

        assertCalledTicketUnchanged(QUEUE_TICKET_ID);
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
    }

    @Test
    void applicationFailuresReturnStableApiErrorsWithoutSkipping() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-SKIP-NOT-FOUND", "arrived", 4);
        mockMvc.perform(post(ENDPOINT, STORE_ID, OTHER_QUEUE_TICKET_ID)
                .header("Idempotency-Key", "skip-ticket-not-found")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(SKIPPED_AT)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("QUEUE_TICKET_NOT_FOUND"));
        assertFailedIdempotencyAndFailureAudit("skip-ticket-not-found");

        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(RESERVATION_ID, "R-SKIP-WAITING", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "waiting", null, null, null, 12, 1);
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "skip-waiting")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(SKIPPED_AT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("QUEUE_TICKET_STATUS_NOT_CALLED"));
        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", QUEUE_TICKET_ID)).isEqualTo("waiting");
        assertFailedIdempotencyAndFailureAudit("skip-waiting");

        fixture.reset();
        fixture.createBaseStore();
        fixture.queueTicket(QUEUE_TICKET_ID, null, "called", CALLED_AT, EXPIRES_AT, null, 12, 1);
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "skip-reservation-not-found")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(SKIPPED_AT)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("RESERVATION_NOT_FOUND"));
        assertFailedIdempotencyAndFailureAudit("skip-reservation-not-found");

        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(RESERVATION_ID, "R-SKIP-NOT-ARRIVED", "confirmed", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, null, 12, 1);
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "skip-reservation-not-arrived")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(SKIPPED_AT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("RESERVATION_STATUS_NOT_ARRIVED"));
        assertCalledTicketUnchanged(QUEUE_TICKET_ID);
        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("confirmed");
        assertFailedIdempotencyAndFailureAudit("skip-reservation-not-arrived");

        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(RESERVATION_ID, "R-SKIP-MISSING-EVIDENCE", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "skipped", CALLED_AT, EXPIRES_AT, null, 12, 1);
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "skip-missing-evidence")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(SKIPPED_AT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("QUEUE_SKIP_EVIDENCE_INCOMPLETE"));
        assertFailedIdempotencyAndFailureAudit("skip-missing-evidence");
    }

    @Test
    void appGateDenyCasesWriteAuditAndDoNotMutateBusinessState() throws Exception {
        assertAppGateDenied(
            "skip-gate-tenant",
            fixture -> jdbc.update("delete from tenant_app_entitlements where tenant_id = ? and app_key = 'reservation_queue'", TENANT_ID),
            "TENANT_APP_NOT_ENABLED"
        );
        assertAppGateDenied(
            "skip-gate-store",
            fixture -> jdbc.update("""
                update store_app_settings
                set is_enabled = false,
                    updated_at = now()
                where tenant_id = ? and store_id = ? and app_key = 'reservation_queue'
                """, TENANT_ID, STORE_ID),
            "STORE_APP_NOT_ENABLED"
        );
        assertAppGateDenied(
            "skip-gate-permission",
            fixture -> actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.call"), Set.of(STORE_ID))),
            "PERMISSION_DENIED"
        );
    }

    private void assertSuccessfulSkipDatabaseState(String idempotencyKey, UUID queueTicketId) {
        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", queueTicketId)).isEqualTo("skipped");
        assertThat(fixture.scalarOffsetDateTime("select skipped_at from queue_tickets where id = ?", queueTicketId))
            .isEqualTo(OffsetDateTime.ofInstant(SKIPPED_AT, ZoneOffset.UTC));
        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("arrived");
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'queue_ticket.skipped'")).isEqualTo(1);
        assertThat(fixture.countWhere("""
            select count(*) from state_transition_logs
            where target_type = 'queue_ticket'
              and target_id = ?
              and from_status = 'called'
              and to_status = 'skipped'
              and transition_code = 'queue_ticket.skip'
            """, queueTicketId)).isEqualTo(1);
        assertThat(fixture.countWhere("""
            select count(*) from audit_logs
            where operation_code = 'queue.skip'
              and target_type = 'queue_ticket'
              and target_id = ?
            """, queueTicketId)).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("completed");
        assertThat(fixture.scalarString("select action from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("skip_queue_ticket");
        assertThat(fixture.scalarString("select target_type from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("queue_ticket");
    }

    private void assertIdempotencyFailure(
        String key,
        String idempotencyStatus,
        String expectedCode,
        String expectedResponseStatus,
        String requestHash
    ) throws Exception {
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.skip"), Set.of(STORE_ID)));
        fixture.reservation(RESERVATION_ID, "R-SKIP-IDEM", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, null, 12, 1);
        fixture.idempotencyRecord(key, requestHash, idempotencyStatus);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(SKIPPED_AT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value(expectedCode))
            .andExpect(jsonPath("$.idempotency.status").value(expectedResponseStatus));

        assertCalledTicketUnchanged(QUEUE_TICKET_ID);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    private void assertCalledTicketUnchanged(UUID queueTicketId) {
        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", queueTicketId)).isEqualTo("called");
        assertThat(fixture.scalarOffsetDateTime("select skipped_at from queue_tickets where id = ?", queueTicketId)).isNull();
        assertBoundaryNoDownstreamSlices();
    }

    private void assertFailedIdempotencyAndFailureAudit(String idempotencyKey) {
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("failed");
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'queue.skip.failed'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'queue_ticket.skipped'")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from state_transition_logs where transition_code = 'queue_ticket.skip'")).isEqualTo(0);
        assertBoundaryNoDownstreamSlices();
    }

    private void assertAppGateDenied(String key, FixtureMutation mutation, String expectedReason) throws Exception {
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.skip"), Set.of(STORE_ID)));
        fixture.reservation(RESERVATION_ID, "R-SKIP-GATE-" + key, "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, null, 12, 1);
        mutation.apply(fixture);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(SKIPPED_AT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedReason));

        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", QUEUE_TICKET_ID)).isEqualTo("called");
        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("arrived");
        assertThat(fixture.scalarOffsetDateTime("select skipped_at from queue_tickets where id = ?", QUEUE_TICKET_ID)).isNull();
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.countWhere("""
            select count(*) from app_gate_audit_logs
            where tenant_id = ?
              and store_id = ?
              and app_key = 'reservation_queue'
              and action = 'APP_GATE_DENIED'
              and operator_user_id = ?
              and operator_role = 'staff'
              and after_json ->> 'denyReason' = ?
              and after_json ->> 'requiredPermission' = 'queue.skip'
            """, TENANT_ID, STORE_ID, ACTOR_ID, expectedReason)).isEqualTo(1);
        assertBoundaryNoDownstreamSlices();
    }

    private void assertBoundaryNoDownstreamSlices() {
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("seating_resources")).isEqualTo(0);
        assertThat(fixture.count("table_locks")).isEqualTo(0);
        assertThat(fixture.count("reservation_preassignments")).isEqualTo(0);
        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("turnovers")).isEqualTo(0);
        assertThat(fixture.count("dining_tables")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from reservations where status in ('no_show', 'cancelled')")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from queue_tickets where status = 'rejoined'")).isEqualTo(0);
    }

    private static String requestJson(Instant skippedAt) {
        return """
            {
              "skippedAt": "%s",
              "reasonCode": "NO_RESPONSE",
              "note": "Customer did not return"
            }
            """.formatted(skippedAt);
    }

    private static String hash(UUID queueTicketId, Instant skippedAt) {
        return QueueSkipApplicationService.requestHash(new SkipQueueTicketCommand(
            TENANT_ID,
            STORE_ID,
            queueTicketId,
            skippedAt,
            "NO_RESPONSE",
            "Customer did not return",
            "unused-for-hash",
            ACTOR_ID,
            "staff"
        ));
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions, Set<UUID> storeIds) {
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

    static final class TestCurrentActorProvider implements CurrentActorProvider {
        private final ThreadLocal<CurrentActor> actor = new ThreadLocal<>();

        void set(CurrentActor currentActor) {
            actor.set(currentActor);
        }

        void clear() {
            actor.remove();
        }

        @Override
        public Optional<CurrentActor> currentActor() {
            return Optional.ofNullable(actor.get());
        }
    }

    @FunctionalInterface
    private interface FixtureMutation {
        void apply(Fixture fixture);
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc;

        private Fixture(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        void reset() {
            jdbc.execute("truncate table tenants cascade");
        }

        void createBaseStore() {
            jdbc.update(
                """
                insert into tenants (id, tenant_code, display_name, status, default_locale)
                values (?, 'tenant-queue-skip-api-it', 'Queue Skip API Tenant', 'active', 'en-SG')
                """,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into stores (
                    id, tenant_id, store_code, display_name, status,
                    timezone, locale, date_format, time_format, currency
                )
                values (?, ?, 'store-queue-skip-api-it', 'Queue Skip API Store', 'active',
                    'Asia/Singapore', 'en-SG', 'yyyy-MM-dd', 'HH:mm', 'SGD')
                """,
                STORE_ID,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into customers (
                    id, tenant_id, customer_code, customer_type, display_name,
                    nickname, phone_e164, status
                )
                values (?, ?, 'C-QUEUE-SKIP-API', 'regular', 'Queue Skip Guest', null, null, 'active')
                """,
                CUSTOMER_ID,
                TENANT_ID
            );
            enableReservationQueueApp();
            queueGroup(QUEUE_GROUP_ID, "3-4", 3, 4, 1);
        }

        void reservation(UUID reservationId, String reservationCode, String status, int partySize) {
            jdbc.update(
                """
                insert into reservations (
                    id, tenant_id, store_id, customer_id, reservation_code, party_size,
                    business_date, reserved_start_at, reserved_end_at, hold_until_at,
                    status, source_channel, note, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'staff', 'Window seat',
                    ?, ?)
                """,
                reservationId,
                TENANT_ID,
                STORE_ID,
                CUSTOMER_ID,
                reservationCode,
                partySize,
                BUSINESS_DATE,
                utc(START_AT),
                utc(END_AT),
                utc(HOLD_UNTIL_AT),
                status,
                utc(START_AT.minusSeconds(7200)),
                utc(START_AT.minusSeconds(3600))
            );
        }

        void queueTicket(
            UUID queueTicketId,
            UUID reservationId,
            String status,
            Instant calledAt,
            Instant expiresAt,
            Instant skippedAt,
            int ticketNumber,
            int queuePosition
        ) {
            jdbc.update(
                """
                insert into queue_tickets (
                    id, tenant_id, store_id, queue_group_id, customer_id, reservation_id,
                    walk_in_id, ticket_number, party_size, business_date, status,
                    queue_position, called_at, expires_at, skipped_at, note, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, null, ?, 4, ?, ?,
                    ?, ?, ?, ?, 'Existing ticket', now(), now())
                """,
                queueTicketId,
                TENANT_ID,
                STORE_ID,
                QUEUE_GROUP_ID,
                CUSTOMER_ID,
                reservationId,
                ticketNumber,
                BUSINESS_DATE,
                status,
                queuePosition,
                calledAt == null ? null : utc(calledAt),
                expiresAt == null ? null : utc(expiresAt),
                skippedAt == null ? null : utc(skippedAt)
            );
        }

        void skipEvidence(UUID queueTicketId) {
            jdbc.update(
                """
                insert into business_events (
                    id, tenant_id, store_id, event_type, target_type, target_id,
                    actor_type, actor_id, source, metadata
                )
                values (gen_random_uuid(), ?, ?, 'queue_ticket.skipped', 'queue_ticket', ?,
                    'staff', ?, 'staff', '{}'::jsonb)
                """,
                TENANT_ID,
                STORE_ID,
                queueTicketId,
                ACTOR_ID
            );
            jdbc.update(
                """
                insert into state_transition_logs (
                    id, tenant_id, store_id, target_type, target_id, from_status, to_status,
                    transition_code, actor_type, actor_id, triggered_by, metadata
                )
                values (gen_random_uuid(), ?, ?, 'queue_ticket', ?, 'called', 'skipped',
                    'queue_ticket.skip', 'staff', ?, 'staff', '{}'::jsonb)
                """,
                TENANT_ID,
                STORE_ID,
                queueTicketId,
                ACTOR_ID
            );
            jdbc.update(
                """
                insert into audit_logs (
                    id, tenant_id, store_id, operation_code, target_type, target_id,
                    source, actor_type, actor_id, before_state, after_state
                )
                values (gen_random_uuid(), ?, ?, 'queue.skip', 'queue_ticket', ?,
                    'staff', 'staff', ?, '{}'::jsonb, '{}'::jsonb)
                """,
                TENANT_ID,
                STORE_ID,
                queueTicketId,
                ACTOR_ID
            );
        }

        void idempotencyRecord(String key, String hash, String status) {
            String snapshot = null;
            if ("failed".equals(status)) {
                snapshot = "{\"failure_reason\":\"audit_write_failed\"}";
            }
            jdbc.update(
                """
                insert into idempotency_records (
                    id, tenant_id, store_id, idempotency_key, source, action,
                    target_type, target_id, request_hash, response_snapshot, status, expires_at
                )
                values (gen_random_uuid(), ?, ?, ?, 'staff', 'skip_queue_ticket',
                    null, null, ?, ?::jsonb, ?, now() + interval '30 minutes')
                """,
                TENANT_ID,
                STORE_ID,
                key,
                hash,
                snapshot,
                status
            );
        }

        int count(String tableName) {
            return jdbc.queryForObject("select count(*) from " + tableName, Integer.class);
        }

        int countWhere(String sql, Object... args) {
            return jdbc.queryForObject(sql, Integer.class, args);
        }

        String scalarString(String sql, Object... args) {
            return jdbc.queryForObject(sql, String.class, args);
        }

        OffsetDateTime scalarOffsetDateTime(String sql, Object... args) {
            return jdbc.queryForObject(sql, OffsetDateTime.class, args);
        }

        private void queueGroup(UUID groupId, String groupCode, int minPartySize, Integer maxPartySize, int sortOrder) {
            jdbc.update(
                """
                insert into queue_groups (
                    id, tenant_id, store_id, group_code, min_party_size, max_party_size,
                    display_i18n_key, status, sort_order
                )
                values (?, ?, ?, ?, ?, ?, ?, 'active', ?)
                """,
                groupId,
                TENANT_ID,
                STORE_ID,
                groupCode,
                minPartySize,
                maxPartySize,
                "queue.group." + groupCode,
                sortOrder
            );
        }

        private void enableReservationQueueApp() {
            jdbc.update(
                """
                insert into tenant_app_entitlements (
                    tenant_id, app_key, status, valid_from, valid_until, config_json, enabled_at
                )
                values (?, 'reservation_queue', 'enabled', now(), null, '{}'::jsonb, now())
                on conflict (tenant_id, app_key) do nothing
                """,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into store_app_settings (
                    tenant_id, store_id, app_key, is_enabled, entry_visible, config_json, enabled_at
                )
                values (?, ?, 'reservation_queue', true, true, '{}'::jsonb, now())
                on conflict (tenant_id, store_id, app_key) do update
                set is_enabled = true,
                    entry_visible = true,
                    disabled_at = null,
                    updated_at = now()
                """,
                TENANT_ID,
                STORE_ID
            );
        }

        private static OffsetDateTime utc(Instant instant) {
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
    }
}
