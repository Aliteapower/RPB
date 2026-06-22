package com.rpb.reservation.table.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.table.domain.TableLock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TableLockRepositoryPort {

    Optional<TableLock> findActiveByResource(StoreScope scope, String resourceType, UUID resourceId);

    boolean existsActiveConflict(StoreScope scope, String resourceType, UUID resourceId, OffsetDateTime at);

    TableLock save(StoreScope scope, TableLock lock);

    TableLock release(StoreScope scope, UUID tableLockId, OffsetDateTime releasedAt);
}
