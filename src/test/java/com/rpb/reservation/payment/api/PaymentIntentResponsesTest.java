package com.rpb.reservation.payment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.payment.application.QuickPayRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentIntentResponsesTest {
    private static final UUID INTENT_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Test
    void summarizesQuickPayRecordsByOperationalPaymentStatus() {
        PaymentIntentResponses.QuickPayRecordsResponse response = PaymentIntentResponses.QuickPayRecordsResponse.from(List.of(
            record("pending", "5.00"),
            record("awaiting_verification", "8.00"),
            record("paid", "12.00")
        ));

        assertThat(response.summary().count()).isEqualTo(3);
        assertThat(response.summary().pendingCount()).isEqualTo(1);
        assertThat(response.summary().awaitingVerificationCount()).isEqualTo(1);
        assertThat(response.summary().paidCount()).isEqualTo(1);
        assertThat(response.summary().totalAmount()).isEqualTo("25");
        assertThat(response.summary().pendingAmount()).isEqualTo("5");
        assertThat(response.summary().awaitingVerificationAmount()).isEqualTo("8");
        assertThat(response.summary().paidAmount()).isEqualTo("12");
        assertThat(response.summary().currency()).isEqualTo("SGD");
    }

    private static QuickPayRecord record(String status, String amount) {
        return new QuickPayRecord(
            INTENT_ID,
            SESSION_ID,
            "PIT-202608-0001",
            "PRS-ABCDEF1234567890",
            7,
            LocalDate.parse("2026-08-10"),
            new BigDecimal(amount),
            "SGD",
            "QP202608100007ACDE",
            status,
            status,
            "T1",
            "Alice",
            OffsetDateTime.parse("2026-08-10T04:10:00Z"),
            OffsetDateTime.parse("2026-08-10T04:12:00Z")
        );
    }
}
