package com.rpb.reservation.platformbilling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PlatformBillingBoundaryValidationTest {
    @Test
    void businessModulesAndAppGateDoNotDependOnPlatformBilling() throws Exception {
        List<Path> forbiddenModuleFiles;
        try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java", "com", "rpb", "reservation"))) {
            forbiddenModuleFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String value = path.toString().replace('\\', '/');
                    return value.contains("/com/rpb/reservation/appgate/")
                        || value.contains("/com/rpb/reservation/reservation/")
                        || value.contains("/com/rpb/reservation/queue/")
                        || value.contains("/com/rpb/reservation/queuedisplay/")
                        || value.contains("/com/rpb/reservation/cleaning/")
                        || value.contains("/com/rpb/reservation/walkin/");
                })
                .toList();
        }

        for (Path file : forbiddenModuleFiles) {
            assertThat(Files.readString(file))
                .as(file.toString())
                .doesNotContain("com.rpb.reservation.platformbilling")
                .doesNotContain("platformbilling");
        }
    }
}
