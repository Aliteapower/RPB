package com.rpb.reservation.store.persistence.mapper;

import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.persistence.entity.StoreEntity;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class DefaultStoreMapper implements StoreMapper {

    @Override
    public Store toDomain(StoreEntity entity) {
        return new Store(
            new StoreId(entity.getId()),
            new TenantId(entity.getTenantId()),
            entity.getStoreCode(),
            entity.getTimezone(),
            entity.getLocale(),
            entity.getStatus()
        );
    }

    @Override
    public StoreEntity toEntity(Store domain) {
        OffsetDateTime now = OffsetDateTime.now();
        return StoreEntity.of(
            domain.id().value(),
            domain.tenantId().value(),
            domain.storeCode(),
            domain.storeCode(),
            domain.status(),
            domain.timezone(),
            domain.locale(),
            "DD-MM-YYYY",
            "HH:mm",
            "USD",
            now,
            now,
            null,
            0
        );
    }
}
