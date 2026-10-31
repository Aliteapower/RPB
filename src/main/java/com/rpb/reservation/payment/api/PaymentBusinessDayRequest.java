package com.rpb.reservation.payment.api;

import java.time.LocalDate;

public record PaymentBusinessDayRequest(LocalDate businessDate) {
}
