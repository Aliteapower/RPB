package com.rpb.reservation.appgate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AppGateMigrationTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000009001");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000009001");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000009001");
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();
    private static final JdbcTemplate JDBC = new JdbcTemplate(dataSource());

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @Test
    void createsAppGateTablesSeedsReservationQueueAndBackfillsExistingTenantAndStore() {
        DATABASE.applyMigration("src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql");
        insertExistingTenantAndStore();

        DATABASE.applyMigration("src/main/resources/db/migration/V002__app_gate_foundation.sql");

        assertThat(tableExists("platform_apps")).isTrue();
        assertThat(tableExists("tenant_app_entitlements")).isTrue();
        assertThat(tableExists("store_app_settings")).isTrue();
        assertThat(tableExists("app_gate_audit_logs")).isTrue();

        assertThat(countWhere("""
            select count(*) from platform_apps
            where app_key = 'reservation_queue'
              and app_name = '订位排号系统'
              and status = 'active'
              and default_entry_route = '/stores/:storeId/staff'
            """)).isEqualTo(1);

        assertThat(countWhere("""
            select count(*) from tenant_app_entitlements
            where tenant_id = ?
              and app_key = 'reservation_queue'
              and status = 'enabled'
            """, TENANT_ID)).isEqualTo(1);

        assertThat(countWhere("""
            select count(*) from store_app_settings
            where tenant_id = ?
              and store_id = ?
              and app_key = 'reservation_queue'
              and is_enabled = true
              and entry_visible = true
            """, TENANT_ID, STORE_ID)).isEqualTo(1);

        JDBC.update(
            """
            insert into app_gate_audit_logs (
                tenant_id, store_id, app_key, action, operator_user_id,
                operator_role, before_json, after_json
            )
            values (?, ?, 'reservation_queue', 'APP_GATE_DENIED', ?, 'staff', null,
                '{"decision":"denied","denyReason":"PERMISSION_DENIED","requiredPermission":"reservation.create"}'::jsonb)
            """,
            TENANT_ID,
            STORE_ID,
            ACTOR_ID
        );
        assertThat(countWhere("""
            select count(*) from app_gate_audit_logs
            where tenant_id = ?
              and store_id = ?
              and app_key = 'reservation_queue'
              and action = 'APP_GATE_DENIED'
              and after_json ->> 'denyReason' = 'PERMISSION_DENIED'
            """, TENANT_ID, STORE_ID)).isEqualTo(1);
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(DATABASE.jdbcUrl());
        dataSource.setUsername(DATABASE.username());
        dataSource.setPassword(DATABASE.password());
        return dataSource;
    }

    private static void insertExistingTenantAndStore() {
        JDBC.update(
            """
            insert into tenants (id, tenant_code, display_name, status, default_locale)
            values (?, 'tenant-appgate-it', 'App Gate Existing Tenant', 'active', 'en-SG')
            """,
            TENANT_ID
        );
        JDBC.update(
            """
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency
            )
            values (?, ?, 'store-appgate-it', 'App Gate Existing Store', 'active',
                'Asia/Singapore', 'en-SG', 'yyyy-MM-dd', 'HH:mm', 'SGD')
            """,
            STORE_ID,
            TENANT_ID
        );
    }

    private static boolean tableExists(String tableName) {
        return countWhere("""
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name = ?
            """, tableName) == 1;
    }

    private static int countWhere(String sql, Object... args) {
        return JDBC.queryForObject(sql, Integer.class, args);
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

        void applyMigration(String migrationPath) {
            Path migration = Path.of(migrationPath).toAbsolutePath();
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
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(LocalPostgresTestDatabase::deleteOne);
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
