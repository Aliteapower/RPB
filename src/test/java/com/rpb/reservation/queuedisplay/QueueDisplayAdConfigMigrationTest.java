package com.rpb.reservation.queuedisplay;

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

class QueueDisplayAdConfigMigrationTest {
    private static final UUID VALIDATION_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000983");
    private static final UUID VALIDATION_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000983");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000984");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000984");
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();
    private static final JdbcTemplate JDBC = new JdbcTemplate(dataSource());

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @Test
    void createsTextOnlyCallScreenAdConfigTablesSeedsDefaultSlidesAndDisplayPermission() {
        DATABASE.applyMigration("src/main/resources/db/migration/V001__reservation_platform_bootstrap.sql");
        insertTenantAndStore(VALIDATION_TENANT_ID, VALIDATION_STORE_ID, "20000000", "1000");
        insertTenantAndStore(OTHER_TENANT_ID, OTHER_STORE_ID, "tenant-other", "store-other");
        DATABASE.applyMigration("src/main/resources/db/migration/V002__app_gate_foundation.sql");
        DATABASE.applyMigration("src/main/resources/db/migration/V003__auth_minimal_login.sql");
        DATABASE.applyMigration("src/main/resources/db/migration/V004__platform_tenant_admin_bootstrap.sql");
        DATABASE.applyMigration("src/main/resources/db/migration/V005__platform_tenant_contact_account_fields.sql");
        DATABASE.applyMigration("src/main/resources/db/migration/V006__tenant_admin_management_foundation.sql");

        DATABASE.applyMigration("src/main/resources/db/migration/V007__queue_display_ad_config.sql");

        assertThat(tableExists("platform_call_screen_ad_seed_sets")).isTrue();
        assertThat(tableExists("platform_call_screen_ad_seed_slides")).isTrue();
        assertThat(tableExists("tenant_call_screen_ad_sets")).isTrue();
        assertThat(tableExists("tenant_call_screen_text_slides")).isTrue();
        assertThat(tableExists("store_call_screen_settings")).isTrue();
        assertThat(tableExists("tenant_call_screen_image_slides")).isFalse();
        assertThat(tableExists("call_screen_media_assets")).isFalse();
        assertThat(tableExists("platform_call_screen_media_seed_slides")).isFalse();
        assertThat(tableExists("tenant_call_screen_media_slides")).isFalse();

        assertThat(countWhere("""
            select count(*)
            from platform_call_screen_ad_seed_sets
            where seed_key = 'restaurant_default'
              and status = 'active'
              and ad_type = 'text'
              and deleted_at is null
            """)).isGreaterThanOrEqualTo(1);

        assertThat(strings("""
            select slide.title
            from platform_call_screen_ad_seed_slides slide
            join platform_call_screen_ad_seed_sets seed on seed.id = slide.seed_set_id
            where seed.seed_key = 'restaurant_default'
              and slide.deleted_at is null
            order by slide.sort_order
            """)).containsExactly("欢迎光临", "今日推荐", "特惠活动", "会员专享");

        assertThat(countWhere("""
            select count(*)
            from auth_account_permissions permission
            join auth_accounts account on account.id = permission.account_id
            where account.username in ('sysadmin', '20000000', '1000')
              and account.tenant_id = ?
              and permission.permission_code = 'queue.display.view'
              and permission.deleted_at is null
            """, VALIDATION_TENANT_ID)).isEqualTo(3);

        assertThat(countWhere("""
            select count(*)
            from auth_account_permissions permission
            join auth_accounts account on account.id = permission.account_id
            where account.username = 'sysadmin'
              and permission.permission_code = 'platform.call_screen_ad.manage'
              and permission.deleted_at is null
            """)).isEqualTo(1);

        assertThat(indexExists("uq_store_call_screen_settings_scope")).isTrue();
        assertThat(indexExists("ux_tenant_call_screen_text_slides_active_sort")).isTrue();
        assertThat(constraintExists("fk_store_call_screen_settings_active_ad_set_scope")).isTrue();

        UUID textAdSetId = insertAdSet(VALIDATION_TENANT_ID, "Validation Text", "text");
        UUID otherTenantTextAdSetId = insertAdSet(OTHER_TENANT_ID, "Other Text", "text");

        JDBC.update(
            """
            insert into tenant_call_screen_text_slides (
                tenant_id, ad_set_id, title, subtitle, tagline, sort_order, status
            )
            values (?, ?, '欢迎光临', '食刻 · 餐厅', '新鲜食材 · 匠心烹饪 · 极致服务', 1, 'active')
            """,
            VALIDATION_TENANT_ID,
            textAdSetId
        );
        JDBC.update(
            """
            insert into tenant_call_screen_text_slides (
                tenant_id, ad_set_id, title, subtitle, tagline, sort_order, status
            )
            values (?, ?, '备用文案', '可停用', '排序可复用', 1, 'disabled')
            """,
            VALIDATION_TENANT_ID,
            textAdSetId
        );

        assertThatThrownBy(() -> JDBC.update(
            """
            insert into tenant_call_screen_text_slides (
                tenant_id, ad_set_id, title, subtitle, tagline, sort_order, status
            )
            values (?, ?, '重复', '重复', '重复', 1, 'active')
            """,
            VALIDATION_TENANT_ID,
            textAdSetId
        )).hasMessageContaining("ux_tenant_call_screen_text_slides_active_sort");

        assertThatThrownBy(() -> insertAdSet(VALIDATION_TENANT_ID, "Rejected Image", "image"))
            .hasMessageContaining("ck_tenant_call_screen_ad_sets_type");
        assertThatThrownBy(() -> insertAdSet(VALIDATION_TENANT_ID, "Rejected Media", "media"))
            .hasMessageContaining("ck_tenant_call_screen_ad_sets_type");

        assertThatThrownBy(() -> JDBC.update(
            """
            insert into platform_call_screen_ad_seed_sets (seed_key, display_name, status, ad_type)
            values ('image_template', '图片模板', 'active', 'image')
            """
        )).hasMessageContaining("ck_platform_call_screen_ad_seed_sets_type");

        assertThatThrownBy(() -> JDBC.update(
            """
            insert into store_call_screen_settings (
                tenant_id, store_id, active_ad_set_id, ad_mode
            )
            values (?, ?, ?, 'text')
            """,
            VALIDATION_TENANT_ID,
            VALIDATION_STORE_ID,
            otherTenantTextAdSetId
        )).hasMessageContaining("fk_store_call_screen_settings_active_ad_set_scope");

        assertThatThrownBy(() -> JDBC.update(
            """
            insert into store_call_screen_settings (
                tenant_id, store_id, active_ad_set_id, ad_mode
            )
            values (?, ?, null, 'media')
            """,
            VALIDATION_TENANT_ID,
            VALIDATION_STORE_ID
        )).hasMessageContaining("ck_store_call_screen_settings_mode");

        int seedSlideCountBeforeReplay = countWhere("""
            select count(*)
            from platform_call_screen_ad_seed_slides slide
            join platform_call_screen_ad_seed_sets seed on seed.id = slide.seed_set_id
            where seed.seed_key = 'restaurant_default'
              and slide.deleted_at is null
            """);
        int permissionCountBeforeReplay = countWhere("""
            select count(*)
            from auth_account_permissions
            where permission_code = 'queue.display.view'
              and deleted_at is null
            """);
        int platformPermissionCountBeforeReplay = countWhere("""
            select count(*)
            from auth_account_permissions
            where permission_code = 'platform.call_screen_ad.manage'
              and deleted_at is null
            """);

        DATABASE.applyMigration("src/main/resources/db/migration/V007__queue_display_ad_config.sql");

        assertThat(countWhere("""
            select count(*)
            from platform_call_screen_ad_seed_slides slide
            join platform_call_screen_ad_seed_sets seed on seed.id = slide.seed_set_id
            where seed.seed_key = 'restaurant_default'
              and slide.deleted_at is null
            """)).isEqualTo(seedSlideCountBeforeReplay);
        assertThat(countWhere("""
            select count(*)
            from auth_account_permissions
            where permission_code = 'queue.display.view'
              and deleted_at is null
            """)).isEqualTo(permissionCountBeforeReplay);
        assertThat(countWhere("""
            select count(*)
            from auth_account_permissions
            where permission_code = 'platform.call_screen_ad.manage'
              and deleted_at is null
            """)).isEqualTo(platformPermissionCountBeforeReplay);
    }

    private static void insertTenantAndStore(UUID tenantId, UUID storeId, String tenantCode, String storeCode) {
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
                'Asia/Singapore', 'zh-CN', 'yyyy-MM-dd', 'HH:mm', 'CNY')
            """,
            storeId,
            tenantId,
            storeCode
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

    private static int countWhere(String sql, Object... args) {
        return JDBC.queryForObject(sql, Integer.class, args);
    }

    private static List<String> strings(String sql) {
        return JDBC.queryForList(sql, String.class);
    }

    private static UUID insertAdSet(UUID tenantId, String name, String adType) {
        return insertAdSet(tenantId, name, adType, "active");
    }

    private static UUID insertAdSet(UUID tenantId, String name, String adType, String status) {
        return JDBC.queryForObject(
            """
            insert into tenant_call_screen_ad_sets (tenant_id, name, ad_type, status)
            values (?, ?, ?, ?)
            returning id
            """,
            UUID.class,
            tenantId,
            name,
            adType,
            status
        );
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
