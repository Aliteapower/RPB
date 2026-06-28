package com.rpb.reservation.platformbilling;

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

class PlatformBillingMigrationTest {
    private static final UUID VALIDATION_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000983");
    private static final UUID VALIDATION_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000983");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000984");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000984");
    private static final UUID LEGACY_OPERATOR_ID = UUID.fromString("30000000-0000-0000-0000-000000009984");
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();
    private static final JdbcTemplate JDBC = new JdbcTemplate(dataSource());

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @Test
    void createsManualBillingTablesBackfillsLegacyGrantsAndDoesNotExpireExistingEntitlements() {
        DATABASE.applyMigration("src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql");
        insertTenantAndStore(VALIDATION_TENANT_ID, VALIDATION_STORE_ID, "20000000", "1000", "SGD");
        insertTenantAndStore(OTHER_TENANT_ID, OTHER_STORE_ID, "tenant-other", "store-other", "cny");
        DATABASE.applyMigration("src/main/resources/db/migration/V002__app_gate_foundation.sql");
        JDBC.update(
            """
            update tenant_app_entitlements
            set enabled_by = ?
            where tenant_id = ?
              and app_key = 'reservation_queue'
            """,
            LEGACY_OPERATOR_ID,
            OTHER_TENANT_ID
        );
        DATABASE.applyMigration("src/main/resources/db/migration/V003__auth_minimal_login.sql");
        DATABASE.applyMigration("src/main/resources/db/migration/V004__platform_tenant_admin_bootstrap.sql");
        DATABASE.applyMigration("src/main/resources/db/migration/V005__platform_tenant_contact_account_fields.sql");
        DATABASE.applyMigration("src/main/resources/db/migration/V006__tenant_admin_management_foundation.sql");
        DATABASE.applyMigration("src/main/resources/db/migration/V007__queue_display_ad_config.sql");

        DATABASE.applyMigration("src/main/resources/db/migration/V008__platform_product_line_billing.sql");

        assertThat(tableExists("tenant_product_subscriptions")).isTrue();
        assertThat(tableExists("tenant_product_subscription_events")).isTrue();
        assertThat(tableExists("platform_product_line_prices")).isFalse();
        assertThat(tableExists("call_screen_media_carousel")).isFalse();

        assertThat(stringValue("""
            select app_name
            from platform_apps
            where app_key = 'reservation_queue'
            """)).isEqualTo("预约排队叫号产线");

        assertThat(countWhere("""
            select count(*)
            from tenant_product_subscriptions
            where tenant_id in (?, ?)
              and app_key = 'reservation_queue'
              and billing_cycle = 'legacy_grant'
              and status = 'active'
              and current_period_end is null
            """, VALIDATION_TENANT_ID, OTHER_TENANT_ID)).isEqualTo(2);

        assertThat(countWhere("""
            select count(*)
            from tenant_app_entitlements
            where tenant_id in (?, ?)
              and app_key = 'reservation_queue'
              and status = 'enabled'
              and valid_until is null
            """, VALIDATION_TENANT_ID, OTHER_TENANT_ID)).isEqualTo(2);

        assertThat(countWhere("""
            select count(*)
            from tenant_product_subscription_events
            where event_type = 'manual_adjust'
              and idempotency_key like 'legacy-grant-backfill-%'
            """)).isEqualTo(2);

        assertThat(stringValue("""
            select currency
            from tenant_product_subscriptions
            where tenant_id = ?
              and app_key = 'reservation_queue'
            """, OTHER_TENANT_ID)).isEqualTo("CNY");
        assertThat(uuidValue("""
            select operator_user_id
            from tenant_product_subscriptions
            where tenant_id = ?
              and app_key = 'reservation_queue'
            """, OTHER_TENANT_ID)).isEqualTo(LEGACY_OPERATOR_ID);

        assertThat(countWhere("""
            select count(*)
            from auth_account_permissions permission
            join auth_accounts account on account.id = permission.account_id
            where account.username = 'sysadmin'
              and permission.permission_code in ('platform.product_line.manage', 'platform.billing.manage')
              and permission.deleted_at is null
            """)).isEqualTo(2);

        assertThat(indexExists("ux_tenant_product_subscriptions_scope")).isTrue();
        assertThat(indexExists("ux_tenant_product_subscription_events_idempotency")).isTrue();
        assertThat(constraintExists("ck_tenant_product_subscriptions_cycle")).isTrue();
        assertThat(constraintExists("ck_tenant_product_subscriptions_status")).isTrue();

        assertThatThrownBy(() -> JDBC.update(
            """
            insert into tenant_product_subscriptions (
                tenant_id, app_key, billing_cycle, status,
                current_period_start, current_period_end, amount, currency
            )
            values (?, 'reservation_queue', 'weekly', 'active', now(), now() + interval '7 days', 10, 'SGD')
            """,
            VALIDATION_TENANT_ID
        )).hasMessageContaining("ck_tenant_product_subscriptions_cycle");

        assertThatThrownBy(() -> JDBC.update(
            """
            insert into tenant_product_subscriptions (
                tenant_id, app_key, billing_cycle, status,
                current_period_start, current_period_end, amount, currency
            )
            values (?, 'reservation_queue', 'monthly', 'active', now(), now() - interval '1 day', 10, 'SGD')
            """,
            VALIDATION_TENANT_ID
        )).hasMessageContaining("ck_tenant_product_subscriptions_period");

        int subscriptionCountBeforeReplay = countWhere("select count(*) from tenant_product_subscriptions");
        int eventCountBeforeReplay = countWhere("select count(*) from tenant_product_subscription_events");

        DATABASE.applyMigration("src/main/resources/db/migration/V008__platform_product_line_billing.sql");

        assertThat(countWhere("select count(*) from tenant_product_subscriptions")).isEqualTo(subscriptionCountBeforeReplay);
        assertThat(countWhere("select count(*) from tenant_product_subscription_events")).isEqualTo(eventCountBeforeReplay);

        DATABASE.applyMigration("src/main/resources/db/migration/V010__platform_product_line_prices.sql");

        assertThat(tableExists("platform_product_line_prices")).isTrue();
        assertThat(indexExists("ux_platform_product_line_prices_scope")).isTrue();
        assertThat(constraintExists("ck_platform_product_line_prices_cycle")).isTrue();
        assertThat(constraintExists("ck_platform_product_line_prices_amount")).isTrue();
        assertThat(constraintExists("ck_platform_product_line_prices_currency")).isTrue();
        assertThat(countWhere("""
            select count(*)
            from platform_product_line_prices
            where app_key = 'reservation_queue'
              and billing_cycle in ('monthly', 'yearly')
              and amount = 0
              and currency = 'SGD'
              and status = 'active'
            """)).isEqualTo(2);

        assertThatThrownBy(() -> JDBC.update(
            """
            insert into platform_product_line_prices (app_key, billing_cycle, amount, currency, status)
            values ('reservation_queue', 'weekly', 1, 'SGD', 'active')
            """
        )).hasMessageContaining("ck_platform_product_line_prices_cycle");

        assertThatThrownBy(() -> JDBC.update(
            """
            insert into platform_product_line_prices (app_key, billing_cycle, amount, currency, status)
            values ('reservation_queue', 'monthly', -1, 'SGD', 'active')
            """
        )).hasMessageContaining("ck_platform_product_line_prices_amount");

        int priceCountBeforeReplay = countWhere("select count(*) from platform_product_line_prices");
        DATABASE.applyMigration("src/main/resources/db/migration/V010__platform_product_line_prices.sql");
        assertThat(countWhere("select count(*) from platform_product_line_prices")).isEqualTo(priceCountBeforeReplay);
    }

    private static void insertTenantAndStore(UUID tenantId, UUID storeId, String tenantCode, String storeCode, String currency) {
        JDBC.update(
            """
            insert into tenants (id, tenant_code, display_name, status, default_locale)
            values (?, ?, '食刻租户', 'active', 'zh-CN')
            """,
            tenantId,
            tenantCode
        );
        JDBC.update(
            """
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency
            )
            values (?, ?, ?, '食刻门店', 'active',
                'Asia/Singapore', 'zh-CN', 'DD-MM-YYYY', 'HH:mm', ?)
            """,
            storeId,
            tenantId,
            storeCode,
            currency
        );
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(DATABASE.jdbcUrl());
        dataSource.setUsername(DATABASE.username());
        dataSource.setPassword(DATABASE.password());
        return dataSource;
    }

    private static boolean tableExists(String tableName) {
        return countWhere("""
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name = ?
            """, tableName) == 1;
    }

    private static boolean indexExists(String indexName) {
        return countWhere("""
            select count(*)
            from pg_indexes
            where schemaname = 'public'
              and indexname = ?
            """, indexName) == 1;
    }

    private static boolean constraintExists(String constraintName) {
        return countWhere("""
            select count(*)
            from pg_constraint
            where conname = ?
            """, constraintName) == 1;
    }

    private static String stringValue(String sql, Object... args) {
        return JDBC.queryForObject(sql, String.class, args);
    }

    private static UUID uuidValue(String sql, Object... args) {
        return JDBC.queryForObject(sql, UUID.class, args);
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
            run(
                command("psql"),
                "-v", "ON_ERROR_STOP=1",
                "-h", "127.0.0.1",
                "-p", String.valueOf(port),
                "-U", username(),
                "-d", "postgres",
                "-f", Path.of(migrationPath).toAbsolutePath().toString()
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
                outputFile = Files.createTempFile("rpb-platform-billing-pg-command-", ".log");
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
