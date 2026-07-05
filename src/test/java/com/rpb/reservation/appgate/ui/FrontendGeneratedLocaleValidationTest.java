package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FrontendGeneratedLocaleValidationTest {

    @Test
    void englishGeneratedLocaleDoesNotContainChinesePageCopy() throws Exception {
        String source = Files.readString(Path.of("src", "i18n", "locales", "generated-en-SG.ts"));

        assertThat(source)
            .as("Generated en-SG page copy should be translated instead of copied from zh-CN")
            .doesNotMatch("(?s).*\\p{IsHan}.*");
    }
}
