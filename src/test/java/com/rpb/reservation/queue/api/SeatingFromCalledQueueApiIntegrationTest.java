package com.rpb.reservation.queue.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.queue.application.command.SeatCalledQueueTicketCommand;
import com.rpb.reservation.queue.application.service.SeatingFromCalledQueueApplicationService;
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
class SeatingFromCalledQueueApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/seating/direct";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000992");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000992");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000992");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000992");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000992");
    private static final UUID OTHER_RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000993");
    private static final UUID AREA_ID = UUID.fromString("51000000-0000-0000-0000-000000000992");
    private static final UUID TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000992");
    private static final UUID SMALL_TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000993");
    private static final UUID GROUP_MEMBER_TABLE_1_ID = UUID.fromString("60000000-0000-0000-0000-000000000994");
    private static final UUID GROUP_MEMBER_TABLE_2_ID = UUID.fromString("60000000-0000-0000-0000-000000000995");
    private static final UUID TABLE_GROUP_ID = UUID.fromString("70000000-0000-0000-0000-000000000992");
    private static final UUID INVALID_TABLE_GROUP_ID = UUID.fromString("70000000-0000-0000-0000-000000000993");
    private static final UUID EXISTING_SEATING_ID = UUID.fromString("80000000-0000-0000-0000-000000000992");
    private static final UUID QUEUE_GROUP_ID = UUID.fromString("92000000-0000-0000-0000-000000000992");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000992");
    private static final UUID OTHER_QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000993");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");
    private static final Instant START_AT = Instant.parse("2030-06-20T03:00:00Z");
    private static final Instant END_AT = Instant.parse("2030-06-20T04:30:00Z");
    private static final Instant HOLD_UNTIL_AT = Instant.parse("2030-06-20T03:15:00Z");
    private static final Instant CALLED_AT = Instant.parse("2030-06-20T03:30:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2030-06-20T03:33:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestCurrentActorProvider actorProvider;

    private SeatCalledQueueFixture fixture;

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
        fixture = new SeatCalledQueueFixture(jdbc);
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.seat"), Set.of(STORE_ID)));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void seatsCalledQueueTicketToTableThroughApiAndWritesEvidence() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-QSEAT-0992", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, 12, 1);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "queue-seat-table-success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tableBody(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.queueTicketId").value(QUEUE_TICKET_ID.toString()))
            .andExpect(jsonPath("$.queueTicketNumber").value(12))
            .andExpect(jsonPath("$.queueTicketStatus").value("seated"))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-QSEAT-0992"))
            .andExpect(jsonPath("$.reservationStatus").value("seated"))
            .andExpect(jsonPath("$.seatingStatus").value("occupied"))
            .andExpect(jsonPath("$.resourceType").value("table"))
            .andExpect(jsonPath("$.resourceId").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.alreadySeated").value(false))
            .andExpect(jsonPath("$.events[0]").value("queue_ticket.seated"))
            .andExpect(jsonPath("$.events[1]").value("reservation.seated"))
            .andExpect(jsonPath("$.events[2]").value("seating.created"))
            .andExpect(jsonPath("$.events[3]").value("table.occupied"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertSuccessfulTableSeating("queue-seat-table-success", QUEUE_TICKET_ID, RESERVATION_ID, TABLE_ID);
    }

    @Test
    void seatsCalledQueueTicketToTableGroupThroughApiAndOccupiesMemberTables() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-QSEAT-GROUP-0992", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, 12, 1);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "queue-seat-group-success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(groupBody(TABLE_GROUP_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resourceType").value("table_group"))
            .andExpect(jsonPath("$.resourceId").value(TABLE_GROUP_ID.toString()))
            .andExpect(jsonPath("$.queueTicketStatus").value("seated"))
            .andExpect(jsonPath("$.reservationStatus").value("seated"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", QUEUE_TICKET_ID)).isEqualTo("seated");
        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("seated");
        assertThat(fixture.scalarInteger("""
            select count(*) from seating_resources
            where resource_type = 'table_group'
              and table_group_id = ?
              and table_id is null
              and status = 'active'
            """, TABLE_GROUP_ID)).isEqualTo(1);
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", GROUP_MEMBER_TABLE_1_ID)).isEqualTo("occupied");
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", GROUP_MEMBER_TABLE_2_ID)).isEqualTo("occupied");
        assertThat(fixture.scalarString("select status from table_groups where id = ?", TABLE_GROUP_ID)).isEqualTo("active");
        assertThat(fixture.countWhere("""
            select count(*) from state_transition_logs
            where target_type = 'dining_table'
              and to_status = 'occupied'
            """)).isEqualTo(2);
        assertBoundaryNoDownstreamSlices();
    }

    @Test
    void completedIdempotencyReplayDoesNotDuplicateEvidence() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-QSEAT-REPLAY-0992", "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, 12, 1);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "queue-seat-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tableBody(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "queue-seat-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tableBody(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueTicketStatus").value("seated"))
            .andExpect(jsonPath("$.reservationStatus").value("seated"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));

        assertThat(fixture.count("seatings")).isEqualTo(1);
        assertThat(fixture.count("seating_resources")).isEqualTo(1);
        assertThat(fixture.count("business_events")).isEqualTo(4);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(4);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'queue.seat'")).isEqualTo(1);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
        assertBoundaryNoDownstreamSlices();
    }

    @Test
    void alreadySeatedWithMatchingActiveSeatingReturnsAlreadySeatedWithoutDuplicateBusinessEvidence() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-QSEAT-ALREADY-0992", "seated", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "seated", CALLED_AT, EXPIRES_AT, 12, 1);
        fixture.existingQueueSeating(QUEUE_TICKET_ID, TABLE_ID);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "queue-seat-already")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tableBody(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.queueTicketStatus").value("seated"))
            .andExpect(jsonPath("$.reservationStatus").value("seated"))
            .andExpect(jsonPath("$.alreadySeated").value(true))
            .andExpect(jsonPath("$.events").isEmpty())
            .andExpect(jsonPath("$.seatingId").value(EXISTING_SEATING_ID.toString()))
            .andExpect(jsonPath("$.resourceType").value("table"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertThat(fixture.count("seatings")).isEqualTo(1);
        assertThat(fixture.count("seating_resources")).isEqualTo(1);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", "queue-seat-already"))
            .isEqualTo("completed");
        assertBoundaryNoDownstreamSlices();
    }

    @Test
    void idempotencyStatesReturnStableErrorsWithoutNewMutation() throws Exception {
        assertIdempotencyFailure("queue-seat-in-progress", "started", "IDEMPOTENCY_IN_PROGRESS", "started");
        assertIdempotencyFailure("queue-seat-failed", "failed", "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY", "failed");
        assertIdempotencyFailure("queue-seat-hash-conflict", "completed", "IDEMPOTENCY_CONFLICT", "conflict", "different-request-hash");
    }

    @Test
    void missingIdempotencyAndResourceSelectionValidationStopBeforeApplicationMutation() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tableBody(TABLE_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "queue-seat-both")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId":"%s","tableGroupId":"%s"}
                    """.formatted(TABLE_ID, TABLE_GROUP_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_SELECTION_CONFLICT"));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "queue-seat-neither")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_SELECTION_REQUIRED"));

        assertNoBusinessMutationBeforeApplication();
    }

    @Test
    void applicationFailuresReturnStableApiErrorsWithoutSeating() throws Exception {
        assertApplicationFailure("queue-seat-ticket-not-found", null, tableBody(TABLE_ID), 404, "QUEUE_TICKET_NOT_FOUND");
        assertApplicationFailure("queue-seat-not-called", fixture -> {
            fixture.reservation(RESERVATION_ID, "R-QSEAT-WAITING", "arrived", 4);
            fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "waiting", null, null, 12, 1);
        }, tableBody(TABLE_ID), 409, "QUEUE_TICKET_STATUS_NOT_CALLED");
        assertApplicationFailure("queue-seat-reservation-not-found", fixture -> {
            fixture.reservation(RESERVATION_ID, "R-QSEAT-SOFT-DELETED", "arrived", 4);
            fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, 12, 1);
            fixture.softDeleteReservation(RESERVATION_ID);
        }, tableBody(TABLE_ID), 404, "RESERVATION_NOT_FOUND");
        assertApplicationFailure("queue-seat-reservation-not-arrived", fixture -> {
            fixture.reservation(RESERVATION_ID, "R-QSEAT-NOT-ARRIVED", "confirmed", 4);
            fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, 12, 1);
        }, tableBody(TABLE_ID), 409, "RESERVATION_STATUS_NOT_ARRIVED");
        assertApplicationFailure("queue-seat-table-not-found", fixture -> {
            fixture.reservation(RESERVATION_ID, "R-QSEAT-TABLE-MISSING", "arrived", 4);
            fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, 12, 1);
        }, tableBody(UUID.randomUUID()), 404, "TABLE_NOT_FOUND");
        assertApplicationFailure("queue-seat-table-unavailable", fixture -> {
            fixture.reservation(RESERVATION_ID, "R-QSEAT-TABLE-BUSY", "arrived", 4);
            fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, 12, 1);
            fixture.updateTableStatus(TABLE_ID, "occupied");
        }, tableBody(TABLE_ID), 409, "TABLE_NOT_AVAILABLE");
        assertApplicationFailure("queue-seat-table-capacity", fixture -> {
            fixture.reservation(RESERVATION_ID, "R-QSEAT-TABLE-SMALL", "arrived", 4);
            fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, 12, 1);
        }, tableBody(SMALL_TABLE_ID), 409, "TABLE_CAPACITY_INSUFFICIENT");
        assertApplicationFailure("queue-seat-table-locked", fixture -> {
            fixture.reservation(RESERVATION_ID, "R-QSEAT-TABLE-LOCKED", "arrived", 4);
            fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, 12, 1);
            fixture.createActiveLock(TABLE_ID, "queue-seat-lock-conflict");
        }, tableBody(TABLE_ID), 409, "TABLE_LOCK_CONFLICT");
        assertApplicationFailure("queue-seat-group-invalid", fixture -> {
            fixture.reservation(RESERVATION_ID, "R-QSEAT-GROUP-BAD", "arrived", 4);
            fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, 12, 1);
        }, groupBody(INVALID_TABLE_GROUP_ID), 409, "TABLE_GROUP_INVALID");
    }

    @Test
    void appGateDenyCasesWriteAuditAndDoNotMutateBusinessState() throws Exception {
        assertAppGateDenied(
            "queue-seat-appgate-tenant",
            fixture -> jdbc.update("delete from tenant_app_entitlements where tenant_id = ? and app_key = 'reservation_queue'", TENANT_ID),
            "TENANT_APP_NOT_ENABLED"
        );
        assertAppGateDenied(
            "queue-seat-appgate-store",
            fixture -> jdbc.update("""
                update store_app_settings
                set is_enabled = false,
                    updated_at = now()
                where tenant_id = ? and store_id = ? and app_key = 'reservation_queue'
                """, TENANT_ID, STORE_ID),
            "STORE_APP_NOT_ENABLED"
        );
        assertAppGateDenied(
            "queue-seat-appgate-permission",
            fixture -> actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.call"), Set.of(STORE_ID))),
            "PERMISSION_DENIED"
        );
    }

    private void assertIdempotencyFailure(String key, String status, String expectedCode, String expectedIdempotencyStatus) throws Exception {
        assertIdempotencyFailure(key, status, expectedCode, expectedIdempotencyStatus, null);
    }

    private void assertIdempotencyFailure(
        String key,
        String status,
        String expectedCode,
        String expectedIdempotencyStatus,
        String requestHash
    ) throws Exception {
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.seat"), Set.of(STORE_ID)));
        fixture.reservation(RESERVATION_ID, "R-QSEAT-IDEM-" + key, "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, 12, 1);
        String hash = requestHash == null ? requestHash(QUEUE_TICKET_ID, TABLE_ID, null) : requestHash;
        fixture.idempotencyRecord(key, hash, status);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tableBody(TABLE_ID)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value(expectedCode))
            .andExpect(jsonPath("$.idempotency.status").value(expectedIdempotencyStatus));

        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("seating_resources")).isEqualTo(0);
        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", QUEUE_TICKET_ID)).isEqualTo("called");
        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("arrived");
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
        assertBoundaryNoDownstreamSlices();
    }

    private void assertApplicationFailure(
        String key,
        FixtureMutation mutation,
        String body,
        int expectedStatus,
        String expectedCode
    ) throws Exception {
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.seat"), Set.of(STORE_ID)));
        if (mutation != null) {
            mutation.apply(fixture);
        }

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedCode))
            .andExpect(jsonPath("$.idempotency.status").value("failed"));

        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("seating_resources")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'queue.seat.failed'")).isEqualTo(1);
        assertBoundaryNoDownstreamSlices();
    }

    private void assertAppGateDenied(String key, FixtureMutation mutation, String expectedReason) throws Exception {
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.seat"), Set.of(STORE_ID)));
        fixture.reservation(RESERVATION_ID, "R-QSEAT-GATE-" + key, "arrived", 4);
        fixture.queueTicket(QUEUE_TICKET_ID, RESERVATION_ID, "called", CALLED_AT, EXPIRES_AT, 12, 1);
        mutation.apply(fixture);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tableBody(TABLE_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedReason));

        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", QUEUE_TICKET_ID)).isEqualTo("called");
        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("arrived");
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", TABLE_ID)).isEqualTo("available");
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("seating_resources")).isEqualTo(0);
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
              and after_json ->> 'requiredPermission' = 'queue.seat'
            """, TENANT_ID, STORE_ID, ACTOR_ID, expectedReason)).isEqualTo(1);
        assertBoundaryNoDownstreamSlices();
    }

    private void assertSuccessfulTableSeating(String idempotencyKey, UUID queueTicketId, UUID reservationId, UUID tableId) {
        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", queueTicketId)).isEqualTo("seated");
        assertThat(fixture.scalarString("select status from reservations where id = ?", reservationId)).isEqualTo("seated");
        assertThat(fixture.count("seatings")).isEqualTo(1);
        assertThat(fixture.scalarInteger("""
            select count(*) from seatings
            where reservation_id is null
              and queue_ticket_id = ?
              and walk_in_id is null
              and status = 'occupied'
            """, queueTicketId)).isEqualTo(1);
        assertThat(fixture.scalarInteger("""
            select count(*) from seating_resources
            where resource_type = 'dining_table'
              and table_id = ?
              and table_group_id is null
              and status = 'active'
            """, tableId)).isEqualTo(1);
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", tableId)).isEqualTo("occupied");
        assertThat(fixture.countWhere("""
            select count(*) from business_events
            where event_type in ('queue_ticket.seated', 'reservation.seated', 'seating.created', 'table.occupied')
            """)).isEqualTo(4);
        assertThat(fixture.countWhere("""
            select count(*) from state_transition_logs
            where transition_code in ('queue_ticket.seat', 'reservation.seat', 'seating.occupy', 'dining_table.occupy')
            """)).isEqualTo(4);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'queue.seat'")).isEqualTo(1);
        assertThat(fixture.scalarString("select action from idempotency_records where idempotency_key = ?", idempotencyKey)).isEqualTo("seat_called_queue_ticket");
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey)).isEqualTo("completed");
        assertThat(fixture.scalarString("select target_type from idempotency_records where idempotency_key = ?", idempotencyKey)).isEqualTo("seating");
        assertBoundaryNoDownstreamSlices();
    }

    private void assertNoBusinessMutationBeforeApplication() {
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("seating_resources")).isEqualTo(0);
        assertBoundaryNoDownstreamSlices();
    }

    private void assertBoundaryNoDownstreamSlices() {
        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("turnovers")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from reservations where status in ('no_show', 'cancelled')")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from queue_tickets where status in ('skipped', 'rejoined')")).isEqualTo(0);
    }

    private static String tableBody(UUID tableId) {
        return """
            {
              "tableId": "%s",
              "tableGroupId": null,
              "overrideReasonCode": null,
              "overrideNote": null,
              "note": null
            }
            """.formatted(tableId);
    }

    private static String groupBody(UUID tableGroupId) {
        return """
            {
              "tableId": null,
              "tableGroupId": "%s",
              "overrideReasonCode": "MANUAL_ASSIGNMENT",
              "overrideNote": "Large party",
              "note": "Seat called queue ticket"
            }
            """.formatted(tableGroupId);
    }

    private static String requestHash(UUID queueTicketId, UUID tableId, UUID tableGroupId) {
        return SeatingFromCalledQueueApplicationService.requestHash(new SeatCalledQueueTicketCommand(
            TENANT_ID,
            STORE_ID,
            queueTicketId,
            tableId,
            tableGroupId,
            List.of(),
            "ignored-by-hash",
            ACTOR_ID,
            "staff",
            null,
            null,
            null
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

    @FunctionalInterface
    private interface FixtureMutation {
        void apply(SeatCalledQueueFixture fixture);
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

    private static final class SeatCalledQueueFixture {
        private final JdbcTemplate jdbc;

        private SeatCalledQueueFixture(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        void reset() {
            jdbc.execute("truncate table tenants cascade");
        }

        void createBaseStore() {
            jdbc.update(
                """
                insert into tenants (id, tenant_code, display_name, status, default_locale)
                values (?, 'tenant-queue-seat-api-it', 'Queue Seat API Tenant', 'active', 'en-SG')
                """,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into stores (
                    id, tenant_id, store_code, display_name, status,
                    timezone, locale, date_format, time_format, currency
                )
                values (?, ?, 'store-queue-seat-api-it', 'Queue Seat API Store', 'active',
                    'Asia/Singapore', 'en-SG', 'yyyy-MM-dd', 'HH:mm', 'SGD')
                """,
                STORE_ID,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into store_areas (id, tenant_id, store_id, area_code, display_name, status, sort_order)
                values (?, ?, ?, 'main', 'Main Dining Room', 'active', 1)
                """,
                AREA_ID,
                TENANT_ID,
                STORE_ID
            );
            jdbc.update(
                """
                insert into store_policies (
                    id, tenant_id, store_id, reservation_hold_minutes, queue_call_hold_minutes,
                    expected_dining_minutes, queue_rejoin_policy_code, table_assignment_policy_code,
                    effective_from_at
                )
                values (gen_random_uuid(), ?, ?, 15, 3, 90, 'same_group_tail',
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
                values (?, ?, 'C-QSEAT-API', 'regular', 'Queue Seat Guest', null, null, 'active')
                """,
                CUSTOMER_ID,
                TENANT_ID
            );
            enableReservationQueueApp();
            queueGroup();
            table(TABLE_ID, "A1", 1, 4, "available");
            table(SMALL_TABLE_ID, "S1", 1, 2, "available");
            table(GROUP_MEMBER_TABLE_1_ID, "G1", 1, 4, "available");
            table(GROUP_MEMBER_TABLE_2_ID, "G2", 1, 4, "available");
            tableGroup(TABLE_GROUP_ID, "G-OK", "active", 3, 8);
            tableGroup(INVALID_TABLE_GROUP_ID, "G-BAD", "inactive", 3, 8);
            groupMember(TABLE_GROUP_ID, GROUP_MEMBER_TABLE_1_ID, "left");
            groupMember(TABLE_GROUP_ID, GROUP_MEMBER_TABLE_2_ID, "right");
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
                    ?, ?, ?, 'Existing called ticket', now(), now())
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

        void existingQueueSeating(UUID queueTicketId, UUID tableId) {
            updateTableStatus(tableId, "occupied");
            jdbc.update(
                """
                insert into seatings (
                    id, tenant_id, store_id, reservation_id, queue_ticket_id, walk_in_id,
                    seating_code, party_size_snapshot, status, seated_at, note,
                    created_at, updated_at
                )
                values (?, ?, ?, null, ?, null, 'S-existing-queue-seat', 4, 'occupied',
                    now(), 'Existing queue seating', now(), now())
                """,
                EXISTING_SEATING_ID,
                TENANT_ID,
                STORE_ID,
                queueTicketId
            );
            jdbc.update(
                """
                insert into seating_resources (
                    id, tenant_id, store_id, seating_id, resource_type, table_id,
                    table_group_id, assigned_at, status, created_at, updated_at
                )
                values (gen_random_uuid(), ?, ?, ?, 'dining_table', ?, null,
                    now(), 'active', now(), now())
                """,
                TENANT_ID,
                STORE_ID,
                EXISTING_SEATING_ID,
                tableId
            );
        }

        void updateTableStatus(UUID tableId, String status) {
            jdbc.update(
                "update dining_tables set status = ?, updated_at = now() where id = ?",
                status,
                tableId
            );
        }

        void softDeleteReservation(UUID reservationId) {
            jdbc.update("update reservations set deleted_at = now(), updated_at = now() where id = ?", reservationId);
        }

        void createActiveLock(UUID tableId, String lockKey) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            jdbc.update(
                """
                insert into table_locks (
                    id, tenant_id, store_id, resource_type, resource_id, lock_key, lock_owner,
                    locked_until_at, source_type, source_id, idempotency_key, status, locked_at
                )
                values (gen_random_uuid(), ?, ?, 'dining_table', ?, ?, 'staff',
                    ?, 'manual', null, null, 'active', ?)
                """,
                TENANT_ID,
                STORE_ID,
                tableId,
                lockKey,
                now.plusMinutes(10),
                now
            );
        }

        void idempotencyRecord(String key, String hash, String status) {
            String snapshot = null;
            if ("completed".equals(status)) {
                snapshot = """
                    {"queueTicketId":"%s","queueTicketNumber":12,"queueTicketStatus":"seated","reservationId":"%s","reservationCode":"R-IDEM","reservationStatus":"seated","seatingId":"%s","resourceType":"dining_table","resourceId":"%s","partySizeSnapshot":4,"seatingStatus":"occupied","seatingResourceStatus":"active","tableStatus":"occupied","groupMemberStatuses":[],"alreadySeated":false}
                    """.formatted(QUEUE_TICKET_ID, RESERVATION_ID, EXISTING_SEATING_ID, TABLE_ID).trim();
            }
            if ("failed".equals(status)) {
                snapshot = "{\"failure_reason\":\"audit_write_failed\"}";
            }
            jdbc.update(
                """
                insert into idempotency_records (
                    id, tenant_id, store_id, idempotency_key, source, action,
                    target_type, target_id, request_hash, response_snapshot, status, expires_at
                )
                values (gen_random_uuid(), ?, ?, ?, 'staff', 'seat_called_queue_ticket',
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

        Integer scalarInteger(String sql, Object... args) {
            return jdbc.queryForObject(sql, Integer.class, args);
        }

        private void queueGroup() {
            jdbc.update(
                """
                insert into queue_groups (
                    id, tenant_id, store_id, group_code, min_party_size, max_party_size,
                    display_i18n_key, status, sort_order
                )
                values (?, ?, ?, '3-4', 3, 4, 'queue.group.3-4', 'active', 1)
                """,
                QUEUE_GROUP_ID,
                TENANT_ID,
                STORE_ID
            );
        }

        private void table(UUID tableId, String tableCode, int capacityMin, int capacityMax, String status) {
            jdbc.update(
                """
                insert into dining_tables (
                    id, tenant_id, store_id, area_id, table_code, display_name,
                    capacity_min, capacity_max, status, is_combinable
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                """,
                tableId,
                TENANT_ID,
                STORE_ID,
                AREA_ID,
                tableCode,
                tableCode,
                capacityMin,
                capacityMax,
                status
            );
        }

        private void tableGroup(UUID groupId, String groupCode, String status, int capacityMin, int capacityMax) {
            jdbc.update(
                """
                insert into table_groups (
                    id, tenant_id, store_id, group_code, group_type, status,
                    display_name, capacity_min, capacity_max
                )
                values (?, ?, ?, ?, 'fixed', ?, ?, ?, ?)
                """,
                groupId,
                TENANT_ID,
                STORE_ID,
                groupCode,
                status,
                groupCode,
                capacityMin,
                capacityMax
            );
        }

        private void groupMember(UUID groupId, UUID tableId, String role) {
            jdbc.update(
                """
                insert into table_group_members (id, tenant_id, store_id, table_group_id, table_id, member_role)
                values (gen_random_uuid(), ?, ?, ?, ?, ?)
                """,
                TENANT_ID,
                STORE_ID,
                groupId,
                tableId,
                role
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
