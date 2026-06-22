package com.rpb.reservation.store.persistence.mapper;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.persistence.entity.StorePolicyEntity;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class DefaultStorePolicyMapper implements StorePolicyMapper {

    @Override
    public StorePolicy toDomain(StorePolicyEntity entity) {
        StoreScope scope = new StoreScope(new TenantId(entity.getTenantId()), new StoreId(entity.getStoreId()));
        return new StorePolicy(
            entity.getId(),
            scope,
            entity.getReservationHoldMinutes(),
            entity.getQueueCallHoldMinutes(),
            entity.getExpectedDiningMinutes(),
            entity.getQueueRejoinPolicyCode(),
            entity.getTableAssignmentPolicyCode()
        );
    }

    @Override
    public StorePolicyEntity toEntity(StorePolicy domain) {
        OffsetDateTime now = OffsetDateTime.now();
        return StorePolicyEntity.of(
            domain.id(),
            domain.scope().tenantId().value(),
            domain.scope().storeId().value(),
            domain.reservationHoldMinutes(),
            domain.queueCallHoldMinutes(),
            domain.expectedDiningMinutes(),
            domain.queueRejoinPolicyCode(),
            domain.tableAssignmentPolicyCode(),
            now,
            null,
            now,
            now,
            null,
            0
        );
    }
}
