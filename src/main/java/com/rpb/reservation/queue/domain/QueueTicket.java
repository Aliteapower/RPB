package com.rpb.reservation.queue.domain;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.queue.value.QueueTicketId;
import com.rpb.reservation.queue.value.QueueTicketNumber;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * QueueTicket domain skeleton. QueueTicket owns waiting after arrival and is
 * not Reservation or WalkIn.
 */
public record QueueTicket(
    QueueTicketId id,
    StoreScope scope,
    UUID queueGroupId,
    CustomerId customerId,
    UUID reservationId,
    UUID walkInId,
    QueueTicketNumber ticketNumber,
    PartySize partySize,
    BusinessDate businessDate,
    QueueTicketStatus status,
    Integer queuePosition,
    Instant calledAt,
    Instant expiresAt,
    Instant skippedAt,
    Instant rejoinedAt,
    String note
) {

    public QueueTicket {
        Objects.requireNonNull(id, "queue_ticket_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(queueGroupId, "queue_group_id_required");
        Objects.requireNonNull(ticketNumber, "queue_ticket_number_required");
        Objects.requireNonNull(partySize, "party_size_required");
        Objects.requireNonNull(businessDate, "business_date_required");
        Objects.requireNonNull(status, "queue_ticket_status_required");
        if (reservationId != null && walkInId != null) {
            throw new IllegalArgumentException("queue_ticket_source_conflict");
        }
        if (queuePosition != null && queuePosition <= 0) {
            throw new IllegalArgumentException("queue_position_must_be_positive");
        }
    }

    public QueueTicket(
        QueueTicketId id,
        StoreScope scope,
        UUID queueGroupId,
        CustomerId customerId,
        UUID reservationId,
        UUID walkInId,
        QueueTicketNumber ticketNumber,
        PartySize partySize,
        BusinessDate businessDate,
        QueueTicketStatus status,
        Integer queuePosition,
        Instant calledAt,
        Instant expiresAt,
        String note
    ) {
        this(
            id,
            scope,
            queueGroupId,
            customerId,
            reservationId,
            walkInId,
            ticketNumber,
            partySize,
            businessDate,
            status,
            queuePosition,
            calledAt,
            expiresAt,
            null,
            null,
            note
        );
    }

    public QueueTicket(
        QueueTicketId id,
        StoreScope scope,
        UUID queueGroupId,
        CustomerId customerId,
        UUID reservationId,
        UUID walkInId,
        QueueTicketNumber ticketNumber,
        PartySize partySize,
        BusinessDate businessDate,
        QueueTicketStatus status,
        Integer queuePosition,
        String note
    ) {
        this(
            id,
            scope,
            queueGroupId,
            customerId,
            reservationId,
            walkInId,
            ticketNumber,
            partySize,
            businessDate,
            status,
            queuePosition,
            null,
            null,
            null,
            null,
            note
        );
    }

    public QueueTicket(
        QueueTicketId id,
        StoreScope scope,
        QueueTicketNumber ticketNumber,
        PartySize partySize,
        QueueTicketStatus status
    ) {
        this(
            id,
            scope,
            UUID.randomUUID(),
            null,
            null,
            null,
            ticketNumber,
            partySize,
            new BusinessDate(java.time.LocalDate.now()),
            status,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public QueueTicket(
        QueueTicketId id,
        StoreScope scope,
        UUID queueGroupId,
        CustomerId customerId,
        UUID reservationId,
        UUID walkInId,
        QueueTicketNumber ticketNumber,
        PartySize partySize,
        BusinessDate businessDate,
        QueueTicketStatus status,
        Integer queuePosition,
        Instant calledAt,
        Instant expiresAt,
        Instant skippedAt,
        String note
    ) {
        this(
            id,
            scope,
            queueGroupId,
            customerId,
            reservationId,
            walkInId,
            ticketNumber,
            partySize,
            businessDate,
            status,
            queuePosition,
            calledAt,
            expiresAt,
            skippedAt,
            null,
            note
        );
    }

    public QueueTicket call(Instant calledAt, Instant expiresAt) {
        Objects.requireNonNull(calledAt, "queue_ticket_called_at_required");
        Objects.requireNonNull(expiresAt, "queue_ticket_expires_at_required");
        return new QueueTicket(
            id,
            scope,
            queueGroupId,
            customerId,
            reservationId,
            walkInId,
            ticketNumber,
            partySize,
            businessDate,
            QueueTicketStatus.CALLED,
            queuePosition,
            calledAt,
            expiresAt,
            skippedAt,
            rejoinedAt,
            note
        );
    }

    public QueueTicket seat() {
        return new QueueTicket(
            id,
            scope,
            queueGroupId,
            customerId,
            reservationId,
            walkInId,
            ticketNumber,
            partySize,
            businessDate,
            QueueTicketStatus.SEATED,
            queuePosition,
            calledAt,
            expiresAt,
            skippedAt,
            rejoinedAt,
            note
        );
    }

    public QueueTicket skip(Instant skippedAt) {
        Objects.requireNonNull(skippedAt, "queue_ticket_skipped_at_required");
        return new QueueTicket(
            id,
            scope,
            queueGroupId,
            customerId,
            reservationId,
            walkInId,
            ticketNumber,
            partySize,
            businessDate,
            QueueTicketStatus.SKIPPED,
            queuePosition,
            calledAt,
            expiresAt,
            skippedAt,
            rejoinedAt,
            note
        );
    }

    public QueueTicket rejoin(Instant rejoinedAt, int queuePosition) {
        Objects.requireNonNull(rejoinedAt, "queue_ticket_rejoined_at_required");
        return new QueueTicket(
            id,
            scope,
            queueGroupId,
            customerId,
            reservationId,
            walkInId,
            ticketNumber,
            partySize,
            businessDate,
            QueueTicketStatus.WAITING,
            queuePosition,
            null,
            null,
            skippedAt,
            rejoinedAt,
            note
        );
    }

    public QueueTicket cancel() {
        return new QueueTicket(
            id,
            scope,
            queueGroupId,
            customerId,
            reservationId,
            walkInId,
            ticketNumber,
            partySize,
            businessDate,
            QueueTicketStatus.CANCELLED,
            queuePosition,
            calledAt,
            expiresAt,
            skippedAt,
            rejoinedAt,
            note
        );
    }

    public String callIntent() {
        return "queue_ticket.call.intent";
    }

    public String domainBoundary() {
        return "QueueTicket is waiting after arrival, not Reservation or WalkIn.";
    }
}
