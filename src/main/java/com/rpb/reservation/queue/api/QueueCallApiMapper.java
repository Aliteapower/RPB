package com.rpb.reservation.queue.api;

import com.rpb.reservation.queue.application.QueueCallResult;
import com.rpb.reservation.queue.application.command.CallQueueTicketCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class QueueCallApiMapper {

    public CallQueueTicketCommand toCommand(
        CallQueueTicketRequest request,
        UUID storeId,
        UUID queueTicketId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new CallQueueTicketCommand(
            actor.tenantId(),
            storeId,
            queueTicketId,
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType()),
            request.calledAt(),
            trimToNull(request.reasonCode()),
            trimToNull(request.note())
        );
    }

    public CallQueueTicketResponse toResponse(QueueCallResult result) {
        return new CallQueueTicketResponse(
            true,
            result.queueTicketId(),
            result.queueTicketNumber(),
            result.queueTicketStatus(),
            result.reservationId(),
            result.reservationCode(),
            result.reservationStatus(),
            result.calledAt(),
            result.holdUntilAt(),
            result.alreadyCalled(),
            result.events(),
            new ApiIdempotencyResponse(idempotencyStatus(result), result.replayed())
        );
    }

    private static String idempotencyStatus(QueueCallResult result) {
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
