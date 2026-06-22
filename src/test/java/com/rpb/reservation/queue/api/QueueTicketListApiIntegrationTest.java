package com.rpb.reservation.queue.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class QueueTicketListApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000993");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000993");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000993");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000993");
    private static final UUID QUEUE_GROUP_ID = UUID.fromString("92000000-0000-0000-0000-000000000993");
    private static final UUID WAITING_RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000993");
    private static final UUID CALLED_RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000994");
    private static final UUID SEATED_RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000995");
    private static final UUID WAITING_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000993");
    private static final UUID CALLED_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000994");
    private static final UUID SEATED_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000995");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");
    private static final Instant START_AT = Instant.parse("2030-06-20T03:00:00Z");
    private static final Instant CALLED_AT = Instant.parse("2030-06-20T03:20:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2030-06-20T03:23:00Z");

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
        actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.view"), Set.of(STORE_ID)));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void listsQueueTicketsWithStatusFilterPaginationReservationSummaryAndStableSorting() throws Exception {
        fixture.reservation(WAITING_RESERVATION_ID, "R-LIST-WAITING", "arrived", 4);
        fixture.reservation(CALLED_RESERVATION_ID, "R-LIST-CALLED", "arrived", 4);
        fixture.reservation(SEATED_RESERVATION_ID, "R-LIST-SEATED", "seated", 4);
        fixture.queueTicket(WAITING_TICKET_ID, WAITING_RESERVATION_ID, "waiting", 10, null, null, 1);
        fixture.queueTicket(CALLED_TICKET_ID, CALLED_RESERVATION_ID, "called", 11, CALLED_AT, EXPIRES_AT, 2);
        fixture.queueTicket(SEATED_TICKET_ID, SEATED_RESERVATION_ID, "seated", 12, null, null, 3);

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("status", "called")
                .param("limit", "50")
                .param("offset", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].queueTicketId").value(CALLED_TICKET_ID.toString()))
            .andExpect(jsonPath("$.items[0].queueTicketNumber").value(11))
            .andExpect(jsonPath("$.items[0].queueTicketStatus").value("called"))
            .andExpect(jsonPath("$.items[0].partySize").value(4))
            .andExpect(jsonPath("$.items[0].partySizeGroup").value("3-4"))
            .andExpect(jsonPath("$.items[0].reservationId").value(CALLED_RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.items[0].reservationCode").value("R-LIST-CALLED"))
            .andExpect(jsonPath("$.items[0].reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.items[0].customerName").value("Queue List Guest"))
            .andExpect(jsonPath("$.items[0].customerPhoneMasked").value("****5432"))
            .andExpect(jsonPath("$.items[0].calledAt").value(CALLED_AT.toString()))
            .andExpect(jsonPath("$.items[0].holdUntilAt").value(EXPIRES_AT.toString()))
            .andExpect(jsonPath("$.items[0].expiresAt").value(EXPIRES_AT.toString()))
            .andExpect(jsonPath("$.page.limit").value(50))
            .andExpect(jsonPath("$.page.offset").value(0))
            .andExpect(jsonPath("$.page.total").value(1));

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("limit", "2")
                .param("offset", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].queueTicketId").value(CALLED_TICKET_ID.toString()))
            .andExpect(jsonPath("$.items[1].queueTicketId").value(SEATED_TICKET_ID.toString()))
            .andExpect(jsonPath("$.page.limit").value(2))
            .andExpect(jsonPath("$.page.offset").value(1))
            .andExpect(jsonPath("$.page.total").value(3));

        assertReadOnlyBoundary();
    }

    @Test
    void queryValidationErrorsDoNotWriteBusinessEvidence() throws Exception {
        mockMvc.perform(get(ENDPOINT, STORE_ID).param("status", "queued"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_STATUS"));
        mockMvc.perform(get(ENDPOINT, STORE_ID).param("limit", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_LIMIT"));
        mockMvc.perform(get(ENDPOINT, STORE_ID).param("limit", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_LIMIT"));
        mockMvc.perform(get(ENDPOINT, STORE_ID).param("offset", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_OFFSET"));

        assertReadOnlyBoundary();
    }

    @Test
    void appGateDenyCasesAuditAndDoNotMutateBusinessState() throws Exception {
        fixture.reservation(WAITING_RESERVATION_ID, "R-LIST-GATE", "arrived", 4);
        fixture.queueTicket(WAITING_TICKET_ID, WAITING_RESERVATION_ID, "waiting", 10, null, null, 1);

        jdbc.update("delete from tenant_app_entitlements where tenant_id = ? and app_key = 'reservation_queue'", TENANT_ID);
        mockMvc.perform(get(ENDPOINT, STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("TENANT_APP_NOT_ENABLED"));
        assertAppGateDeniedWithoutBusinessMutation("TENANT_APP_NOT_ENABLED");

        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(WAITING_RESERVATION_ID, "R-LIST-GATE", "arrived", 4);
        fixture.queueTicket(WAITING_TICKET_ID, WAITING_RESERVATION_ID, "waiting", 10, null, null, 1);
        jdbc.update("""
            update store_app_settings
            set is_enabled = false,
                updated_at = now()
            where tenant_id = ? and store_id = ? and app_key = 'reservation_queue'
            """, TENANT_ID, STORE_ID);
        mockMvc.perform(get(ENDPOINT, STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_APP_NOT_ENABLED"));
        assertAppGateDeniedWithoutBusinessMutation("STORE_APP_NOT_ENABLED");

        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(WAITING_RESERVATION_ID, "R-LIST-GATE", "arrived", 4);
        fixture.queueTicket(WAITING_TICKET_ID, WAITING_RESERVATION_ID, "waiting", 10, null, null, 1);
        actorProvider.set(actor(Set.of("store_staff"), Set.of("queue.call"), Set.of(STORE_ID)));
        mockMvc.perform(get(ENDPOINT, STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        assertAppGateDeniedWithoutBusinessMutation("PERMISSION_DENIED");
    }

    private void assertAppGateDeniedWithoutBusinessMutation(String expectedReason) {
        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", WAITING_TICKET_ID)).isEqualTo("waiting");
        assertThat(fixture.scalarString("select status from reservations where id = ?", WAITING_RESERVATION_ID)).isEqualTo("arrived");
        assertThat(fixture.countWhere("""
            select count(*) from app_gate_audit_logs
            where tenant_id = ?
              and store_id = ?
              and app_key = 'reservation_queue'
              and action = 'APP_GATE_DENIED'
              and operator_user_id = ?
              and operator_role = 'staff'
              and after_json ->> 'denyReason' = ?
              and after_json ->> 'requiredPermission' = 'queue.view'
            """, TENANT_ID, STORE_ID, ACTOR_ID, expectedReason)).isEqualTo(1);
        assertReadOnlyBoundary();
    }

    private void assertReadOnlyBoundary() {
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("seating_resources")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("turnovers")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from reservations where status in ('no_show', 'cancelled')")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from queue_tickets where status in ('skipped', 'rejoined')")).isEqualTo(0);
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
                values (?, 'tenant-queue-list-api-it', 'Queue List API Tenant', 'active', 'en-SG')
                """,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into stores (
                    id, tenant_id, store_code, display_name, status,
                    timezone, locale, date_format, time_format, currency
                )
                values (?, ?, 'store-queue-list-api-it', 'Queue List API Store', 'active',
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
                values (?, ?, 'C-QUEUE-LIST-API', 'regular', 'Queue List Guest', null, '+6598765432', 'active')
                """,
                CUSTOMER_ID,
                TENANT_ID
            );
            enableReservationQueueApp();
            queueGroup(QUEUE_GROUP_ID, "3-4", 3, 4, 1);
        }

        void reservation(UUID reservationId, String reservationCode, String status, int partySize) {
            UUID reservationCustomerId = reservationId;
            jdbc.update(
                """
                insert into customers (
                    id, tenant_id, customer_code, customer_type, display_name,
                    nickname, phone_e164, status
                )
                values (?, ?, ?, 'regular', 'Queue List Reservation Guest', null, ?, 'active')
                on conflict (id) do nothing
                """,
                reservationCustomerId,
                TENANT_ID,
                "C-QUEUE-LIST-API-" + reservationId.toString().substring(24),
                "+659876" + reservationId.toString().substring(32)
            );
            jdbc.update(
                """
                insert into reservations (
                    id, tenant_id, store_id, customer_id, reservation_code, party_size,
                    business_date, reserved_start_at, reserved_end_at, hold_until_at,
                    status, source_channel, note, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'staff', 'Queue list note',
                    ?, ?)
                """,
                reservationId,
                TENANT_ID,
                STORE_ID,
                reservationCustomerId,
                reservationCode,
                partySize,
                BUSINESS_DATE,
                utc(START_AT),
                utc(START_AT.plusSeconds(5400)),
                utc(START_AT.plusSeconds(900)),
                status,
                utc(START_AT.minusSeconds(7200)),
                utc(START_AT.minusSeconds(3600))
            );
        }

        void queueTicket(
            UUID queueTicketId,
            UUID reservationId,
            String status,
            int ticketNumber,
            Instant calledAt,
            Instant expiresAt,
            int createdOffset
        ) {
            jdbc.update(
                """
                insert into queue_tickets (
                    id, tenant_id, store_id, queue_group_id, customer_id, reservation_id,
                    walk_in_id, ticket_number, party_size, business_date, status,
                    queue_position, called_at, expires_at, note, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, null, ?, 4, ?, ?,
                    ?, ?, ?, 'Existing ticket', ?, ?)
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
                ticketNumber,
                calledAt == null ? null : utc(calledAt),
                expiresAt == null ? null : utc(expiresAt),
                utc(START_AT.plusSeconds(createdOffset)),
                utc(START_AT.plusSeconds(createdOffset))
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
