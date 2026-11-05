package com.rpb.reservation.queuedisplay.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.queuedisplay.application.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class QueueDisplayPrivacyTest {
    @ParameterizedTest
    @CsvSource(value = {"张小明|张***", "Alice Example|A***", "+6591234567|***", "a@example.com|***", "张|***", "𠮷田太郎|𠮷***", "''|顾客", "NULL|顾客", "'   '|顾客", "顾客|顾客"}, delimiter = '|', nullValues = "NULL")
    void masksBothScreenNamesAtTheSerializedApiBoundary(String raw, String masked) throws Exception {
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var call = new QueueDisplayCurrentCall(UUID.randomUUID(), 1, "1-2", raw, 2, now, now)
            .withDisplayValues("A1", raw);
        var waiting = new QueueDisplayWaitingPreviewItem(2, "1-2", raw, 2).withDisplayValues("A2", raw);
        var result = QueueDisplayResult.success(now, "Asia/Singapore", "08:00", LocalDate.of(2030, 1, 1),
            call, 1, List.of(waiting), new QueueDisplayAds("text", 5, 3, List.of()), null);
        var response = QueueDisplayResponse.from(result);
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(response);
        assertThat(json.at("/currentCall/customerDisplayName").asText()).isEqualTo(masked);
        assertThat(json.at("/waiting/preview/0/customerDisplayName").asText()).isEqualTo(masked);
        assertThat(json.at("/currentCall/displayNumber").asText()).isEqualTo("A1");
        if (raw != null && !raw.isEmpty() && !"顾客".equals(raw)) assertThat(json.toString()).doesNotContain(raw);
        assertThat(call.customerName()).isEqualTo(raw);
    }
}
