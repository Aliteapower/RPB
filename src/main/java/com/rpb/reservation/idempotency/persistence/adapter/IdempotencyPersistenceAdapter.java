package com.rpb.reservation.idempotency.persistence.adapter;

import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.persistence.mapper.DefaultIdempotencyMapper;
import com.rpb.reservation.idempotency.persistence.repository.IdempotencyRecordJpaRepository;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyPersistenceAdapter implements IdempotencyRepositoryPort {

    private final IdempotencyRecordJpaRepository repository;
    private final DefaultIdempotencyMapper mapper;

    public IdempotencyPersistenceAdapter(IdempotencyRecordJpaRepository repository, DefaultIdempotencyMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<IdempotencyRecord> findByScopeActionKey(
        StoreScope scope,
        String source,
        String action,
        IdempotencyKey key
    ) {
        return repository.findByTenantIdAndStoreIdAndSourceAndActionAndIdempotencyKey(
            scope.tenantId().value(),
            scope.storeId().value(),
            source,
            action,
            key.value()
        ).map(mapper::toDomain);
    }

    @Override
    public Optional<IdempotencyRecord> findByScopeActionKey(
        TenantScope scope,
        String source,
        String action,
        IdempotencyKey key
    ) {
        return repository.findByTenantIdAndStoreIdIsNullAndSourceAndActionAndIdempotencyKey(
            scope.tenantId().value(),
            source,
            action,
            key.value()
        ).map(mapper::toDomain);
    }

    @Override
    public Optional<IdempotencyRecord> findByScopeActionKey(
        PlatformScope scope,
        String source,
        String action,
        IdempotencyKey key
    ) {
        return repository.findByTenantIdIsNullAndStoreIdIsNullAndSourceAndActionAndIdempotencyKey(
            source,
            action,
            key.value()
        ).map(mapper::toDomain);
    }

    @Override
    public IdempotencyRecord start(
        StoreScope scope,
        String source,
        String action,
        IdempotencyKey key,
        String requestHash,
        OffsetDateTime expiresAt
    ) {
        IdempotencyRecord record = new IdempotencyRecord(
            UUID.randomUUID(),
            key,
            source,
            action,
            requestHash,
            IdempotencyStatus.STARTED,
            null,
            null,
            null
        );
        return mapper.toDomain(repository.save(mapper.toEntity(scope, record, expiresAt)));
    }

    @Override
    public IdempotencyRecord complete(StoreScope scope, IdempotencyRecord record, String targetType) {
        IdempotencyRecord completed = new IdempotencyRecord(
            record.id(),
            record.idempotencyKey(),
            record.source(),
            record.action(),
            record.requestHash(),
            IdempotencyStatus.COMPLETED,
            targetType,
            record.targetId(),
            record.responseSnapshot()
        );
        return mapper.toDomain(repository.save(mapper.toEntity(scope, completed, OffsetDateTime.now().plusHours(1))));
    }

    @Override
    public IdempotencyRecord fail(StoreScope scope, IdempotencyRecord record, String failureReason) {
        IdempotencyRecord failed = new IdempotencyRecord(
            record.id(),
            record.idempotencyKey(),
            record.source(),
            record.action(),
            record.requestHash(),
            IdempotencyStatus.FAILED,
            record.targetType(),
            record.targetId(),
            "{\"failure_reason\":\"" + escapeJson(failureReason) + "\"}"
        );
        return mapper.toDomain(repository.save(mapper.toEntity(scope, failed, OffsetDateTime.now().plusHours(1))));
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\f", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
