package com.rpb.reservation.staffhome.application;

import java.util.UUID;

public record StaffHomeOverviewQuery(
    UUID tenantId,
    UUID storeId,
    UUID actorId,
    String actorType,
    String businessDate
) {
}
