package com.rpb.reservation.table.persistence.mapper;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.persistence.entity.TableGroupEntity;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class DefaultTableGroupMapper implements TableGroupMapper {

    @Override
    public TableGroup toDomain(TableGroupEntity entity) {
        return new TableGroup(
            new TableGroupId(entity.getId()),
            new StoreScope(new TenantId(entity.getTenantId()), new StoreId(entity.getStoreId())),
            entity.getGroupCode(),
            entity.getGroupType(),
            new CapacityRange(entity.getCapacityMin(), entity.getCapacityMax()),
            statusFromCode(entity.getStatus())
        );
    }

    @Override
    public TableGroupEntity toEntity(TableGroup domain) {
        OffsetDateTime now = OffsetDateTime.now();
        return TableGroupEntity.of(
            domain.id().value(),
            domain.scope().tenantId().value(),
            domain.scope().storeId().value(),
            domain.groupCode(),
            domain.groupType(),
            domain.status().code(),
            domain.groupCode(),
            domain.capacity().min(),
            domain.capacity().max(),
            null,
            null,
            now,
            now,
            null,
            0
        );
    }

    private static TableGroupStatus statusFromCode(String code) {
        for (TableGroupStatus status : TableGroupStatus.values()) {
            if (status.code().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown_table_group_status");
    }
}
