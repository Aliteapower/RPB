package com.rpb.reservation.payment;

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

class PaymentProofTemplatePlatformMigrationTest extends AbstractPaymentMigrationTest {
    @Test
    void v055CreatesContributionReviewTableAndPlatformPermission() {
        assertThat(columnNames("payment_proof_template_contributions"))
            .contains(
                "id",
                "tenant_id",
                "store_id",
                "source_template_id",
                "platform_template_id",
                "bank_code",
                "bank_name",
                "locale",
                "template_name",
                "layout_json",
                "sample_file_digest",
                "sample_raw_text",
                "sample_ocr_reference",
                "sample_ocr_amount",
                "status",
                "review_note",
                "submitted_by",
                "reviewed_by",
                "reviewed_at",
                "version"
            );
        assertThat(checkConstraintDefinitions("payment_proof_template_contributions"))
            .anyMatch(definition -> definition.contains("submitted") && definition.contains("accepted") && definition.contains("rejected"));
        assertThat(permissionCodes()).contains("platform.payment_proof_template.manage");
        assertThat(indexNames("payment_proof_template_contributions"))
            .contains(
                "ix_payment_proof_template_contributions_review",
                "ix_payment_proof_template_contributions_tenant"
            );
    }
}

abstract class AbstractPaymentMigrationTest {
    private static final UUID PLATFORM_ADMIN_ACCOUNT_ID = UUID.fromString("30000000-0000-0000-0000-000000000955");
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();
    private static final JdbcTemplate JDBC = new JdbcTemplate(dataSource());
    private static boolean migrationsApplied;

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    protected List<String> columnNames(String tableName) {
        applyMigrationsOnce();
        return JDBC.queryForList("""
            select column_name
            from information_schema.columns
            where table_schema = 'public'
              and table_name = ?
            order by ordinal_position
            """, String.class, tableName);
    }

    protected List<String> checkConstraintDefinitions(String tableName) {
        applyMigrationsOnce();
        return JDBC.queryForList("""
            select pg_get_constraintdef(con.oid)
            from pg_constraint con
            join pg_class relation on relation.oid = con.conrelid
            join pg_namespace schema on schema.oid = relation.relnamespace
            where schema.nspname = 'public'
              and relation.relname = ?
              and con.contype = 'c'
            """, String.class, tableName);
    }

    protected List<String> permissionCodes() {
        applyMigrationsOnce();
        return JDBC.queryForList("""
            select distinct permission_code
            from auth_account_permissions
            where deleted_at is null
            """, String.class);
    }

    protected List<String> indexNames(String tableName) {
        applyMigrationsOnce();
        return JDBC.queryForList("""
            select indexname
            from pg_indexes
            where schemaname = 'public'
              and tablename = ?
            """, String.class, tableName);
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(DATABASE.jdbcUrl());
        dataSource.setUsername(DATABASE.username());
        dataSource.setPassword(DATABASE.password());
        return dataSource;
    }

    private static void applyMigrationsOnce() {
        if (!migrationsApplied) {
            DATABASE.applyMigrationsUntil("V054__paynow_payment_proof_template_library.sql");
            JDBC.update("""
                insert into auth_accounts (
                    id,
                    username,
                    display_name,
                    actor_type,
                    status,
                    password_hash,
                    password_algo
                )
                values (?, 'paynow-platform-admin', 'PayNow Platform Admin', 'platform_admin', 'active', 'test-hash', 'bcrypt-lowercase-v1')
                """, PLATFORM_ADMIN_ACCOUNT_ID);
            DATABASE.applyMigrationsAfter("V054__paynow_payment_proof_template_library.sql");
            migrationsApplied = true;
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
                LocalPostgresTestDatabase database = new LocalPostgresTestDatabase(targetDirectory, freePort());
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
            try (Stream<Path> paths = Files.list(migrationDirectory)) {
                paths
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .filter(filter)
                    .sorted()
                    .forEach(this::applyMigration);
            } catch (IOException exception) {
                throw new IllegalStateException("migration_list_failed: " + migrationDirectory, exception);
            }
        }

        private void applyMigration(Path migration) {
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
            run(command("initdb"), "-A", "trust", "-U", username(), "--encoding", "UTF8", "--locale", "C", "-D", dataDirectory.toString());
        }

        private void startServer() {
            run(
                command("pg_ctl"),
                "-D", dataDirectory.toString(),
                "-l", dataDirectory.resolve("postgres.log").toString(),
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
            return candidates.stream().filter(Files::exists).findFirst().map(Path::toString).orElse(fileName);
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
