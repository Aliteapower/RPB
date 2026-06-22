package com.rpb.reservation.appgate.domain;

import java.util.Map;
import java.util.UUID;

public record AppGateDecision(
    boolean allowed,
    String appKey,
    UUID tenantId,
    UUID storeId,
    String requiredPermission,
    AppGateDenyReason denyReason,
    String messageKey,
    Map<String, Object> details
) {
    public AppGateDecision {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static AppGateDecision allow(String appKey, UUID tenantId, UUID storeId, String requiredPermission) {
        return new AppGateDecision(true, appKey, tenantId, storeId, requiredPermission, null, null, Map.of());
    }

    public static AppGateDecision deny(
        String appKey,
        UUID tenantId,
        UUID storeId,
        String requiredPermission,
        AppGateDenyReason reason
    ) {
        return new AppGateDecision(false, appKey, tenantId, storeId, requiredPermission, reason, reason.messageKey(), Map.of());
    }
}
