package com.rpb.reservation.queue.persistence.mapper;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.persistence.entity.QueueTicketEntity;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.queue.value.QueueTicketId;
import com.rpb.reservation.queue.value.QueueTicketNumber;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class DefaultQueueTicketMapper implements QueueTicketMapper {

    @Override
    public QueueTicket toDomain(QueueTicketEntity entity) {
        return new QueueTicket(
            new QueueTicketId(entity.getId()),
            new StoreScope(new TenantId(entity.getTenantId()), new StoreId(entity.getStoreId())),
            entity.getQueueGroupId(),
            entity.getCustomerId() == null ? null : new CustomerId(entity.getCustomerId()),
            entity.getReservationId(),
            entity.getWalkInId(),
            new QueueTicketNumber(entity.getTicketNumber()),
            new PartySize(entity.getPartySize()),
            new BusinessDate(entity.getBusinessDate()),
            statusFromCode(entity.getStatus()),
            entity.getQueuePosition(),
            toInstant(entity.getCalledAt()),
            toInstant(entity.getExpiresAt()),
            toInstant(entity.getSkippedAt()),
            entity.getNote()
        );
    }

    @Override
    public QueueTicketEntity toEntity(QueueTicket domain) {
        OffsetDateTime now = OffsetDateTime.now();
        return QueueTicketEntity.of(
            domain.id().value(),
            domain.scope().tenantId().value(),
            domain.scope().storeId().value(),
            domain.queueGroupId(),
            domain.customerId() == null ? null : domain.customerId().value(),
            domain.reservationId(),
            domain.walkInId(),
            domain.ticketNumber().value(),
            domain.partySize().value(),
            domain.businessDate().value(),
            domain.status().code(),
            domain.queuePosition(),
            toOffsetDateTime(domain.calledAt()),
            toOffsetDateTime(domain.skippedAt()),
            null,
            toOffsetDateTime(domain.expiresAt()),
            null,
            domain.note(),
            now,
            now,
            null,
            0
        );
    }

    private static QueueTicketStatus statusFromCode(String code) {
        for (QueueTicketStatus status : QueueTicketStatus.values()) {
            if (status.code().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown_queue_ticket_status");
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
