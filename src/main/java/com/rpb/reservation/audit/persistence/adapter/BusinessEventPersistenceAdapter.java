package com.rpb.reservation.audit.persistence.adapter;

import com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.persistence.mapper.DefaultBusinessEventMapper;
import com.rpb.reservation.audit.persistence.repository.BusinessEventJpaRepository;
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
public class BusinessEventPersistenceAdapter implements BusinessEventRepositoryPort {

    private final BusinessEventJpaRepository repository;
    private final DefaultBusinessEventMapper mapper;

    public BusinessEventPersistenceAdapter(BusinessEventJpaRepository repository, DefaultBusinessEventMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public BusinessEvent append(StoreScope scope, BusinessEvent event) {
        return mapper.toDomain(repository.save(mapper.toEntity(scope, event)));
    }

    @Override
    public BusinessEvent append(TenantScope scope, BusinessEvent event) {
        return mapper.toDomain(repository.save(mapper.toEntity(scope, event)));
    }

    @Override
    public BusinessEvent append(PlatformScope scope, BusinessEvent event) {
        return mapper.toDomain(repository.save(mapper.toEntity(scope, event)));
    }

    @Override
    public List<BusinessEvent> findByTarget(StoreScope scope, String targetType, UUID targetId) {
        return repository.findByTenantIdAndStoreIdAndTargetTypeAndTargetIdOrderByOccurredAtAsc(
            scope.tenantId().value(),
            scope.storeId().value(),
            targetType,
            targetId
        ).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<BusinessEvent> findTimeline(StoreScope scope, TimeRange timeRange) {
        return repository.findByTenantIdAndStoreIdAndOccurredAtBetweenOrderByOccurredAtAsc(
            scope.tenantId().value(),
            scope.storeId().value(),
            OffsetDateTime.ofInstant(timeRange.start(), ZoneOffset.UTC),
            OffsetDateTime.ofInstant(timeRange.end(), ZoneOffset.UTC)
        ).stream().map(mapper::toDomain).toList();
    }
}
