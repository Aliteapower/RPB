package com.rpb.reservation.appgate.application;

import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;

public record AppGateAccessRequest(
    String appKey,
    UUID tenantId,
    UUID storeId,
    String requiredPermission,
    CurrentActor actor
) {
}
