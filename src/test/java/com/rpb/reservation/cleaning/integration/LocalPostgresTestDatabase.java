package com.rpb.reservation.cleaning.integration;

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

final class LocalPostgresTestDatabase implements AutoCloseable {
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
