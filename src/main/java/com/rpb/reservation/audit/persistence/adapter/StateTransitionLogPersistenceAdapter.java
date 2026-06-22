package com.rpb.reservation.audit.persistence.adapter;

import com.rpb.reservation.audit.application.port.out.StateTransitionLogRepositoryPort;
import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.audit.persistence.mapper.DefaultStateTransitionLogMapper;
import com.rpb.reservation.audit.persistence.repository.StateTransitionLogJpaRepository;
import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class StateTransitionLogPersistenceAdapter implements StateTransitionLogRepositoryPort {

    private final StateTransitionLogJpaRepository repository;
    private final DefaultStateTransitionLogMapper mapper;

    public StateTransitionLogPersistenceAdapter(
        StateTransitionLogJpaRepository repository,
        DefaultStateTransitionLogMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public StateTransitionLog append(StoreScope scope, StateTransitionLog transitionLog) {
        return mapper.toDomain(repository.save(mapper.toEntity(scope, transitionLog)));
    }

    @Override
    public StateTransitionLog append(TenantScope scope, StateTransitionLog transitionLog) {
        return mapper.toDomain(repository.save(mapper.toEntity(scope, transitionLog)));
    }

    @Override
    public StateTransitionLog append(PlatformScope scope, StateTransitionLog transitionLog) {
        return mapper.toDomain(repository.save(mapper.toEntity(scope, transitionLog)));
    }

    @Override
    public List<StateTransitionLog> findByTarget(StoreScope scope, String targetType, UUID targetId) {
        return repository.findByTenantIdAndStoreIdAndTargetTypeAndTargetIdOrderByOccurredAtAsc(
            scope.tenantId().value(),
            scope.storeId().value(),
            targetType,
            targetId
        ).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<StateTransitionLog> findLatest(StoreScope scope, String targetType, UUID targetId) {
        return repository.findFirstByTenantIdAndStoreIdAndTargetTypeAndTargetIdOrderByOccurredAtDesc(
            scope.tenantId().value(),
            scope.storeId().value(),
            targetType,
            targetId
        ).map(mapper::toDomain);
    }
}
