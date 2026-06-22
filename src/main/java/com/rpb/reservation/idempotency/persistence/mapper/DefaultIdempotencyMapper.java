package com.rpb.reservation.idempotency.persistence.mapper;

import com.rpb.reservation.common.persistence.mapper.SnapshotPayload;
import com.rpb.reservation.common.persistence.mapper.TargetRef;
import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.persistence.entity.IdempotencyRecordEntity;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultIdempotencyMapper implements IdempotencyMapper {

    @Override
    public IdempotencyRecord toDomain(IdempotencyRecordEntity entity) {
        return new IdempotencyRecord(
            entity.getId(),
            new IdempotencyKey(entity.getIdempotencyKey()),
            entity.getSource(),
            entity.getAction(),
            entity.getRequestHash(),
            statusFromCode(entity.getStatus()),
            entity.getTargetType(),
            entity.getTargetId(),
            entity.getResponseSnapshot()
        );
    }

    @Override
    public IdempotencyRecordEntity toEntity(IdempotencyRecord domain) {
        return toEntity(null, null, domain, OffsetDateTime.now().plusHours(1));
    }

    public IdempotencyRecordEntity toEntity(StoreScope scope, IdempotencyRecord domain, OffsetDateTime expiresAt) {
        return toEntity(scope.tenantId().value(), scope.storeId().value(), domain, expiresAt);
    }

    public IdempotencyRecordEntity toEntity(TenantScope scope, IdempotencyRecord domain, OffsetDateTime expiresAt) {
        return toEntity(scope.tenantId().value(), null, domain, expiresAt);
    }

    public IdempotencyRecordEntity toEntity(PlatformScope scope, IdempotencyRecord domain, OffsetDateTime expiresAt) {
        return toEntity(null, null, domain, expiresAt);
    }

    @Override
    public TargetRef toTargetRef(IdempotencyRecordEntity entity) {
        return new TargetRef(entity.getTargetType(), entity.getTargetId());
    }

    @Override
    public SnapshotPayload toResponseSnapshot(IdempotencyRecordEntity entity) {
        return new SnapshotPayload(entity.getResponseSnapshot());
    }

    private static IdempotencyRecordEntity toEntity(
        UUID tenantId,
        UUID storeId,
        IdempotencyRecord domain,
        OffsetDateTime expiresAt
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        return IdempotencyRecordEntity.of(
            domain.id(),
            tenantId,
            storeId,
            domain.idempotencyKey().value(),
            domain.source(),
            domain.action(),
            domain.targetType(),
            domain.targetId(),
            domain.requestHash(),
            domain.responseSnapshot(),
            domain.status().code(),
            expiresAt,
            now,
            now
        );
    }

    private static IdempotencyStatus statusFromCode(String code) {
        for (IdempotencyStatus status : IdempotencyStatus.values()) {
            if (status.code().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown_idempotency_status");
    }
}
