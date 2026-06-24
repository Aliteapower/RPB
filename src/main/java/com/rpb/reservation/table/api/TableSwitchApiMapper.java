package com.rpb.reservation.table.api;

import com.rpb.reservation.table.application.TableSwitchResult;
import com.rpb.reservation.table.application.command.SwitchTableCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TableSwitchApiMapper {

    public SwitchTableCommand toCommand(
        TableSwitchRequest request,
        UUID storeId,
        UUID seatingId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new SwitchTableCommand(
            actor.tenantId(),
            storeId,
            seatingId,
            request.tableId(),
            request.tableGroupId(),
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType()),
            trimToNull(request.reasonCode()),
            trimToNull(request.note())
        );
    }

    public TableSwitchResponse toResponse(TableSwitchResult result) {
        return new TableSwitchResponse(
            true,
            result.seatingId(),
            new TableSwitchResponse.ResourceResponse(
                apiResourceType(result.fromResourceType()),
                result.fromResourceId(),
                result.fromResourceStatus()
            ),
            new TableSwitchResponse.ResourceResponse(
                apiResourceType(result.toResourceType()),
                result.toResourceId(),
                result.toResourceStatus()
            ),
            result.cleaningId(),
            result.seatingStatus(),
            List.of("table.switch.completed", "table.cleaning", "table.occupied"),
            ApiIdempotencyResponse.completed(result.replayed())
        );
    }

    private static String apiResourceType(String resourceType) {
        if ("dining_table".equals(resourceType)) {
            return "TABLE";
        }
        if ("table_group".equals(resourceType)) {
            return "TABLE_GROUP";
        }
        return resourceType == null ? null : resourceType.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
