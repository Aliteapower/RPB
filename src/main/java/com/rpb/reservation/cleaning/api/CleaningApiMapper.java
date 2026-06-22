package com.rpb.reservation.cleaning.api;

import com.rpb.reservation.cleaning.application.CleaningApplicationResult;
import com.rpb.reservation.cleaning.application.command.CompleteCleaningCommand;
import com.rpb.reservation.cleaning.application.command.StartCleaningCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CleaningApiMapper {

    public StartCleaningCommand toCommand(
        StartCleaningRequest request,
        UUID storeId,
        UUID seatingId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new StartCleaningCommand(
            actor.tenantId(),
            storeId,
            seatingId,
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType()),
            trimToNull(request.reasonCode()),
            trimToNull(request.note())
        );
    }

    public CompleteCleaningCommand toCommand(
        CompleteCleaningRequest request,
        UUID storeId,
        UUID cleaningId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new CompleteCleaningCommand(
            actor.tenantId(),
            storeId,
            cleaningId,
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType()),
            trimToNull(request.reasonCode()),
            trimToNull(request.note())
        );
    }

    public StartCleaningResponse toStartResponse(CleaningApplicationResult result) {
        return new StartCleaningResponse(
            true,
            result.cleaningId(),
            result.seatingId(),
            new StartCleaningResponse.ResourceResponse(
                apiResourceType(result.resourceType()),
                result.resourceId(),
                null
            ),
            result.cleaningStatus(),
            result.currentTableStatus(),
            List.of("cleaning.started", "table.cleaning"),
            ApiIdempotencyResponse.completed(result.replayed())
        );
    }

    public CompleteCleaningResponse toCompleteResponse(CleaningApplicationResult result) {
        return new CompleteCleaningResponse(
            true,
            result.cleaningId(),
            new CompleteCleaningResponse.ResourceResponse(
                apiResourceType(result.resourceType()),
                result.resourceId(),
                null
            ),
            result.cleaningStatus(),
            result.currentTableStatus(),
            List.of("cleaning.completed", "table.available"),
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
