package com.rpb.reservation.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.reservation.application.command.CheckInReservationCommand;
import com.rpb.reservation.reservation.application.service.ReservationCheckInApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
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
class ReservationCheckInApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/check-in";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000801");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000801");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000801");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000801");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000801");
    private static final UUID ARRIVED_RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000802");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");
    private static final Instant START_AT = Instant.parse("2030-06-20T03:00:00Z");
    private static final Instant END_AT = Instant.parse("2030-06-20T04:30:00Z");
    private static final Instant HOLD_UNTIL_AT = Instant.parse("2030-06-20T03:15:00Z");
    private static final Instant ARRIVED_AT = Instant.parse("2030-06-20T03:10:00Z");
    private static final Instant PREVIOUS_ARRIVED_AT = Instant.parse("2030-06-20T03:03:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestCurrentActorProvider actorProvider;

    private CheckInFixture fixture;

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
        fixture = new CheckInFixture(jdbc);
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(actor(Set.of("store_staff"), Set.of("reservation.check_in"), Set.of(STORE_ID)));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void checksInConfirmedReservationThroughApiAndWritesEvidenceToPostgres() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CHECKIN-0801", "confirmed");

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "checkin-success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(ARRIVED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-CHECKIN-0801"))
            .andExpect(jsonPath("$.status").value("arrived"))
            .andExpect(jsonPath("$.arrivedAt").value(ARRIVED_AT.toString()))
            .andExpect(jsonPath("$.alreadyArrived").value(false))
            .andExpect(jsonPath("$.events[0]").value("reservation.arrived"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertSuccessfulCheckInDatabaseState("checkin-success", RESERVATION_ID, ARRIVED_AT);
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void completedIdempotencyReplayDoesNotDuplicateEvidence() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CHECKIN-0801", "confirmed");

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "checkin-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(ARRIVED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "checkin-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(ARRIVED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));

        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'reservation.arrived'"))
            .isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from state_transition_logs where transition_code = 'reservation.check_in'"))
            .isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'reservation.check_in'"))
            .isEqualTo(1);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void alreadyArrivedWithNewKeyReturnsAlreadyArrivedWithoutDuplicateEvidence() throws Exception {
        fixture.reservation(ARRIVED_RESERVATION_ID, "R-CHECKIN-0802", "arrived");
        fixture.arrivalTransition(ARRIVED_RESERVATION_ID, PREVIOUS_ARRIVED_AT);

        mockMvc.perform(post(ENDPOINT, STORE_ID, ARRIVED_RESERVATION_ID)
                .header("Idempotency-Key", "checkin-already-arrived")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(ARRIVED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reservationId").value(ARRIVED_RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.status").value("arrived"))
            .andExpect(jsonPath("$.alreadyArrived").value(true))
            .andExpect(jsonPath("$.arrivedAt").value(PREVIOUS_ARRIVED_AT.toString()))
            .andExpect(jsonPath("$.events").isEmpty())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertThat(fixture.scalarString("select status from reservations where id = ?", ARRIVED_RESERVATION_ID))
            .isEqualTo("arrived");
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(1);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", "checkin-already-arrived"))
            .isEqualTo("completed");
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void cancelledReservationFailsAndLeavesBusinessStateUnchanged() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CHECKIN-0801", "cancelled");

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "checkin-cancelled")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(ARRIVED_AT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("RESERVATION_CANNOT_CHECK_IN_CANCELLED"))
            .andExpect(jsonPath("$.idempotency.status").value("failed"));

        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID))
            .isEqualTo("cancelled");
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", "checkin-cancelled"))
            .isEqualTo("failed");
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'reservation.check_in.failed'"))
            .isEqualTo(1);
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void idempotencyInProgressReturnsConflictWithoutMutation() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CHECKIN-0801", "confirmed");
        CheckInReservationRequest request = new CheckInReservationRequest(
            ARRIVED_AT,
            "customer_arrived",
            "Guest is waiting at host stand"
        );
        fixture.idempotencyRecord(
            "checkin-in-progress",
            hash(RESERVATION_ID, request),
            "started",
            null,
            null
        );

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "checkin-in-progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(ARRIVED_AT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_IN_PROGRESS"))
            .andExpect(jsonPath("$.idempotency.status").value("started"));

        assertConfirmedReservationUnchanged(RESERVATION_ID);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void appGateRuntimeAllowsEnabledTenantStoreAndPermittedActor() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CHECKIN-0801", "confirmed");

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "appgate-checkin-allowed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(ARRIVED_AT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        assertThat(fixture.countWhere("select count(*) from reservations where status = 'arrived'")).isEqualTo(1);
        assertThat(fixture.count("app_gate_audit_logs")).isEqualTo(0);
    }

    @Test
    void appGateRuntimeRejectsTenantWithoutReservationQueueEntitlementAndAuditsDenial() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CHECKIN-0801", "confirmed");
        jdbc.update("delete from tenant_app_entitlements where tenant_id = ? and app_key = 'reservation_queue'", TENANT_ID);

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "appgate-checkin-tenant-disabled")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(ARRIVED_AT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("TENANT_APP_NOT_ENABLED"));

        assertAppGateDeniedWithoutBusinessMutation("TENANT_APP_NOT_ENABLED", "reservation.check_in");
    }

    @Test
    void appGateRuntimeRejectsStoreWithoutEnabledReservationQueueAndAuditsDenial() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CHECKIN-0801", "confirmed");
        jdbc.update("""
            update store_app_settings
            set is_enabled = false,
                updated_at = now()
            where tenant_id = ? and store_id = ? and app_key = 'reservation_queue'
            """, TENANT_ID, STORE_ID);

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "appgate-checkin-store-disabled")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(ARRIVED_AT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("STORE_APP_NOT_ENABLED"));

        assertAppGateDeniedWithoutBusinessMutation("STORE_APP_NOT_ENABLED", "reservation.check_in");
    }

    @Test
    void appGateRuntimeRejectsActorWithoutCheckInPermissionAndAuditsDenial() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CHECKIN-0801", "confirmed");
        actorProvider.set(actor(Set.of("store_staff"), Set.of(), Set.of(STORE_ID)));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "appgate-checkin-permission-denied")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(ARRIVED_AT)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        assertAppGateDeniedWithoutBusinessMutation("PERMISSION_DENIED", "reservation.check_in");
    }

    @Test
    void boundaryArtifactsRemainLimitedToReservationCheckInApi() throws Exception {
        assertThat(fixture.scalarString("select to_regclass('public.check_ins')::text")).isNull();

        try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java", "com", "rpb", "reservation"))) {
            List<String> sourceFiles = paths
                .filter(Files::isRegularFile)
                .map(path -> path.toString().replace('\\', '/'))
                .toList();

            assertThat(sourceFiles)
                .noneMatch(path -> path.toLowerCase().contains("checkinentity"))
                .noneMatch(ReservationCheckInApiIntegrationTest::isForbiddenQueueApiFile)
                .noneMatch(path -> path.toLowerCase().contains("/seating/api/"))
                .noneMatch(path -> path.toLowerCase().contains("tableassignmentcontroller"))
                .noneMatch(path -> path.toLowerCase().contains("reservationnoshowcontroller"))
                .noneMatch(path -> path.toLowerCase().contains("reservationcancellationcontroller"));
        }
    }

    private static boolean isForbiddenQueueApiFile(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (!normalized.contains("/queue/api/")) {
            return false;
        }
        return !Set.of(
            "src/main/java/com/rpb/reservation/queue/api/CallQueueTicketRequest.java",
            "src/main/java/com/rpb/reservation/queue/api/CallQueueTicketResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatCalledQueueTicketRequest.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatCalledQueueTicketResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCallApiErrorCode.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCallApiErrorMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCallApiErrorResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCallApiMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCallController.java",
            "src/main/java/com/rpb/reservation/queue/api/CancelQueueTicketRequest.java",
            "src/main/java/com/rpb/reservation/queue/api/CancelQueueTicketResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCancelApiErrorCode.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCancelApiErrorMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCancelApiErrorResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCancelApiMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCancelController.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueTicketListApiErrorCode.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueTicketListApiErrorMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueTicketListApiErrorResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueTicketListApiMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueTicketListController.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueTicketListResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueSkipApiErrorCode.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueSkipApiErrorMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueSkipApiErrorResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueSkipApiMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueSkipController.java",
            "src/main/java/com/rpb/reservation/queue/api/SkipQueueTicketRequest.java",
            "src/main/java/com/rpb/reservation/queue/api/SkipQueueTicketResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiErrorCode.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiErrorMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiErrorResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueRejoinController.java",
            "src/main/java/com/rpb/reservation/queue/api/RejoinQueueTicketRequest.java",
            "src/main/java/com/rpb/reservation/queue/api/RejoinQueueTicketResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorCode.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueController.java"
        ).contains(normalized);
    }

    private void assertSuccessfulCheckInDatabaseState(String idempotencyKey, UUID reservationId, Instant arrivedAt) {
        assertThat(fixture.count("reservations")).isEqualTo(1);
        assertThat(fixture.scalarString("select status from reservations where id = ?", reservationId)).isEqualTo("arrived");
        assertThat(fixture.scalarOffsetDateTime("select updated_at from reservations where id = ?", reservationId))
            .isEqualTo(OffsetDateTime.ofInstant(arrivedAt, ZoneOffset.UTC));
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'reservation.arrived'"))
            .isEqualTo(1);
        assertThat(fixture.scalarString("select metadata ->> 'arrivedAt' from business_events where target_id = ?", reservationId))
            .isEqualTo(arrivedAt.toString());
        assertThat(fixture.countWhere("""
            select count(*) from state_transition_logs
            where target_type = 'reservation'
              and target_id = ?
              and from_status = 'confirmed'
              and to_status = 'arrived'
              and transition_code = 'reservation.check_in'
            """, reservationId)).isEqualTo(1);
        assertThat(fixture.countWhere("""
            select count(*) from audit_logs
            where operation_code = 'reservation.check_in'
              and target_type = 'reservation'
              and target_id = ?
            """, reservationId)).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("completed");
        assertThat(fixture.scalarString("select target_type from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("reservation");
        assertThat(fixture.scalarString("select target_id::text from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo(reservationId.toString());
    }

    private void assertAppGateDeniedWithoutBusinessMutation(String expectedReason, String expectedPermission) {
        assertConfirmedReservationUnchanged(RESERVATION_ID);
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertBoundaryTablesRemainEmpty();
        assertThat(fixture.countWhere("""
            select count(*) from app_gate_audit_logs
            where tenant_id = ?
              and store_id = ?
              and app_key = 'reservation_queue'
              and action = 'APP_GATE_DENIED'
              and operator_user_id = ?
              and operator_role = 'staff'
              and after_json ->> 'denyReason' = ?
              and after_json ->> 'requiredPermission' = ?
            """, TENANT_ID, STORE_ID, ACTOR_ID, expectedReason, expectedPermission)).isEqualTo(1);
    }

    private void assertConfirmedReservationUnchanged(UUID reservationId) {
        assertThat(fixture.scalarString("select status from reservations where id = ?", reservationId)).isEqualTo("confirmed");
        assertThat(fixture.scalarOffsetDateTime("select updated_at from reservations where id = ?", reservationId))
            .isEqualTo(OffsetDateTime.ofInstant(START_AT.minusSeconds(3600), ZoneOffset.UTC));
        assertBoundaryTablesRemainEmpty();
    }

    private void assertBoundaryTablesRemainEmpty() {
        assertThat(fixture.count("queue_tickets")).isEqualTo(0);
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("table_locks")).isEqualTo(0);
        assertThat(fixture.count("reservation_preassignments")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from reservations where status in ('no_show')")).isEqualTo(0);
    }

    private static String requestJson(Instant arrivedAt) {
        return """
            {
              "arrivedAt": "%s",
              "reasonCode": "customer_arrived",
              "note": "Guest is waiting at host stand"
            }
            """.formatted(arrivedAt);
    }

    private static String hash(UUID reservationId, CheckInReservationRequest request) {
        return ReservationCheckInApplicationService.requestHash(new CheckInReservationCommand(
            TENANT_ID,
            STORE_ID,
            reservationId,
            "unused-for-hash",
            ACTOR_ID,
            "staff",
            request.arrivedAt(),
            request.reasonCode(),
            request.note()
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
        Clock testClock() {
            return Clock.fixed(Instant.parse("2030-06-20T02:00:00Z"), ZoneOffset.UTC);
        }

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

    private static final class CheckInFixture {
        private final JdbcTemplate jdbc;

        private CheckInFixture(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        void reset() {
            jdbc.execute("truncate table tenants cascade");
        }

        void createBaseStore() {
            jdbc.update(
                """
                insert into tenants (id, tenant_code, display_name, status, default_locale)
                values (?, 'tenant-checkin-api-it', 'CheckIn API Tenant', 'active', 'en-SG')
                """,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into stores (
                    id, tenant_id, store_code, display_name, status,
                    timezone, locale, date_format, time_format, currency
                )
                values (?, ?, 'store-checkin-api-it', 'CheckIn API Store', 'active',
                    'Asia/Singapore', 'en-SG', 'yyyy-MM-dd', 'HH:mm', 'SGD')
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
                values (?, ?, 'C-CHECKIN-API', 'regular', 'CheckIn Guest', null, null, 'active')
                """,
                CUSTOMER_ID,
                TENANT_ID
            );
            enableReservationQueueApp();
        }

        void reservation(UUID reservationId, String reservationCode, String status) {
            jdbc.update(
                """
                insert into reservations (
                    id, tenant_id, store_id, customer_id, reservation_code, party_size,
                    business_date, reserved_start_at, reserved_end_at, hold_until_at,
                    status, source_channel, note, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, 4, ?, ?, ?, ?, ?, 'staff', 'Window seat',
                    ?, ?)
                """,
                reservationId,
                TENANT_ID,
                STORE_ID,
                CUSTOMER_ID,
                reservationCode,
                BUSINESS_DATE,
                utc(START_AT),
                utc(END_AT),
                utc(HOLD_UNTIL_AT),
                status,
                utc(START_AT.minusSeconds(7200)),
                utc(START_AT.minusSeconds(3600))
            );
        }

        void arrivalTransition(UUID reservationId, Instant arrivedAt) {
            jdbc.update(
                """
                insert into state_transition_logs (
                    id, tenant_id, store_id, target_type, target_id,
                    actor_type, actor_id, from_status, to_status,
                    transition_code, triggered_by, metadata, occurred_at
                )
                values (gen_random_uuid(), ?, ?, 'reservation', ?,
                    'staff', ?, 'confirmed', 'arrived',
                    'reservation.check_in', 'staff',
                    ?::jsonb, ?)
                """,
                TENANT_ID,
                STORE_ID,
                reservationId,
                ACTOR_ID,
                "{\"arrivedAt\":\"" + arrivedAt + "\"}",
                utc(arrivedAt)
            );
        }

        void idempotencyRecord(String key, String hash, String status, String targetType, UUID targetId) {
            jdbc.update(
                """
                insert into idempotency_records (
                    id, tenant_id, store_id, idempotency_key, source, action,
                    target_type, target_id, request_hash, response_snapshot, status, expires_at
                )
                values (gen_random_uuid(), ?, ?, ?, 'staff', 'check_in_reservation',
                    ?, ?, ?, null, ?, now() + interval '30 minutes')
                """,
                TENANT_ID,
                STORE_ID,
                key,
                targetType,
                targetId,
                hash,
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
