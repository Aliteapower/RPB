package com.rpb.reservation.audit.persistence.adapter;

import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.persistence.mapper.DefaultAuditLogMapper;
import com.rpb.reservation.audit.persistence.repository.AuditLogJpaRepository;
import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.time.TimeRange;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogPersistenceAdapter implements AuditLogRepositoryPort {

    private final AuditLogJpaRepository repository;
    private final DefaultAuditLogMapper mapper;

    public AuditLogPersistenceAdapter(AuditLogJpaRepository repository, DefaultAuditLogMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public AuditLog append(StoreScope scope, AuditLog auditLog) {
        return mapper.toDomain(repository.save(mapper.toEntity(scope, auditLog)));
    }

    @Override
    public AuditLog append(TenantScope scope, AuditLog auditLog) {
        return mapper.toDomain(repository.save(mapper.toEntity(scope, auditLog)));
    }

    @Override
    public AuditLog append(PlatformScope scope, AuditLog auditLog) {
        return mapper.toDomain(repository.save(mapper.toEntity(scope, auditLog)));
    }

    @Override
    public List<AuditLog> findByTarget(StoreScope scope, String targetType, UUID targetId) {
        return repository.findByTenantIdAndStoreIdAndTargetTypeAndTargetIdOrderByOccurredAtAsc(
            scope.tenantId().value(),
            scope.storeId().value(),
            targetType,
            targetId
        ).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AuditLog> findByOperation(StoreScope scope, String operationCode, TimeRange timeRange) {
        return repository.findByTenantIdAndStoreIdAndOperationCodeAndOccurredAtBetweenOrderByOccurredAtAsc(
            scope.tenantId().value(),
            scope.storeId().value(),
            operationCode,
            OffsetDateTime.ofInstant(timeRange.start(), ZoneOffset.UTC),
            OffsetDateTime.ofInstant(timeRange.end(), ZoneOffset.UTC)
        ).stream().map(mapper::toDomain).toList();
    }
}
