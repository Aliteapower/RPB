package com.rpb.reservation.table.persistence.mapper;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.persistence.entity.DiningTableEntity;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class DefaultDiningTableMapper implements DiningTableMapper {

    @Override
    public DiningTable toDomain(DiningTableEntity entity) {
        return new DiningTable(
            new TableId(entity.getId()),
            new StoreScope(new TenantId(entity.getTenantId()), new StoreId(entity.getStoreId())),
            entity.getAreaId(),
            entity.getTableCode(),
            new CapacityRange(entity.getCapacityMin(), entity.getCapacityMax()),
            statusFromCode(entity.getStatus()),
            Boolean.TRUE.equals(entity.getCombinable())
        );
    }

    @Override
    public DiningTableEntity toEntity(DiningTable domain) {
        OffsetDateTime now = OffsetDateTime.now();
        return DiningTableEntity.of(
            domain.id().value(),
            domain.scope().tenantId().value(),
            domain.scope().storeId().value(),
            domain.areaId(),
            domain.tableCode(),
            domain.tableCode(),
            domain.capacity().min(),
            domain.capacity().max(),
            domain.status().code(),
            0,
            domain.combinable(),
            now,
            now,
            null,
            0
        );
    }

    private static DiningTableStatus statusFromCode(String code) {
        for (DiningTableStatus status : DiningTableStatus.values()) {
            if (status.code().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown_dining_table_status");
    }
}
