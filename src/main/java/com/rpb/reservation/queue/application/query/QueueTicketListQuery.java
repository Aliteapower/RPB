package com.rpb.reservation.queue.application.query;

import java.util.UUID;

public record QueueTicketListQuery(
    UUID tenantId,
    UUID storeId,
    UUID actorId,
    String actorType,
    String status,
    String limit,
    String offset,
    String tableArea,
    String partySize,
    String phone,
    String businessDate
) {
    public QueueTicketListQuery(UUID tenantId,UUID storeId,UUID actorId,String actorType,String status,String limit,String offset,String tableArea,String partySize,String phone) { this(tenantId,storeId,actorId,actorType,status,limit,offset,tableArea,partySize,phone,null); }
}
