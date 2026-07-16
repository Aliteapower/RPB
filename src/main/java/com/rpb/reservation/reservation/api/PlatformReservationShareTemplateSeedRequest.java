package com.rpb.reservation.reservation.api;

public record PlatformReservationShareTemplateSeedRequest(
    String displayName,
    String locale,
    String templateText,
    String status,
    Integer version
) {
}
