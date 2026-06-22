package com.rpb.reservation.audit.persistence.mapper;

import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.persistence.entity.BusinessEventEntity;
import com.rpb.reservation.common.persistence.mapper.MetadataPayload;
import com.rpb.reservation.common.persistence.mapper.SnapshotPayload;
import com.rpb.reservation.common.persistence.mapper.TargetRef;
import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultBusinessEventMapper implements BusinessEventMapper {

    @Override
    public BusinessEvent toDomain(BusinessEventEntity entity) {
        return new BusinessEvent(
            entity.getId(),
            entity.getEventType(),
            entity.getTargetType(),
            entity.getTargetId(),
            entity.getActorType(),
            entity.getActorId(),
            entity.getSource(),
            entity.getMetadata()
        );
    }

    @Override
    public BusinessEventEntity toEntity(BusinessEvent domain) {
        return toEntity(null, null, domain);
    }

    public BusinessEventEntity toEntity(StoreScope scope, BusinessEvent domain) {
        return toEntity(scope.tenantId().value(), scope.storeId().value(), domain);
    }

    public BusinessEventEntity toEntity(TenantScope scope, BusinessEvent domain) {
        return toEntity(scope.tenantId().value(), null, domain);
    }

    public BusinessEventEntity toEntity(PlatformScope scope, BusinessEvent domain) {
        return toEntity(null, null, domain);
    }

    @Override
    public TargetRef toTargetRef(BusinessEventEntity entity) {
        return new TargetRef(entity.getTargetType(), entity.getTargetId());
    }

    @Override
    public MetadataPayload toMetadataPayload(BusinessEventEntity entity) {
        return new MetadataPayload(entity.getMetadata());
    }

    @Override
    public SnapshotPayload toBeforeSnapshot(BusinessEventEntity entity) {
        return new SnapshotPayload(entity.getBeforeState());
    }

    @Override
    public SnapshotPayload toAfterSnapshot(BusinessEventEntity entity) {
        return new SnapshotPayload(entity.getAfterState());
    }

    private static BusinessEventEntity toEntity(UUID tenantId, UUID storeId, BusinessEvent domain) {
        OffsetDateTime now = OffsetDateTime.now();
        return BusinessEventEntity.of(
            domain.id(),
            tenantId,
            storeId,
            domain.eventType(),
            domain.targetType(),
            domain.targetId(),
            domain.actorType(),
            domain.actorId(),
            domain.source(),
            null,
            null,
            null,
            null,
            domain.metadata(),
            now,
            now
        );
    }
}
