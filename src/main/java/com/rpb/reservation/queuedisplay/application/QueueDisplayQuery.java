package com.rpb.reservation.queuedisplay.application;

import java.util.UUID;

public record QueueDisplayQuery(UUID tenantId, UUID storeId, UUID actorId, String actorType, String locale) {
    public QueueDisplayQuery(UUID tenantId, UUID storeId, UUID actorId, String actorType) {
        this(tenantId, storeId, actorId, actorType, null);
    }
}
