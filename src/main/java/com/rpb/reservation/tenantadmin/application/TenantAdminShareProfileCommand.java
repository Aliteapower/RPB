package com.rpb.reservation.tenantadmin.application;

public record TenantAdminShareProfileCommand(
    String shareDisplayName,
    String googleMapUrl,
    String reservationShareNote,
    String reservationShareTemplate
) {
}
