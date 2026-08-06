package com.rpb.reservation.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class PaymentMigrationTest {
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();
    private static final JdbcTemplate JDBC = new JdbcTemplate(dataSource());
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EXISTING_TENANT_ADMIN_ACCOUNT_ID = UUID.fromString("30000000-0000-0000-0000-000000000048");
    private static boolean migrationsApplied;

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @Test
    void seedsPaymentProductLineEntryPermissionsAndDefaultPrices() {
        applyMigrationsOnce();

        assertThat(countWhere("""
            select count(*) from platform_apps
            where app_key = 'payment'
              and app_name = 'PayNow 支付产线'
              and status = 'active'
              and default_entry_route = '/stores/:storeId/payments'
              and jsonb_exists(config_json -> 'entryPermissions', 'payment.intent.create')
            """)).isEqualTo(1);

        assertThat(countWhere("""
            select count(*) from platform_product_line_prices
            where app_key = 'payment'
              and billing_cycle in ('monthly', 'yearly')
              and amount = 0.00
              and currency = 'SGD'
            """)).isEqualTo(2);
    }

    @Test
    void grantsPaymentPermissionsToExistingTenantAdmins() {
        try (LocalPostgresTestDatabase database = LocalPostgresTestDatabase.start()) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource(database));
            database.applyMigrationsUntil("V047__payment_product_line_foundation.sql");
            insertExistingTenantAdminWithoutPaymentPermissions(jdbc);

            database.applyMigrationsAfter("V047__payment_product_line_foundation.sql");

            assertThat(countWhere(jdbc, """
                with required_permissions(permission_code) as (
                    values
                        ('payment.settings.manage'),
                        ('payment.intent.view'),
                        ('payment.intent.create'),
                        ('payment.verification.review')
                )
                select count(*)
                from required_permissions permission
                where not exists (
                    select 1
                    from auth_account_permissions existing
                    where existing.account_id = ?
                      and existing.permission_code = permission.permission_code
                      and existing.deleted_at is null
                )
                """, EXISTING_TENANT_ADMIN_ACCOUNT_ID)).isZero();
        }
    }

    private static void insertExistingTenantAdminWithoutPaymentPermissions(JdbcTemplate jdbc) {
        ensureStoreScope(jdbc);
        jdbc.update("""
            insert into auth_accounts (
                id,
                tenant_id,
                username,
                display_name,
                actor_type,
                status,
                password_hash,
                password_algo,
                default_store_id
            )
            values (
                ?,
                ?,
                'paynow-existing-admin',
                'PayNow Existing Admin',
                'tenant_admin',
                'active',
                '$2a$10$ktA3gOgzus6v0bsJqw53.OerYPoQT6oet7NDdkmNhYYZaKH9ix9Vy',
                'bcrypt-lowercase-v1',
                ?
            )
            """, EXISTING_TENANT_ADMIN_ACCOUNT_ID, TENANT_ID, STORE_ID);
        jdbc.update("""
            insert into auth_account_roles (account_id, role_code)
            values (?, 'tenant_admin')
            """, EXISTING_TENANT_ADMIN_ACCOUNT_ID);
        jdbc.update("""
            insert into auth_account_permissions (account_id, permission_code)
            values (?, 'tenant.admin.manage')
            """, EXISTING_TENANT_ADMIN_ACCOUNT_ID);
        jdbc.update("""
            insert into auth_account_store_access (account_id, tenant_id, store_id)
            values (?, ?, ?)
            """, EXISTING_TENANT_ADMIN_ACCOUNT_ID, TENANT_ID, STORE_ID);
    }

    @Test
    void createsPaymentOperationalTablesAndRejectsInvalidAmount() {
        applyMigrationsOnce();

        assertThat(tableExists("payment_method_profiles")).isTrue();
        assertThat(tableExists("payment_intents")).isTrue();
        assertThat(tableExists("payment_sessions")).isTrue();
        assertThat(tableExists("payment_display_counters")).isTrue();
        assertThat(tableExists("payment_proofs")).isTrue();
        assertThat(tableExists("payment_ocr_results")).isTrue();
        assertThat(tableExists("payment_verifications")).isTrue();
        assertThat(tableExists("payment_events")).isTrue();

        ensureStoreScope();

        assertThatThrownBy(() -> JDBC.update("""
            insert into payment_intents (
                tenant_id,
                store_id,
                intent_no,
                source_type,
                method,
                amount,
                currency,
                payment_reference,
                status,
                idempotency_key
            )
            values (?, ?, 'PIT-TEST', 'quick_pay', 'paynow', -1.00, 'SGD', 'QP-TEST', 'pending', 'intent-negative-amount')
            """, TENANT_ID, STORE_ID))
            .hasMessageContaining("ck_payment_intents_amount");
    }

    private static DriverManagerDataSource dataSource() {
        return dataSource(DATABASE);
    }

    private static DriverManagerDataSource dataSource(LocalPostgresTestDatabase database) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(database.jdbcUrl());
        dataSource.setUsername(database.username());
        dataSource.setPassword(database.password());
        return dataSource;
    }

    private static int countWhere(String sql, Object... args) {
        return countWhere(JDBC, sql, args);
    }

    private static int countWhere(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static boolean tableExists(String tableName) {
        Integer count = JDBC.queryForObject("""
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name = ?
            """, Integer.class, tableName);
        return count != null && count == 1;
    }

    private static void ensureStoreScope() {
        ensureStoreScope(JDBC);
    }

    private static void ensureStoreScope(JdbcTemplate jdbc) {
        jdbc.update("""
            insert into tenants (
                id,
                tenant_code,
                display_name,
                status
            )
            values (?, 'payment-test-tenant', 'Payment Test Tenant', 'active')
            on conflict (id) do nothing
            """, TENANT_ID);
        jdbc.update("""
            insert into stores (
                id,
                tenant_id,
                store_code,
                display_name,
                status,
                timezone,
                locale,
                date_format,
                time_format,
                currency
            )
            values (?, ?, 'payment-test-store', 'Payment Test Store', 'active', 'Asia/Singapore', 'en-SG', 'yyyy-MM-dd', 'HH:mm', 'SGD')
            on conflict (id) do nothing
            """, STORE_ID, TENANT_ID);
    }

    private static void applyMigrationsOnce() {
        if (migrationsApplied) {
            return;
        }
        DATABASE.applyMigrations();
        migrationsApplied = true;
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

        void applyMigrations() {
            applyMigrations(migration -> true);
        }

        void applyMigrationsUntil(String inclusiveFileName) {
            applyMigrations(migration -> migration.getFileName().toString().compareTo(inclusiveFileName) <= 0);
        }

        void applyMigrationsAfter(String exclusiveFileName) {
            applyMigrations(migration -> migration.getFileName().toString().compareTo(exclusiveFileName) > 0);
        }

        private void applyMigrations(java.util.function.Predicate<Path> filter) {
            Path migrationDirectory = Path.of("src", "main", "resources", "db", "migration").toAbsolutePath();
            List<Path> migrations;
            try (Stream<Path> paths = Files.list(migrationDirectory)) {
                migrations = paths
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .filter(filter)
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
            IOException lastException = null;
            for (int attempt = 0; attempt < 5; attempt++) {
                try {
                    Files.deleteIfExists(path);
                    return;
                } catch (IOException exception) {
                    lastException = exception;
                    sleepBeforeDeleteRetry();
                }
            }
            throw new IllegalStateException("delete_path_failed: " + path, lastException);
        }

        private static void sleepBeforeDeleteRetry() {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
