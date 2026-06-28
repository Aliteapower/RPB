package com.rpb.reservation.tenantadmin.api;

public record TenantAdminShareProfileRequest(
    String shareDisplayName,
    String googleMapUrl,
    String reservationShareNote,
    String reservationShareTemplate
) {
}
