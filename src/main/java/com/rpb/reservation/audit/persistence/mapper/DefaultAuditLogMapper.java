package com.rpb.reservation.audit.persistence.mapper;

import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.persistence.entity.AuditLogEntity;
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
public class DefaultAuditLogMapper implements AuditLogMapper {

    @Override
    public AuditLog toDomain(AuditLogEntity entity) {
        return new AuditLog(
            entity.getId(),
            entity.getOperationCode(),
            entity.getTargetType(),
            entity.getTargetId(),
            entity.getSource(),
            entity.getActorType(),
            entity.getActorId(),
            entity.getMetadata()
        );
    }

    @Override
    public AuditLogEntity toEntity(AuditLog domain) {
        return toEntity(null, null, domain);
    }

    public AuditLogEntity toEntity(StoreScope scope, AuditLog domain) {
        return toEntity(scope.tenantId().value(), scope.storeId().value(), domain);
    }

    public AuditLogEntity toEntity(TenantScope scope, AuditLog domain) {
        return toEntity(scope.tenantId().value(), null, domain);
    }

    public AuditLogEntity toEntity(PlatformScope scope, AuditLog domain) {
        return toEntity(null, null, domain);
    }

    @Override
    public TargetRef toTargetRef(AuditLogEntity entity) {
        return new TargetRef(entity.getTargetType(), entity.getTargetId());
    }

    @Override
    public MetadataPayload toMetadataPayload(AuditLogEntity entity) {
        return new MetadataPayload(entity.getMetadata());
    }

    @Override
    public SnapshotPayload toBeforeSnapshot(AuditLogEntity entity) {
        return new SnapshotPayload(entity.getBeforeState());
    }

    @Override
    public SnapshotPayload toAfterSnapshot(AuditLogEntity entity) {
        return new SnapshotPayload(entity.getAfterState());
    }

    private static AuditLogEntity toEntity(UUID tenantId, UUID storeId, AuditLog domain) {
        OffsetDateTime now = OffsetDateTime.now();
        return AuditLogEntity.of(
            domain.id(),
            tenantId,
            storeId,
            domain.operationCode(),
            domain.targetType(),
            domain.targetId(),
            domain.actorType(),
            domain.actorId(),
            null,
            domain.source(),
            null,
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
