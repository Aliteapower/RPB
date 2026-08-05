package com.rpb.reservation.payment.api;

import com.rpb.reservation.payment.application.PaymentMethodProfile;
import java.util.UUID;

public record PaymentProfileResponse(
    boolean success,
    Profile profile
) {
    public static PaymentProfileResponse from(PaymentMethodProfile profile) {
        return new PaymentProfileResponse(true, Profile.from(profile));
    }

    public record Profile(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String method,
        String status,
        String paynowType,
        String paynowMobile,
        String paynowUen,
        String merchantName,
        String currency,
        String configJson,
        int version
    ) {
        static Profile from(PaymentMethodProfile profile) {
            return new Profile(
                profile.id(),
                profile.tenantId(),
                profile.storeId(),
                profile.method(),
                profile.status(),
                profile.paynowType(),
                profile.paynowMobile(),
                profile.paynowUen(),
                profile.merchantName(),
                profile.currency(),
                profile.configJson(),
                profile.version()
            );
        }
    }
}
