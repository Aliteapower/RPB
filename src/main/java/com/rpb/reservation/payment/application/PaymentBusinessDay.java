package com.rpb.reservation.payment.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

public record PaymentBusinessDay(
    LocalDate businessDate,
    String status,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt
) {
    public PaymentBusinessDay {
        Objects.requireNonNull(businessDate, "payment_business_date_required");
        Objects.requireNonNull(status, "payment_business_day_status_required");
    }
}
