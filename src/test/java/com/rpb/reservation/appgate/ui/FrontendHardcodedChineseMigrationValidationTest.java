package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrontendHardcodedChineseMigrationValidationTest {

    @Test
    void frontendSourceFilesDoNotContainHardcodedChineseOutsideLocaleDictionaries() throws Exception {
        List<Path> files = Files.walk(Path.of("src"))
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".vue") || path.toString().endsWith(".ts"))
            .filter(path -> !path.toString().replace('\\', '/').startsWith("src/i18n/locales/"))
            .filter(path -> !path.toString().replace('\\', '/').startsWith("src/test/"))
            .toList();

        StringBuilder offenders = new StringBuilder();
        for (Path file : files) {
            String source = Files.readString(file);
            if (source.matches("(?s).*\\p{IsHan}.*")) {
                offenders.append(file).append('\n');
            }
        }

        assertThat(offenders.toString())
            .as("Frontend UI copy should live in src/i18n/locales dictionaries")
            .isBlank();
    }
}
