package com.rpb.reservation.queue.persistence.repository;

import java.time.Instant;
import java.util.UUID;

public interface QueueTicketListProjection {
    UUID getQueueTicketId();
    Integer getQueueTicketNumber();
    String getQueueTicketStatus();
    Integer getPartySize();
    String getPartySizeGroup();
    UUID getReservationId();
    String getReservationCode();
    String getReservationStatus();
    String getCustomerName();
    String getCustomerPhoneE164();
    Instant getCreatedAt();
    Instant getCalledAt();
    Instant getExpiresAt();
}
