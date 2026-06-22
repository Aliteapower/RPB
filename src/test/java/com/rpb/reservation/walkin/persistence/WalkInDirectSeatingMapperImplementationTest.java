package com.rpb.reservation.walkin.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.persistence.entity.BusinessEventEntity;
import com.rpb.reservation.audit.persistence.mapper.DefaultBusinessEventMapper;
import com.rpb.reservation.common.persistence.mapper.SeatingResourceTargetMapping;
import com.rpb.reservation.common.persistence.mapper.SeatingSourceMapping;
import com.rpb.reservation.common.persistence.mapper.SnapshotPayload;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.persistence.entity.IdempotencyRecordEntity;
import com.rpb.reservation.idempotency.persistence.mapper.DefaultIdempotencyMapper;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.persistence.entity.SeatingEntity;
import com.rpb.reservation.seating.persistence.entity.SeatingResourceEntity;
import com.rpb.reservation.seating.persistence.mapper.DefaultSeatingMapper;
import com.rpb.reservation.seating.persistence.mapper.DefaultSeatingResourceMapper;
import com.rpb.reservation.seating.status.SeatingStatus;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.value.WalkInId;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalkInDirectSeatingMapperImplementationTest {

    private final TenantId tenantId = new TenantId(UUID.randomUUID());
    private final StoreId storeId = new StoreId(UUID.randomUUID());
    private final StoreScope scope = new StoreScope(tenantId, storeId);

    @Test
    void seatingMapperPreservesWalkInSourceXorBoundary() {
        UUID walkInId = UUID.randomUUID();
        Seating seating = new Seating(
            new SeatingId(UUID.randomUUID()),
            scope,
            "walk_in",
            walkInId,
            "S-1001",
            new PartySize(4),
            SeatingStatus.OCCUPIED
        );

        DefaultSeatingMapper mapper = new DefaultSeatingMapper();
        SeatingEntity entity = mapper.toEntity(seating);

        assertThat(entity.getWalkInId()).isEqualTo(walkInId);
        assertThat(entity.getReservationId()).isNull();
        assertThat(entity.getQueueTicketId()).isNull();

        SeatingSourceMapping source = mapper.toSourceMapping(entity);
        assertThat(source.sourceType()).isEqualTo("walk_in");
        assertThat(source.sourceId()).isEqualTo(walkInId);
        assertThat(mapper.toDomain(entity).sourceId()).isEqualTo(walkInId);
    }

    @Test
    void seatingMapperRejectsInvalidSourceXorShape() {
        SeatingEntity invalid = SeatingEntity.of(
            UUID.randomUUID(),
            tenantId.value(),
            storeId.value(),
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            "S-1002",
            2,
            "occupied",
            OffsetDateTime.now(),
            null,
            null,
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            null,
            0
        );

        assertThatThrownBy(() -> new DefaultSeatingMapper().toSourceMapping(invalid))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid_seating_source");
    }

    @Test
    void seatingResourceMapperPreservesTableTargetXorBoundary() {
        UUID tableId = UUID.randomUUID();
        SeatingResource resource = new SeatingResource(
            UUID.randomUUID(),
            scope,
            new SeatingId(UUID.randomUUID()),
            "dining_table",
            tableId,
            "active"
        );

        DefaultSeatingResourceMapper mapper = new DefaultSeatingResourceMapper();
        SeatingResourceEntity entity = mapper.toEntity(resource);

        assertThat(entity.getTableId()).isEqualTo(tableId);
        assertThat(entity.getTableGroupId()).isNull();

        SeatingResourceTargetMapping target = mapper.toTargetMapping(entity);
        assertThat(target.resourceType()).isEqualTo("dining_table");
        assertThat(target.tableId()).isEqualTo(tableId);
        assertThat(target.tableGroupId()).isNull();
    }

    @Test
    void idempotencyMapperPreservesRequestHashStatusAndResponseSnapshot() {
        UUID recordId = UUID.randomUUID();
        IdempotencyRecord record = new IdempotencyRecord(
            recordId,
            new IdempotencyKey("idem-1001"),
            "staff",
            "seat_walk_in_directly",
            "hash-abc",
            IdempotencyStatus.COMPLETED,
            "seating",
            UUID.randomUUID(),
            "{\"seatingId\":\"S-1\"}"
        );

        DefaultIdempotencyMapper mapper = new DefaultIdempotencyMapper();
        IdempotencyRecordEntity entity = mapper.toEntity(scope, record, OffsetDateTime.now().plusMinutes(30));

        assertThat(entity.getTenantId()).isEqualTo(tenantId.value());
        assertThat(entity.getStoreId()).isEqualTo(storeId.value());
        assertThat(entity.getRequestHash()).isEqualTo("hash-abc");
        assertThat(entity.getStatus()).isEqualTo("completed");

        IdempotencyRecord roundTrip = mapper.toDomain(entity);
        assertThat(roundTrip.requestHash()).isEqualTo("hash-abc");
        assertThat(roundTrip.status()).isEqualTo(IdempotencyStatus.COMPLETED);

        SnapshotPayload snapshot = mapper.toResponseSnapshot(entity);
        assertThat(snapshot.json()).contains("seatingId");
    }

    @Test
    void businessEventMapperKeepsGenericTargetAndMetadataSeparate() {
        UUID targetId = UUID.randomUUID();
        BusinessEvent event = new BusinessEvent(
            UUID.randomUUID(),
            "walk_in.created",
            "walk_in",
            targetId,
            "staff",
            UUID.randomUUID(),
            "staff",
            "{\"partySize\":4}"
        );

        DefaultBusinessEventMapper mapper = new DefaultBusinessEventMapper();
        BusinessEventEntity entity = mapper.toEntity(scope, event);

        assertThat(entity.getTenantId()).isEqualTo(tenantId.value());
        assertThat(entity.getStoreId()).isEqualTo(storeId.value());
        assertThat(mapper.toTargetRef(entity).targetType()).isEqualTo("walk_in");
        assertThat(mapper.toTargetRef(entity).targetId()).isEqualTo(targetId);
        assertThat(mapper.toMetadataPayload(entity).json()).contains("partySize");
    }
}
