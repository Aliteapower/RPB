package com.rpb.reservation.table.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.table.application.TemporaryTableGroupResult;
import com.rpb.reservation.table.application.service.DissolveTemporaryTableGroupCommand;
import com.rpb.reservation.table.application.service.SaveTemporaryTableGroupCommand;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TemporaryTableGroupApiMapper {

    public SaveTemporaryTableGroupCommand toSaveCommand(
        SaveTemporaryTableGroupRequest request,
        UUID storeId,
        UUID tenantId,
        BusinessDate businessDate
    ) {
        return new SaveTemporaryTableGroupCommand(
            new StoreScope(new TenantId(tenantId), storeId),
            request.groupName(),
            request.tableIds(),
            businessDate
        );
    }

    public DissolveTemporaryTableGroupCommand toDissolveCommand(UUID storeId, UUID tenantId, UUID tableGroupId) {
        return new DissolveTemporaryTableGroupCommand(
            new StoreScope(new TenantId(tenantId), storeId),
            tableGroupId
        );
    }

    public TemporaryTableGroupResponse toResponse(TemporaryTableGroupResult result) {
        return new TemporaryTableGroupResponse(
            true,
            result.group().id().value(),
            result.group().groupCode(),
            result.group().groupType(),
            result.group().status().code(),
            result.group().capacity().min(),
            result.group().capacity().max(),
            result.members().stream().map(TableGroupMember::tableId).map(tableId -> tableId.value()).toList()
        );
    }
}
