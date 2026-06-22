package com.rpb.reservation.queue.application.query;

import java.util.UUID;

public record QueueTicketListQuery(
    UUID tenantId,
    UUID storeId,
    UUID actorId,
    String actorType,
    String status,
    String limit,
    String offset
) {
}
