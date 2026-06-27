package com.rpb.reservation.tenantadmin.application;

public record TenantAdminShareProfileCommand(
    String shareDisplayName,
    String shareAddress,
    String googleMapUrl,
    String shareContactPhone,
    String reservationShareNote,
    String reservationShareTemplate
) {
}
