package com.rpb.reservation.reservation.persistence.repository;

import java.time.Instant;
import java.util.UUID;

public interface ReservationResourceAssignmentProjection {
    UUID getReservationId();

    String getReservationCode();

    String getReservationStatus();

    Integer getPartySize();

    Instant getReservedStartAt();

    Instant getReservedEndAt();

    String getCustomerName();

    String getCustomerPhoneE164();

    String getResourceType();

    UUID getResourceId();

    String getResourceCode();

    UUID getQueueTicketId();

    Integer getQueueTicketNumber();

    String getQueueTicketStatus();
}
