package com.rpb.reservation.walkin.api;

import com.rpb.reservation.walkin.application.WalkInDirectSeatingResult;
import com.rpb.reservation.walkin.application.command.SeatWalkInDirectlyCommand;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WalkInDirectSeatingApiMapper {

    public SeatWalkInDirectlyCommand toCommand(
        SeatWalkInDirectlyRequest request,
        java.util.UUID storeId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new SeatWalkInDirectlyCommand(
            actor.tenantId(),
            storeId,
            request.partySize(),
            request.customerId(),
            trimToNull(request.customerName()),
            trimToNull(request.customerNickname()),
            trimToNull(request.phoneE164()),
            request.tableId(),
            request.tableGroupId(),
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType()),
            trimToNull(request.overrideReasonCode()),
            trimToNull(request.overrideNote())
        );
    }

    public SeatWalkInDirectlyResponse toResponse(WalkInDirectSeatingResult result) {
        return new SeatWalkInDirectlyResponse(
            true,
            result.walkInId(),
            result.seatingId(),
            new SeatWalkInDirectlyResponse.ResourceResponse(
                apiResourceType(result.resourceType()),
                result.resourceId(),
                null
            ),
            result.partySizeSnapshot(),
            result.seatingStatus(),
            List.of("walk_in.created", "seating.created", "table.occupied"),
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
        return resourceType == null ? null : resourceType.toUpperCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
