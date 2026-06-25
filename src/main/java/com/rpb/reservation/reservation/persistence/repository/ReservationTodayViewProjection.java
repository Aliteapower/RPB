package com.rpb.reservation.reservation.persistence.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface ReservationTodayViewProjection {
    UUID getReservationId();

    String getReservationCode();

    String getStatus();

    Integer getPartySize();

    OffsetDateTime getReservedStartAt();

    OffsetDateTime getReservedEndAt();

    OffsetDateTime getHoldUntilAt();

    LocalDate getBusinessDate();

    String getCustomerName();

    String getCustomerNickname();

    String getPhoneE164();

    String getNote();

    UUID getSeatingId();

    String getCurrentResourceType();

    UUID getCurrentResourceId();

    String getCurrentResourceCode();

    String getAssignedResourceType();

    UUID getAssignedResourceId();

    String getAssignedResourceCode();

    UUID getQueueTicketId();

    Integer getQueueTicketNumber();

    String getQueueTicketGroupCode();

    String getQueueTicketStatus();
}
