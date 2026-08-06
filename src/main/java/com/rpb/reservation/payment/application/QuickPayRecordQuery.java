package com.rpb.reservation.payment.application;

import java.time.LocalDate;

public record QuickPayRecordQuery(
    LocalDate businessDate,
    String status,
    String terminalCode,
    String search,
    int limit
) {
}
