package com.rpb.reservation.cleaning.persistence.mapper;

import com.rpb.reservation.cleaning.domain.Cleaning;
import com.rpb.reservation.cleaning.persistence.entity.CleaningEntity;
import com.rpb.reservation.cleaning.status.CleaningStatus;
import com.rpb.reservation.cleaning.value.CleaningId;
import com.rpb.reservation.common.persistence.mapper.CleaningResourceTargetMapping;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultCleaningMapper implements CleaningMapper {

    private static final String DINING_TABLE = "dining_table";
    private static final String TABLE_GROUP = "table_group";

    @Override
    public Cleaning toDomain(CleaningEntity entity) {
        CleaningResourceTargetMapping target = toTargetMapping(entity);
        UUID resourceId = target.tableId() == null ? target.tableGroupId() : target.tableId();
        return new Cleaning(
            new CleaningId(entity.getId()),
            new StoreScope(new TenantId(entity.getTenantId()), new StoreId(entity.getStoreId())),
            new SeatingId(entity.getSeatingId()),
            target.resourceType(),
            resourceId,
            statusFromCode(entity.getStatus())
        );
    }

    @Override
    public CleaningEntity toEntity(Cleaning domain) {
        UUID tableId = null;
        UUID tableGroupId = null;
        switch (domain.resourceType()) {
            case DINING_TABLE -> tableId = domain.resourceId();
            case TABLE_GROUP -> tableGroupId = domain.resourceId();
            default -> throw new IllegalArgumentException("unknown_cleaning_resource_type");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startedAt = switch (domain.status()) {
            case CLEANING, COMPLETED, RELEASED -> now;
            case PENDING, CANCELLED -> null;
        };
        OffsetDateTime completedAt = switch (domain.status()) {
            case COMPLETED, RELEASED -> now;
            case PENDING, CLEANING, CANCELLED -> null;
        };
        OffsetDateTime releasedAt = domain.status() == CleaningStatus.RELEASED ? now : null;

        return CleaningEntity.of(
            domain.id().value(),
            domain.scope().tenantId().value(),
            domain.scope().storeId().value(),
            domain.seatingId().value(),
            domain.resourceType(),
            tableId,
            tableGroupId,
            domain.status().code(),
            startedAt,
            completedAt,
            releasedAt,
            null,
            now,
            now,
            null,
            0
        );
    }

    @Override
    public CleaningResourceTargetMapping toTargetMapping(CleaningEntity entity) {
        int targetCount = (entity.getTableId() == null ? 0 : 1) + (entity.getTableGroupId() == null ? 0 : 1);
        if (targetCount != 1) {
            throw new IllegalArgumentException("invalid_cleaning_resource_target");
        }
        if (DINING_TABLE.equals(entity.getResourceType()) && entity.getTableId() != null) {
            return new CleaningResourceTargetMapping(DINING_TABLE, entity.getTableId(), null);
        }
        if (TABLE_GROUP.equals(entity.getResourceType()) && entity.getTableGroupId() != null) {
            return new CleaningResourceTargetMapping(TABLE_GROUP, null, entity.getTableGroupId());
        }
        throw new IllegalArgumentException("invalid_cleaning_resource_target");
    }

    private static CleaningStatus statusFromCode(String code) {
        for (CleaningStatus status : CleaningStatus.values()) {
            if (status.code().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown_cleaning_status");
    }
}
