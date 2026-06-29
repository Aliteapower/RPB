package com.rpb.reservation.tenantadmin.application;

public record TenantAdminShareProfileUpdate(
    String shareDisplayName,
    String googleMapUrl,
    String whatsappBusinessPhoneE164,
    String reservationShareNote,
    String reservationShareTemplate
) {
}
