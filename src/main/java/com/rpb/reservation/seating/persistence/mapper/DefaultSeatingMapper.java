package com.rpb.reservation.seating.persistence.mapper;

import com.rpb.reservation.common.persistence.mapper.SeatingSourceMapping;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.persistence.entity.SeatingEntity;
import com.rpb.reservation.seating.status.SeatingStatus;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultSeatingMapper implements SeatingMapper {

    private static final String RESERVATION = "reservation";
    private static final String QUEUE_TICKET = "queue_ticket";
    private static final String WALK_IN = "walk_in";

    @Override
    public Seating toDomain(SeatingEntity entity) {
        SeatingSourceMapping source = toSourceMapping(entity);
        return new Seating(
            new SeatingId(entity.getId()),
            new StoreScope(new TenantId(entity.getTenantId()), new StoreId(entity.getStoreId())),
            source.sourceType(),
            source.sourceId(),
            entity.getSeatingCode(),
            entity.getManualOverrideReasonCode(),
            entity.getNote(),
            new PartySize(entity.getPartySizeSnapshot()),
            statusFromCode(entity.getStatus())
        );
    }

    @Override
    public SeatingEntity toEntity(Seating domain) {
        UUID reservationId = null;
        UUID queueTicketId = null;
        UUID walkInId = null;
        if (domain.sourceId() == null) {
            throw new IllegalArgumentException("seating_source_id_required");
        }
        switch (domain.sourceType()) {
            case RESERVATION -> reservationId = domain.sourceId();
            case QUEUE_TICKET -> queueTicketId = domain.sourceId();
            case WALK_IN -> walkInId = domain.sourceId();
            default -> throw new IllegalArgumentException("unknown_seating_source_type");
        }
        OffsetDateTime now = OffsetDateTime.now();
        return SeatingEntity.of(
            domain.id().value(),
            domain.scope().tenantId().value(),
            domain.scope().storeId().value(),
            reservationId,
            queueTicketId,
            walkInId,
            domain.seatingCode() == null ? "S-" + domain.id().value() : domain.seatingCode(),
            domain.partySizeSnapshot().value(),
            domain.status().code(),
            now,
            null,
            domain.manualOverrideReasonCode(),
            domain.note(),
            now,
            now,
            null,
            0
        );
    }

    @Override
    public SeatingSourceMapping toSourceMapping(SeatingEntity entity) {
        int sourceCount = count(entity.getReservationId(), entity.getQueueTicketId(), entity.getWalkInId());
        if (sourceCount != 1) {
            throw new IllegalArgumentException("invalid_seating_source");
        }
        if (entity.getReservationId() != null) {
            return new SeatingSourceMapping(RESERVATION, entity.getReservationId());
        }
        if (entity.getQueueTicketId() != null) {
            return new SeatingSourceMapping(QUEUE_TICKET, entity.getQueueTicketId());
        }
        return new SeatingSourceMapping(WALK_IN, entity.getWalkInId());
    }

    private static int count(UUID first, UUID second, UUID third) {
        return (first == null ? 0 : 1) + (second == null ? 0 : 1) + (third == null ? 0 : 1);
    }

    private static SeatingStatus statusFromCode(String code) {
        for (SeatingStatus status : SeatingStatus.values()) {
            if (status.code().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown_seating_status");
    }
}
