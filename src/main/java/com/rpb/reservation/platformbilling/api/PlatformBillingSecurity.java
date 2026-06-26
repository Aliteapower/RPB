package com.rpb.reservation.platformbilling.api;

import com.rpb.reservation.platformbilling.application.PlatformBillingOperator;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;

final class PlatformBillingSecurity {
    private static final String PLATFORM_ADMIN = "platform_admin";
    private static final String TENANT_MANAGE_PERMISSION = "platform.tenant.manage";

    private PlatformBillingSecurity() {
    }

    static CurrentActor requirePlatformAdmin(CurrentActorProvider provider, String dedicatedPermission) {
        CurrentActor actor = provider.currentActor()
            .orElseThrow(() -> new PlatformBillingApiException(PlatformBillingApiErrorCode.UNAUTHENTICATED));
        boolean hasPlatformAdminRole = actor.roles().contains(PLATFORM_ADMIN);
        boolean hasPermission = actor.hasPermission(dedicatedPermission) || actor.hasPermission(TENANT_MANAGE_PERMISSION);
        if (!hasPlatformAdminRole || !hasPermission) {
            throw new PlatformBillingApiException(PlatformBillingApiErrorCode.FORBIDDEN);
        }
        return actor;
    }

    static PlatformBillingOperator operator(CurrentActor actor) {
        return new PlatformBillingOperator(actor.actorId(), actor.actorType());
    }
}
