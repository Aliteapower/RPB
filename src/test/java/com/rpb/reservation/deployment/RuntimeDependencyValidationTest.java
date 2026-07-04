package com.rpb.reservation.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RuntimeDependencyValidationTest {

    @Test
    void flywayIncludesPostgresqlDatabaseSupportForPublicRuntime() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom)
            .contains("<artifactId>flyway-core</artifactId>")
            .contains("<artifactId>flyway-database-postgresql</artifactId>");
        assertThat(Class.forName("org.flywaydb.database.postgresql.PostgreSQLDatabaseType"))
            .isNotNull();
    }
}
