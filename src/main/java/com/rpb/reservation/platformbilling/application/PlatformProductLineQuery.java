package com.rpb.reservation.platformbilling.application;

public record PlatformProductLineQuery(
    String keyword,
    String status,
    int page,
    int size
) {
}
