package com.rpb.reservation.tenantadmin.api;

public record TenantAdminShareProfileRequest(
    String shareDisplayName,
    String googleMapUrl,
    String shareEmail,
    String whatsappBusinessPhoneE164,
    String reservationShareNote,
    String reservationShareTemplate
) {
}
