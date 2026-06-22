package com.rpb.reservation.queue.persistence.mapper;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queue.domain.QueueGroup;
import com.rpb.reservation.queue.persistence.entity.QueueGroupEntity;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class DefaultQueueGroupMapper implements QueueGroupMapper {

    @Override
    public QueueGroup toDomain(QueueGroupEntity entity) {
        return new QueueGroup(
            entity.getId(),
            new StoreScope(new TenantId(entity.getTenantId()), new StoreId(entity.getStoreId())),
            entity.getGroupCode(),
            entity.getMinPartySize(),
            entity.getMaxPartySize(),
            entity.getDisplayI18nKey(),
            entity.getStatus()
        );
    }

    @Override
    public QueueGroupEntity toEntity(QueueGroup domain) {
        OffsetDateTime now = OffsetDateTime.now();
        return QueueGroupEntity.of(
            domain.id(),
            domain.scope().tenantId().value(),
            domain.scope().storeId().value(),
            domain.groupCode(),
            domain.minPartySize(),
            domain.maxPartySize(),
            domain.displayI18nKey(),
            domain.status(),
            0,
            now,
            now,
            null,
            0
        );
    }
}
