package com.rpb.reservation.payment.api;

import com.rpb.reservation.payment.application.PaymentBusinessDay;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PaymentBusinessDayResponse(
    boolean success,
    LocalDate businessDate,
    String status,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt
) {
    public static PaymentBusinessDayResponse from(PaymentBusinessDay businessDay) {
        return new PaymentBusinessDayResponse(
            true,
            businessDay.businessDate(),
            businessDay.status(),
            businessDay.openedAt(),
            businessDay.closedAt()
        );
    }
}
