package com.rpb.reservation.queue.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.queue.application.command.CallQueueTicketCommand;
import com.rpb.reservation.queue.application.service.QueueCallApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
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
class QueueCallApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/call";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000982");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000982");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000982");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000982");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000982");
    private static final UUID OTHER_RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000983");
    private static final UUID QUEUE_GROUP_ID = UUID.fromString("92000000-0000-0000-0000-000000000982");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000982");
    private static final UUID OTHER_QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000983");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");
    private static final Instant START_AT = Instant.parse("2030-06-20T03:00:00Z");
    private static final Instant END_AT = Instant.parse("2030-06-20T04:30:00Z");
    private static final Instant HOLD_UNTIL_AT = Instant.parse("2030-06-20T03:15:00Z");
    private static final Instant CALLED_AT = Instant.parse("2030-06-20T03:30:00Z");
    private static final Instant POLICY_HOLD_UNTIL_AT = Instant.parse("2030-06-20T03:34:00Z");
    private static final Instant DEFAULT_HOLD_UNTIL_AT = Instant.parse("2030-06-20T03:33:00Z");

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
        actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.call"), Set.of(STORE_ID)));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void callsWaitingQueueTicketThroughApiAndWritesEvidenceToPostgres() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CALL-0982", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "waiting", null, null, 12, 1);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "call-success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.queueTicketId").value(QUEUE_TICKET_ID.toString()))
            .andExpect(jsonPath("$.queueTicketNumber").value(12))
            .andExpect(jsonPath("$.queueTicketStatus").value("called"))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-CALL-0982"))
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.calledAt").value(CALLED_AT.toString()))
            .andExpect(jsonPath("$.holdUntilAt").value(POLICY_HOLD_UNTIL_AT.toString()))
            .andExpect(jsonPath("$.alreadyCalled").value(false))
            .andExpect(jsonPath("$.events[0]").value("queue_ticket.called"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertSuccessfulCallDatabaseState("call-success", QUEUE_TICKET_ID, POLICY_HOLD_UNTIL_AT);
        assertBoundaryTablesRemainUnchanged();
    }

    @Test
    void missingStorePolicyFallsBackToDefaultThreeMinuteHold() throws Exception {
        fixture.deleteStorePolicies();
        fixture.reservation(RESERVATION_ID, "R-CALL-DEFAULT", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "waiting", null, null, 12, 1);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "call-default-hold")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.holdUntilAt").value(DEFAULT_HOLD_UNTIL_AT.toString()));

        assertSuccessfulCallDatabaseState("call-default-hold", QUEUE_TICKET_ID, DEFAULT_HOLD_UNTIL_AT);
    }

    @Test
    void completedIdempotencyReplayDoesNotDuplicateEvidence() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CALL-REPLAY", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "waiting", null, null, 12, 1);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "call-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "call-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueTicketStatus").value("called"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true))
            .andExpect(jsonPath("$.events").isEmpty());

        assertThat(fixture.countWhere("select count(*) from queue_tickets where status = 'called'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'queue_ticket.called'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from state_transition_logs where transition_code = 'queue_ticket.call'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'queue.call'")).isEqualTo(1);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
        assertBoundaryTablesRemainUnchanged();
    }

    @Test
    void repeatCallWithNewKeyRefreshesCalledHoldAndWritesBusinessEvidence() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CALL-ALREADY", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, POLICY_HOLD_UNTIL_AT, 12, 1);
        Instant repeatCalledAt = CALLED_AT.plusSeconds(90);
        Instant repeatHoldUntilAt = repeatCalledAt.plus(Duration.between(CALLED_AT, POLICY_HOLD_UNTIL_AT));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "call-repeat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(repeatCalledAt)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueTicketStatus").value("called"))
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.calledAt").value(repeatCalledAt.toString()))
            .andExpect(jsonPath("$.holdUntilAt").value(repeatHoldUntilAt.toString()))
            .andExpect(jsonPath("$.alreadyCalled").value(false))
            .andExpect(jsonPath("$.events[0]").value("queue_ticket.called"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        assertThat(fixture.scalarOffsetDateTime("select called_at from queue_tickets where id = ?", QUEUE_TICKET_ID))
            .isEqualTo(OffsetDateTime.ofInstant(repeatCalledAt, ZoneOffset.UTC));
        assertThat(fixture.scalarOffsetDateTime("select expires_at from queue_tickets where id = ?", QUEUE_TICKET_ID))
            .isEqualTo(OffsetDateTime.ofInstant(repeatHoldUntilAt, ZoneOffset.UTC));
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'queue_ticket.called'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from state_transition_logs where transition_code = 'queue_ticket.call'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'queue.call'")).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", "call-repeat"))
            .isEqualTo("completed");
        assertBoundaryTablesRemainUnchanged();
    }

    @Test
    void idempotencyStatesReturnStableErrorsWithoutMutation() throws Exception {
        assertIdempotencyFailure("call-in-progress", "started", "IDEMPOTENCY_IN_PROGRESS", "started", hash(QUEUE_TICKET_ID, CALLED_AT));
        assertIdempotencyFailure("call-failed", "failed", "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY", "failed", hash(QUEUE_TICKET_ID, CALLED_AT));
        assertIdempotencyFailure("call-hash-conflict", "completed", "IDEMPOTENCY_CONFLICT", "conflict", "different-request-hash");
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequestWithoutMutation() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CALL-MISSING-IDEM", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "waiting", null, null, 12, 1);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"));

        assertWaitingTicketUnchanged(QUEUE_TICKET_ID);
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
    }

    @Test
    void applicationFailuresReturnStableApiErrorsWithoutCallingTicket() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CALL-NOT-FOUND", "arrived", 4);
        mockMvc.perform(post(ENDPOINT, STORE_ID, OTHER_QUEUE_TICKET_ID)
                .header("Idempotency-Key", "call-ticket-not-found")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("QUEUE_TICKET_NOT_FOUND"));
        assertFailedIdempotencyAndFailureAudit("call-ticket-not-found");

        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(RESERVATION_ID, "R-CALL-SKIPPED", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "skipped", null, null, 12, 1);
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "call-skipped")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("QUEUE_TICKET_STATUS_NOT_WAITING"));
        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", QUEUE_TICKET_ID)).isEqualTo("skipped");
        assertFailedIdempotencyAndFailureAudit("call-skipped");

        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(RESERVATION_ID, "R-CALL-NOT-ARRIVED", "confirmed", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "waiting", null, null, 12, 1);
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "call-reservation-not-arrived")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("RESERVATION_STATUS_NOT_ARRIVED"));
        assertTicketCallEvidenceUnchanged(QUEUE_TICKET_ID);
        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("confirmed");
        assertFailedIdempotencyAndFailureAudit("call-reservation-not-arrived");

        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(RESERVATION_ID, "R-CALL-MISSING-EVIDENCE", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, null, 12, 1);
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "call-missing-evidence")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("QUEUE_CALL_EVIDENCE_INCOMPLETE"));
        assertFailedIdempotencyAndFailureAudit("call-missing-evidence");
    }

    @Test
    void appGateDenyCasesAuditAndDoNotMutateBusinessState() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CALL-GATE", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "waiting", null, null, 12, 1);

        jdbc.update("delete from tenant_app_entitlements where tenant_id = ? and app_key = 'reservation_queue'", TENANT_ID);
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "call-gate-tenant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("TENANT_APP_NOT_ENABLED"));
        assertAppGateDeniedWithoutBusinessMutation("TENANT_APP_NOT_ENABLED");

        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(RESERVATION_ID, "R-CALL-GATE", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "waiting", null, null, 12, 1);
        jdbc.update("""
            update store_app_settings
            set is_enabled = false,
                updated_at = now()
            where tenant_id = ? and store_id = ? and app_key = 'reservation_queue'
            """, TENANT_ID, STORE_ID);
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "call-gate-store")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_APP_NOT_ENABLED"));
        assertAppGateDeniedWithoutBusinessMutation("STORE_APP_NOT_ENABLED");

        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(RESERVATION_ID, "R-CALL-GATE", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "waiting", null, null, 12, 1);
        actorProvider.set(actor(Set.of("store_staff"), Set.of("reservation.queue"), Set.of(STORE_ID)));
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "call-gate-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        assertAppGateDeniedWithoutBusinessMutation("PERMISSION_DENIED");
    }

    private void assertSuccessfulCallDatabaseState(String idempotencyKey, UUID queueTicketId, Instant holdUntilAt) {
        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", queueTicketId)).isEqualTo("called");
        assertThat(fixture.scalarOffsetDateTime("select called_at from queue_tickets where id = ?", queueTicketId))
            .isEqualTo(OffsetDateTime.ofInstant(CALLED_AT, ZoneOffset.UTC));
        assertThat(fixture.scalarOffsetDateTime("select expires_at from queue_tickets where id = ?", queueTicketId))
            .isEqualTo(OffsetDateTime.ofInstant(holdUntilAt, ZoneOffset.UTC));
        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("arrived");
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'queue_ticket.called'")).isEqualTo(1);
        assertThat(fixture.countWhere("""
            select count(*) from state_transition_logs
            where target_type = 'queue_ticket'
              and target_id = ?
              and from_status = 'waiting'
              and to_status = 'called'
              and transition_code = 'queue_ticket.call'
            """, queueTicketId)).isEqualTo(1);
        assertThat(fixture.countWhere("""
            select count(*) from audit_logs
            where operation_code = 'queue.call'
              and target_type = 'queue_ticket'
              and target_id = ?
            """, queueTicketId)).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("completed");
        assertThat(fixture.scalarString("select action from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("call_queue_ticket");
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
        actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.call"), Set.of(STORE_ID)));
        fixture.reservation(RESERVATION_ID, "R-CALL-IDEM", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "waiting", null, null, 12, 1);
        fixture.idempotencyRecord(key, requestHash, idempotencyStatus);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(CALLED_AT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value(expectedCode))
            .andExpect(jsonPath("$.idempotency.status").value(expectedResponseStatus));

        assertWaitingTicketUnchanged(QUEUE_TICKET_ID);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    private void assertWaitingTicketUnchanged(UUID queueTicketId) {
        assertTicketCallEvidenceUnchanged(queueTicketId);
        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("arrived");
        assertBoundaryTablesRemainUnchanged();
    }

    private void assertTicketCallEvidenceUnchanged(UUID queueTicketId) {
        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", queueTicketId)).isEqualTo("waiting");
        assertThat(fixture.scalarOffsetDateTime("select called_at from queue_tickets where id = ?", queueTicketId)).isNull();
        assertThat(fixture.scalarOffsetDateTime("select expires_at from queue_tickets where id = ?", queueTicketId)).isNull();
    }

    private void assertFailedIdempotencyAndFailureAudit(String idempotencyKey) {
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("failed");
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'queue.call.failed'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'queue_ticket.called'")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from state_transition_logs where transition_code = 'queue_ticket.call'")).isEqualTo(0);
        assertBoundaryTablesRemainUnchanged();
    }

    private void assertAppGateDeniedWithoutBusinessMutation(String expectedReason) {
        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("arrived");
        assertWaitingTicketUnchanged(QUEUE_TICKET_ID);
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
              and after_json ->> 'requiredPermission' = 'queue.call'
            """, TENANT_ID, STORE_ID, ACTOR_ID, expectedReason)).isEqualTo(1);
        assertBoundaryTablesRemainUnchanged();
    }

    private void assertBoundaryTablesRemainUnchanged() {
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("seating_resources")).isEqualTo(0);
        assertThat(fixture.count("table_locks")).isEqualTo(0);
        assertThat(fixture.count("reservation_preassignments")).isEqualTo(0);
        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("turnovers")).isEqualTo(0);
        assertThat(fixture.count("dining_tables")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from reservations where status in ('no_show', 'cancelled')")).isEqualTo(0);
    }

    private static String requestJson(Instant calledAt) {
        return """
            {
              "calledAt": "%s",
              "reasonCode": "TABLE_READY",
              "note": "Call customer near entrance"
            }
            """.formatted(calledAt);
    }

    private static String hash(UUID queueTicketId, Instant calledAt) {
        return QueueCallApplicationService.requestHash(new CallQueueTicketCommand(
            TENANT_ID,
            STORE_ID,
            queueTicketId,
            "unused-for-hash",
            ACTOR_ID,
            "staff",
            calledAt,
            "TABLE_READY",
            "Call customer near entrance"
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
                values (?, 'tenant-queue-call-api-it', 'Queue Call API Tenant', 'active', 'en-SG')
                """,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into stores (
                    id, tenant_id, store_code, display_name, status,
                    timezone, locale, date_format, time_format, currency
                )
                values (?, ?, 'store-queue-call-api-it', 'Queue Call API Store', 'active',
                    'Asia/Singapore', 'en-SG', 'DD-MM-YYYY', 'HH:mm', 'SGD')
                """,
                STORE_ID,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into store_policies (
                    id, tenant_id, store_id, reservation_hold_minutes, queue_call_hold_minutes,
                    expected_dining_minutes, queue_rejoin_policy_code, table_assignment_policy_code,
                    effective_from_at
                )
                values (gen_random_uuid(), ?, ?, 15, 4, 90, 'same_group_tail',
                    'default_capacity_time_area_group', now() - interval '1 day')
                """,
                TENANT_ID,
                STORE_ID
            );
            jdbc.update(
                """
                insert into customers (
                    id, tenant_id, customer_code, customer_type, display_name,
                    nickname, phone_e164, status
                )
                values (?, ?, 'C-QUEUE-CALL-API', 'regular', 'Queue Call Guest', null, null, 'active')
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
            int ticketNumber,
            int queuePosition
        ) {
            jdbc.update(
                """
                insert into queue_tickets (
                    id, tenant_id, store_id, queue_group_id, customer_id, reservation_id,
                    walk_in_id, ticket_number, party_size, business_date, status,
                    queue_position, called_at, expires_at, note, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, null, ?, 4, ?, ?,
                    ?, ?, ?, 'Existing ticket', now(), now())
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
                expiresAt == null ? null : utc(expiresAt)
            );
        }

        void deleteStorePolicies() {
            jdbc.update("delete from store_policies where tenant_id = ? and store_id = ?", TENANT_ID, STORE_ID);
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
                values (gen_random_uuid(), ?, ?, ?, 'staff', 'call_queue_ticket',
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

    private static final class LocalPostgresTestDatabase implements AutoCloseable {
        private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(45);

        private final Path dataDirectory;
        private final int port;

        private LocalPostgresTestDatabase(Path dataDirectory, int port) {
            this.dataDirectory = dataDirectory;
            this.port = port;
        }

        static LocalPostgresTestDatabase start() {
            try {
                Path targetDirectory = Path.of("target", "test-postgres", UUID.randomUUID().toString());
                deleteIfExists(targetDirectory);
                Files.createDirectories(targetDirectory);
                int port = freePort();
                LocalPostgresTestDatabase database = new LocalPostgresTestDatabase(targetDirectory, port);
                database.init();
                database.startServer();
                database.applyMigration();
                Runtime.getRuntime().addShutdownHook(new Thread(database::closeQuietly));
                return database;
            } catch (IOException exception) {
                throw new IllegalStateException("local_postgres_start_failed", exception);
            }
        }

        String jdbcUrl() {
            return "jdbc:postgresql://127.0.0.1:" + port + "/postgres?stringtype=unspecified";
        }

        String username() {
            return "postgres";
        }

        String password() {
            return "";
        }

        @Override
        public void close() {
            run(command("pg_ctl"), "-D", dataDirectory.toString(), "-m", "fast", "-w", "stop");
            deleteIfExists(dataDirectory);
        }

        private void closeQuietly() {
            try {
                close();
            } catch (RuntimeException ignored) {
                // Test shutdown should not hide the original test result.
            }
        }

        private void init() {
            run(command("initdb"), "-A", "trust", "-U", username(), "-D", dataDirectory.toString());
        }

        private void startServer() {
            Path logFile = dataDirectory.resolve("postgres.log");
            run(
                command("pg_ctl"),
                "-D", dataDirectory.toString(),
                "-l", logFile.toString(),
                "-o", "-p " + port + " -h 127.0.0.1",
                "-w",
                "start"
            );
        }

        private void applyMigration() {
            Path migrationDirectory = Path.of("src", "main", "resources", "db", "migration").toAbsolutePath();
            List<Path> migrations;
            try (Stream<Path> paths = Files.list(migrationDirectory)) {
                migrations = paths
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
            } catch (IOException exception) {
                throw new IllegalStateException("migration_list_failed: " + migrationDirectory, exception);
            }
            for (Path migration : migrations) {
                run(
                    command("psql"),
                    "-v", "ON_ERROR_STOP=1",
                    "-h", "127.0.0.1",
                    "-p", String.valueOf(port),
                    "-U", username(),
                    "-d", "postgres",
                    "-f", migration.toString()
                );
            }
        }

        private static void run(String... command) {
            Path outputFile = null;
            try {
                outputFile = Files.createTempFile("rpb-pg-command-", ".log");
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true);
                builder.redirectOutput(outputFile.toFile());
                Process process = builder.start();
                boolean exited = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                String output = new String(Files.readAllBytes(outputFile), Charset.defaultCharset());
                if (!exited) {
                    process.destroyForcibly();
                    throw new IllegalStateException("command_timeout: " + String.join(" ", command) + System.lineSeparator() + output);
                }
                if (process.exitValue() != 0) {
                    throw new IllegalStateException("command_failed: " + String.join(" ", command) + System.lineSeparator() + output);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("command_start_failed: " + String.join(" ", command), exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("command_interrupted: " + String.join(" ", command), exception);
            } finally {
                if (outputFile != null) {
                    try {
                        Files.deleteIfExists(outputFile);
                    } catch (IOException ignored) {
                        outputFile.toFile().deleteOnExit();
                    }
                }
            }
        }

        private static String command(String executable) {
            String suffix = System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "";
            String fileName = executable + suffix;
            String configuredBin = System.getenv("RPB_PG_BIN");
            if (configuredBin != null && !configuredBin.isBlank()) {
                Path configured = Path.of(configuredBin, fileName);
                if (Files.exists(configured)) {
                    return configured.toString();
                }
            }
            List<Path> candidates = new ArrayList<>();
            candidates.add(Path.of("C:", "Program Files", "PostgreSQL", "17", "bin", fileName));
            candidates.add(Path.of("C:", "Program Files", "PostgreSQL", "16", "bin", fileName));
            candidates.add(Path.of("C:", "Program Files", "PostgreSQL", "15", "bin", fileName));
            for (Path candidate : candidates) {
                if (Files.exists(candidate)) {
                    return candidate.toString();
                }
            }
            return fileName;
        }

        private static int freePort() throws IOException {
            try (ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(true);
                return socket.getLocalPort();
            }
        }

        private static void deleteIfExists(Path path) {
            if (!Files.exists(path)) {
                return;
            }
            try (Stream<Path> paths = Files.walk(path)) {
                paths.sorted(Comparator.reverseOrder()).forEach(LocalPostgresTestDatabase::deleteOne);
            } catch (IOException exception) {
                throw new IllegalStateException("delete_path_failed: " + path, exception);
            }
        }

        private static void deleteOne(Path path) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                throw new IllegalStateException("delete_path_failed: " + path, exception);
            }
        }
    }
}
