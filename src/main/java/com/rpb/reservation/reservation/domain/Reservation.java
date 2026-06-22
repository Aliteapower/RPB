package com.rpb.reservation.reservation.domain;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationCode;
import com.rpb.reservation.reservation.value.ReservationId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reservation domain skeleton. Reservation owns future capacity intent and is
 * not QueueTicket or Seating.
 */
public record Reservation(
    ReservationId id,
    StoreScope scope,
    CustomerId customerId,
    ReservationCode reservationCode,
    PartySize partySize,
    BusinessDate businessDate,
    Instant reservedStartAt,
    Instant reservedEndAt,
    Instant holdUntilAt,
    ReservationStatus status,
    String sourceChannel,
    String cancellationReasonCode,
    String noShowReasonCode,
    String note,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {

    public Reservation {
        Objects.requireNonNull(id, "reservation_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(reservationCode, "reservation_code_required");
        Objects.requireNonNull(partySize, "party_size_required");
        Objects.requireNonNull(status, "reservation_status_required");
        if (sourceChannel == null || sourceChannel.isBlank()) {
            sourceChannel = "staff";
        }
    }

    public Reservation(
        ReservationId id,
        StoreScope scope,
        ReservationCode reservationCode,
        PartySize partySize,
        ReservationStatus status
    ) {
        this(
            id,
            scope,
            null,
            reservationCode,
            partySize,
            null,
            null,
            null,
            null,
            status,
            "staff",
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public static Reservation skeleton(StoreScope scope, ReservationCode code, PartySize partySize, ReservationStatus status) {
        return new Reservation(new ReservationId(UUID.randomUUID()), scope, code, partySize, status);
    }

    public String confirmIntent() {
        return "reservation.confirm.intent";
    }

    public String domainBoundary() {
        return "Reservation is future capacity intent, not QueueTicket or Seating.";
    }
}
