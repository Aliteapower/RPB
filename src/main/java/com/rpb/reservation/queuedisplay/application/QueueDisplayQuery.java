package com.rpb.reservation.queuedisplay.application;

import java.util.UUID;

public record QueueDisplayQuery(UUID tenantId, UUID storeId, UUID actorId, String actorType) {
}
