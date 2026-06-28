package com.rpb.reservation.tenantadmin.application;

public record TenantAdminShareProfileUpdate(
    String shareDisplayName,
    String googleMapUrl,
    String reservationShareNote,
    String reservationShareTemplate
) {
}
