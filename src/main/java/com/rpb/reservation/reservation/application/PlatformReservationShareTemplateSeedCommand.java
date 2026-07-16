package com.rpb.reservation.reservation.application;

public record PlatformReservationShareTemplateSeedCommand(
    String displayName,
    String locale,
    String templateText,
    String status,
    Integer version
) {
}
