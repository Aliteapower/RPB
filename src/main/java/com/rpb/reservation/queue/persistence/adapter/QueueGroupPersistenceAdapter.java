package com.rpb.reservation.queue.persistence.adapter;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.queue.application.port.out.QueueGroupRepositoryPort;
import com.rpb.reservation.queue.domain.QueueGroup;
import com.rpb.reservation.queue.persistence.mapper.QueueGroupMapper;
import com.rpb.reservation.queue.persistence.repository.QueueGroupJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class QueueGroupPersistenceAdapter implements QueueGroupRepositoryPort {

    private final QueueGroupJpaRepository repository;
    private final QueueGroupMapper mapper;

    public QueueGroupPersistenceAdapter(QueueGroupJpaRepository repository, QueueGroupMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<QueueGroup> findActiveByCode(StoreScope scope, String groupCode) {
        return repository.findByTenantIdAndStoreIdAndGroupCodeAndStatusAndDeletedAtIsNull(
            scope.tenantId().value(),
            scope.storeId().value(),
            groupCode,
            "active"
        ).map(mapper::toDomain);
    }

    @Override
    public Optional<QueueGroup> findActiveByPartySize(StoreScope scope, PartySize partySize) {
        return repository.findActiveByPartySize(
            scope.tenantId().value(),
            scope.storeId().value(),
            partySize.value()
        ).map(mapper::toDomain);
    }
}
