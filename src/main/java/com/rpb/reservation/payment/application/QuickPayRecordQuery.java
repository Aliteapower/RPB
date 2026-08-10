package com.rpb.reservation.payment.application;

import java.time.LocalDate;

public record QuickPayRecordQuery(
    LocalDate businessDate,
    String status,
    String terminalCode,
    String cashierName,
    String search,
    int limit
) {
}
