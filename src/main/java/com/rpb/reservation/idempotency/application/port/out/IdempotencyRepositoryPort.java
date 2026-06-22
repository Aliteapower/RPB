package com.rpb.reservation.idempotency.application.port.out;

import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface IdempotencyRepositoryPort {

    Optional<IdempotencyRecord> findByScopeActionKey(StoreScope scope, String source, String action, IdempotencyKey key);

    Optional<IdempotencyRecord> findByScopeActionKey(TenantScope scope, String source, String action, IdempotencyKey key);

    Optional<IdempotencyRecord> findByScopeActionKey(PlatformScope scope, String source, String action, IdempotencyKey key);

    IdempotencyRecord start(StoreScope scope, String source, String action, IdempotencyKey key, String requestHash, OffsetDateTime expiresAt);

    IdempotencyRecord complete(StoreScope scope, IdempotencyRecord record, String targetType);

    IdempotencyRecord fail(StoreScope scope, IdempotencyRecord record, String failureReason);
}
