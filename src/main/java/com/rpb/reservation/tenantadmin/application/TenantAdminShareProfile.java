package com.rpb.reservation.tenantadmin.application;

import java.util.List;

public record TenantAdminShareProfile(
    String storeDisplayName,
    String shareDisplayName,
    String shareAddress,
    String googleMapUrl,
    String shareContactPhone,
    String whatsappBusinessPhoneE164,
    String reservationShareNote,
    String reservationShareTemplate,
    String defaultReservationShareTemplate,
    List<String> availableVariables,
    boolean usesDefaultReservationShareTemplate
) {
    public TenantAdminShareProfile {
        availableVariables = availableVariables == null ? List.of() : List.copyOf(availableVariables);
    }
}
