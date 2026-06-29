package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminShareProfile;
import java.util.List;

public record TenantAdminShareProfileResponse(
    boolean success,
    ShareProfileBody shareProfile
) {
    public static TenantAdminShareProfileResponse from(TenantAdminShareProfile profile) {
        return new TenantAdminShareProfileResponse(
            true,
            new ShareProfileBody(
                profile.storeDisplayName(),
                profile.shareDisplayName(),
                profile.shareAddress(),
                profile.googleMapUrl(),
                profile.shareContactPhone(),
                profile.shareEmail(),
                profile.whatsappBusinessPhoneE164(),
                profile.reservationShareNote(),
                profile.reservationShareTemplate(),
                profile.defaultReservationShareTemplate(),
                profile.availableVariables(),
                profile.usesDefaultReservationShareTemplate()
            )
        );
    }

    public record ShareProfileBody(
        String storeDisplayName,
        String shareDisplayName,
        String shareAddress,
        String googleMapUrl,
        String shareContactPhone,
        String shareEmail,
        String whatsappBusinessPhoneE164,
        String reservationShareNote,
        String reservationShareTemplate,
        String defaultReservationShareTemplate,
        List<String> availableVariables,
        boolean usesDefaultReservationShareTemplate
    ) {
        public ShareProfileBody {
            availableVariables = availableVariables == null ? List.of() : List.copyOf(availableVariables);
        }
    }
}
