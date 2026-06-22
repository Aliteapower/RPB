package com.rpb.reservation.table.persistence.mapper;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.table.domain.TableLock;
import com.rpb.reservation.table.persistence.entity.TableLockEntity;
import com.rpb.reservation.table.status.TableLockStatus;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class DefaultTableLockMapper implements TableLockMapper {

    @Override
    public TableLock toDomain(TableLockEntity entity) {
        return new TableLock(
            entity.getId(),
            new StoreScope(new TenantId(entity.getTenantId()), new StoreId(entity.getStoreId())),
            entity.getResourceType(),
            entity.getResourceId(),
            entity.getLockKey(),
            entity.getLockOwner(),
            entity.getSourceType(),
            entity.getSourceId(),
            entity.getLockedAt().toInstant(),
            entity.getLockedUntilAt().toInstant(),
            entity.getIdempotencyKey() == null ? null : new IdempotencyKey(entity.getIdempotencyKey()),
            statusFromCode(entity.getStatus())
        );
    }

    @Override
    public TableLockEntity toEntity(TableLock domain) {
        OffsetDateTime now = OffsetDateTime.now();
        return TableLockEntity.of(
            domain.id(),
            domain.scope().tenantId().value(),
            domain.scope().storeId().value(),
            domain.resourceType(),
            domain.resourceId(),
            domain.lockKey(),
            domain.lockOwner(),
            OffsetDateTime.ofInstant(domain.lockedUntilAt(), ZoneOffset.UTC),
            domain.sourceType(),
            domain.sourceId(),
            domain.idempotencyKey() == null ? null : domain.idempotencyKey().value(),
            domain.status().code(),
            OffsetDateTime.ofInstant(domain.lockedAt(), ZoneOffset.UTC),
            domain.status() == TableLockStatus.RELEASED ? now : null,
            now,
            now,
            0
        );
    }

    private static TableLockStatus statusFromCode(String code) {
        for (TableLockStatus status : TableLockStatus.values()) {
            if (status.code().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown_table_lock_status");
    }
}
