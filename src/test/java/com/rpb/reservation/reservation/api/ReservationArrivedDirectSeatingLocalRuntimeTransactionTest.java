package com.rpb.reservation.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.common.scope.StoreScope;
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
import org.springframework.boot.test.mock.mockito.MockReset;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ReservationArrivedDirectSeatingLocalRuntimeTransactionTest {
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();
    private static final String CHECK_IN_DIRECT_ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/seating/check-in-direct";

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000971");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000971");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000971");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000971");
    private static final UUID AREA_ID = UUID.fromString("51000000-0000-0000-0000-000000000971");
    private static final UUID TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000971");
    private static final UUID BOUNDARY_TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000972");
    private static final Instant START_AT = Instant.parse("2030-06-20T03:00:00Z");
    private static final Instant END_AT = Instant.parse("2030-06-20T04:30:00Z");
    private static final Instant ARRIVED_AT = Instant.parse("2030-06-20T02:55:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestCurrentActorProvider actorProvider;

    @SpyBean(reset = MockReset.AFTER)
    private BusinessEventRepositoryPort businessEventRepository;

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
        actorProvider.set(CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("reservation.create", "reservation.check_in", "reservation.seat"),
            Set.of(STORE_ID)
        ));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void seatsReservationCreatedAndCheckedInThroughLocalRuntimePathWithoutRollback() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/stores/{storeId}/reservations", STORE_ID)
                .header("Idempotency-Key", "runtime-create-reservation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createReservationBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("confirmed"))
            .andReturn();
        UUID reservationId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).path("reservationId").asText());

        mockMvc.perform(post("/api/v1/stores/{storeId}/reservations/{reservationId}/check-in", STORE_ID, reservationId)
                .header("Idempotency-Key", "runtime-checkin-reservation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkInBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("arrived"));

        mockMvc.perform(post("/api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct", STORE_ID, reservationId)
                .header("Idempotency-Key", "runtime-seat-reservation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(seatBody(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationStatus").value("seated"))
            .andExpect(jsonPath("$.seatingStatus").value("occupied"))
            .andExpect(jsonPath("$.resourceType").value("table"))
            .andExpect(jsonPath("$.resourceId").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.alreadySeated").value(false))
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        mockMvc.perform(post("/api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct", STORE_ID, reservationId)
                .header("Idempotency-Key", "runtime-seat-already")
                .contentType(MediaType.APPLICATION_JSON)
                .content(seatBody(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.alreadySeated").value(true))
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        assertThat(fixture.scalarString("select status from reservations where id = ?", reservationId)).isEqualTo("seated");
        assertThat(fixture.scalarInteger("select version from reservations where id = ?", reservationId)).isEqualTo(2);
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", TABLE_ID)).isEqualTo("occupied");
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", BOUNDARY_TABLE_ID)).isEqualTo("available");
        assertThat(fixture.countWhere("""
            select count(*) from seatings
            where tenant_id = ?
              and store_id = ?
              and reservation_id = ?
              and queue_ticket_id is null
              and walk_in_id is null
              and status = 'occupied'
              and deleted_at is null
            """, TENANT_ID, STORE_ID, reservationId)).isEqualTo(1);
        assertThat(fixture.countWhere("""
            select count(*) from seating_resources
            where tenant_id = ?
              and store_id = ?
              and resource_type = 'dining_table'
              and table_id = ?
              and table_group_id is null
              and status = 'active'
              and deleted_at is null
            """, TENANT_ID, STORE_ID, TABLE_ID)).isEqualTo(1);
        assertThat(fixture.countWhere("""
            select count(*) from business_events
            where event_type in ('reservation.seated', 'seating.created', 'table.occupied')
            """)).isEqualTo(3);
        assertThat(fixture.countWhere("""
            select count(*) from state_transition_logs
            where transition_code in ('reservation.seat', 'seating.occupy', 'dining_table.occupy')
            """)).isEqualTo(3);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'reservation.seat'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from idempotency_records where status = 'completed'")).isEqualTo(4);
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void missingReservationErrorDoesNotCreateSeatingOrRollbackResponseMapping() throws Exception {
        UUID missingReservationId = UUID.fromString("50000000-0000-0000-0000-000000000979");

        mockMvc.perform(post("/api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct", STORE_ID, missingReservationId)
                .header("Idempotency-Key", "runtime-seat-missing-reservation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(seatBody(TABLE_ID)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("RESERVATION_NOT_FOUND"))
            .andExpect(jsonPath("$.idempotency.status").value("failed"));

        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("seating_resources")).isEqualTo(0);
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", TABLE_ID)).isEqualTo("available");
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void checkInDirectRollsBackReservationAndTableWhenEventWriteFails() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/stores/{storeId}/reservations", STORE_ID)
                .header("Idempotency-Key", "runtime-create-atomic-seat-reservation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createReservationBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("confirmed"))
            .andReturn();
        UUID reservationId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).path("reservationId").asText());

        doThrow(new IllegalStateException("forced event append failure"))
            .when(businessEventRepository)
            .append(any(StoreScope.class), any(BusinessEvent.class));

        mockMvc.perform(post(CHECK_IN_DIRECT_ENDPOINT, STORE_ID, reservationId)
                .header("Idempotency-Key", "runtime-check-in-seat-event-failure")
                .contentType(MediaType.APPLICATION_JSON)
                .content(seatBody(TABLE_ID)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("EVENT_WRITE_FAILED"))
            .andExpect(jsonPath("$.idempotency.status").value("failed"));

        assertThat(fixture.scalarString("select status from reservations where id = ?", reservationId)).isEqualTo("confirmed");
        assertThat(fixture.scalarInteger("select version from reservations where id = ?", reservationId)).isEqualTo(0);
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", TABLE_ID)).isEqualTo("available");
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("seating_resources")).isEqualTo(0);
        assertThat(fixture.countWhere("""
            select count(*) from idempotency_records
            where idempotency_key = 'runtime-check-in-seat-event-failure'
              and action = 'check_in_and_seat_reservation'
              and status = 'failed'
            """)).isEqualTo(1);
        assertThat(fixture.countWhere("""
            select count(*) from audit_logs
            where operation_code = 'reservation.check_in_and_seat.failed'
            """)).isEqualTo(1);
        assertBoundaryTablesRemainEmpty();
    }

    private void assertBoundaryTablesRemainEmpty() {
        assertThat(fixture.count("queue_tickets")).isEqualTo(0);
        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("turnovers")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from reservations where status in ('no_show', 'cancelled')")).isEqualTo(0);
    }

    private static String createReservationBody() {
        return """
            {
              "partySize": 4,
              "reservedStartAt": "%s",
              "reservedEndAt": "%s",
              "customerId": "%s",
              "customerName": null,
              "customerNickname": null,
              "phoneE164": null,
              "note": "Local runtime transaction regression"
            }
            """.formatted(START_AT, END_AT, CUSTOMER_ID);
    }

    private static String checkInBody() {
        return """
            {
              "arrivedAt": "%s",
              "reasonCode": "customer_arrived",
              "note": "Guest is at host stand"
            }
            """.formatted(ARRIVED_AT);
    }

    private static String seatBody(UUID tableId) {
        return """
            {
              "tableId": "%s",
              "tableGroupId": null,
              "overrideReasonCode": null,
              "overrideNote": null,
              "note": "Seat arrived reservation"
            }
            """.formatted(tableId);
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
                values (?, 'tenant-seat-runtime-it', 'Seat Runtime Tenant', 'active', 'en-SG')
                """,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into stores (
                    id, tenant_id, store_code, display_name, status,
                    timezone, locale, date_format, time_format, currency
                )
                values (?, ?, 'store-seat-runtime-it', 'Seat Runtime Store', 'active',
                    'Asia/Singapore', 'en-SG', 'DD-MM-YYYY', 'HH:mm', 'SGD')
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
                values (?, ?, 'C-SEAT-RUNTIME', 'regular', 'Seat Runtime Guest', null, null, 'active')
                """,
                CUSTOMER_ID,
                TENANT_ID
            );
            enableReservationQueueApp();
            table(TABLE_ID, "R1", 1, 4, "available");
            table(BOUNDARY_TABLE_ID, "R2", 1, 4, "available");
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
