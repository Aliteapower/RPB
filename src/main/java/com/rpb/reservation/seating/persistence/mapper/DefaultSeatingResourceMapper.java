package com.rpb.reservation.seating.persistence.mapper;

import com.rpb.reservation.common.persistence.mapper.SeatingResourceTargetMapping;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.persistence.entity.SeatingResourceEntity;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultSeatingResourceMapper implements SeatingResourceMapper {

    private static final String DINING_TABLE = "dining_table";
    private static final String TABLE_GROUP = "table_group";

    @Override
    public SeatingResource toDomain(SeatingResourceEntity entity) {
        SeatingResourceTargetMapping target = toTargetMapping(entity);
        UUID resourceId = target.tableId() == null ? target.tableGroupId() : target.tableId();
        return new SeatingResource(
            entity.getId(),
            new StoreScope(new TenantId(entity.getTenantId()), new StoreId(entity.getStoreId())),
            new SeatingId(entity.getSeatingId()),
            target.resourceType(),
            resourceId,
            entity.getStatus()
        );
    }

    @Override
    public SeatingResourceEntity toEntity(SeatingResource domain) {
        UUID tableId = null;
        UUID tableGroupId = null;
        switch (domain.resourceType()) {
            case DINING_TABLE -> tableId = domain.resourceId();
            case TABLE_GROUP -> tableGroupId = domain.resourceId();
            default -> throw new IllegalArgumentException("unknown_seating_resource_type");
        }
        OffsetDateTime now = OffsetDateTime.now();
        return SeatingResourceEntity.of(
            domain.id(),
            domain.scope().tenantId().value(),
            domain.scope().storeId().value(),
            domain.seatingId().value(),
            domain.resourceType(),
            tableId,
            tableGroupId,
            now,
            null,
            domain.status(),
            now,
            now,
            null
        );
    }

    @Override
    public SeatingResourceTargetMapping toTargetMapping(SeatingResourceEntity entity) {
        int targetCount = (entity.getTableId() == null ? 0 : 1) + (entity.getTableGroupId() == null ? 0 : 1);
        if (targetCount != 1) {
            throw new IllegalArgumentException("invalid_seating_resource_target");
        }
        if (entity.getTableId() != null) {
            return new SeatingResourceTargetMapping(DINING_TABLE, entity.getTableId(), null);
        }
        return new SeatingResourceTargetMapping(TABLE_GROUP, null, entity.getTableGroupId());
    }
}
