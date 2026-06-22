package com.rpb.reservation.queue.api;

import com.rpb.reservation.queue.application.SeatingFromCalledQueueResult;
import com.rpb.reservation.queue.application.command.SeatCalledQueueTicketCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SeatingFromCalledQueueApiMapper {

    public SeatCalledQueueTicketCommand toCommand(
        SeatCalledQueueTicketRequest request,
        UUID storeId,
        UUID queueTicketId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new SeatCalledQueueTicketCommand(
            actor.tenantId(),
            storeId,
            queueTicketId,
            request.tableId(),
            request.tableGroupId(),
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType()),
            trimToNull(request.overrideReasonCode()),
            trimToNull(request.overrideNote()),
            trimToNull(request.note())
        );
    }

    public SeatCalledQueueTicketResponse toResponse(SeatingFromCalledQueueResult result) {
        return new SeatCalledQueueTicketResponse(
            true,
            result.queueTicketId(),
            result.queueTicketNumber(),
            result.queueTicketStatus(),
            result.reservationId(),
            result.reservationCode(),
            result.reservationStatus(),
            result.seatingId(),
            result.seatingStatus(),
            apiResourceType(result.resourceType()),
            result.resourceId(),
            result.alreadySeated(),
            result.events(),
            new ApiIdempotencyResponse(idempotencyStatus(result), result.replayed())
        );
    }

    private static String apiResourceType(String resourceType) {
        if ("dining_table".equals(resourceType)) {
            return "table";
        }
        return resourceType;
    }

    private static String idempotencyStatus(SeatingFromCalledQueueResult result) {
        if (hasText(result.idempotencyStatus())) {
            return result.idempotencyStatus().trim();
        }
        return "completed";
    }

    private static String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
