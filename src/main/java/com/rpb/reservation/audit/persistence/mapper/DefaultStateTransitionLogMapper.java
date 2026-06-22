package com.rpb.reservation.audit.persistence.mapper;

import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.audit.persistence.entity.StateTransitionLogEntity;
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
public class DefaultStateTransitionLogMapper implements StateTransitionLogMapper {

    @Override
    public StateTransitionLog toDomain(StateTransitionLogEntity entity) {
        return new StateTransitionLog(
            entity.getId(),
            entity.getTargetType(),
            entity.getTargetId(),
            entity.getFromStatus(),
            entity.getToStatus(),
            entity.getTransitionCode(),
            entity.getActorType(),
            entity.getActorId(),
            entity.getTriggeredBy(),
            entity.getMetadata()
        );
    }

    @Override
    public StateTransitionLogEntity toEntity(StateTransitionLog domain) {
        return toEntity(null, null, domain);
    }

    public StateTransitionLogEntity toEntity(StoreScope scope, StateTransitionLog domain) {
        return toEntity(scope.tenantId().value(), scope.storeId().value(), domain);
    }

    public StateTransitionLogEntity toEntity(TenantScope scope, StateTransitionLog domain) {
        return toEntity(scope.tenantId().value(), null, domain);
    }

    public StateTransitionLogEntity toEntity(PlatformScope scope, StateTransitionLog domain) {
        return toEntity(null, null, domain);
    }

    @Override
    public TargetRef toTargetRef(StateTransitionLogEntity entity) {
        return new TargetRef(entity.getTargetType(), entity.getTargetId());
    }

    @Override
    public MetadataPayload toMetadataPayload(StateTransitionLogEntity entity) {
        return new MetadataPayload(entity.getMetadata());
    }

    @Override
    public SnapshotPayload toBeforeSnapshot(StateTransitionLogEntity entity) {
        return new SnapshotPayload(entity.getBeforeState());
    }

    @Override
    public SnapshotPayload toAfterSnapshot(StateTransitionLogEntity entity) {
        return new SnapshotPayload(entity.getAfterState());
    }

    private static StateTransitionLogEntity toEntity(UUID tenantId, UUID storeId, StateTransitionLog domain) {
        OffsetDateTime now = OffsetDateTime.now();
        return StateTransitionLogEntity.of(
            domain.id(),
            tenantId,
            storeId,
            domain.targetType(),
            domain.targetId(),
            domain.actorType(),
            domain.actorId(),
            domain.fromStatus(),
            domain.toStatus(),
            domain.transitionCode(),
            domain.triggeredBy(),
            null,
            null,
            null,
            null,
            domain.metadata(),
            now,
            null,
            now
        );
    }
}
