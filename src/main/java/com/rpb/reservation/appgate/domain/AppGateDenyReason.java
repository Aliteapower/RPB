package com.rpb.reservation.appgate.domain;

import java.util.Locale;

public enum AppGateDenyReason {
    APP_DISABLED,
    TENANT_APP_NOT_ENABLED,
    TENANT_APP_EXPIRED,
    STORE_APP_NOT_ENABLED,
    STORE_ACCESS_DENIED,
    PERMISSION_DENIED;

    public String messageKey() {
        return "appgate." + name().toLowerCase(Locale.ROOT);
    }
}
