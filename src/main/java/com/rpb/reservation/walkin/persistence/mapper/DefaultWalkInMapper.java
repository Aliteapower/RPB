package com.rpb.reservation.walkin.persistence.mapper;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.domain.WalkIn;
import com.rpb.reservation.walkin.persistence.entity.WalkInEntity;
import com.rpb.reservation.walkin.value.WalkInId;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class DefaultWalkInMapper implements WalkInMapper {

    @Override
    public WalkIn toDomain(WalkInEntity entity) {
        return new WalkIn(
            new WalkInId(entity.getId()),
            new StoreScope(new TenantId(entity.getTenantId()), new StoreId(entity.getStoreId())),
            new PartySize(entity.getPartySize()),
            entity.getStatus()
        );
    }

    @Override
    public WalkInEntity toEntity(WalkIn domain) {
        OffsetDateTime now = OffsetDateTime.now();
        return WalkInEntity.of(
            domain.id().value(),
            domain.scope().tenantId().value(),
            domain.scope().storeId().value(),
            null,
            "W-" + domain.id().value(),
            domain.partySize().value(),
            LocalDate.now(),
            now,
            domain.status(),
            null,
            now,
            now,
            null,
            0
        );
    }
}
