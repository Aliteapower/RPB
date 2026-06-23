package com.rpb.reservation.queue.api;

import com.rpb.reservation.queue.application.QueueRejoinResult;
import com.rpb.reservation.queue.application.command.RejoinQueueTicketCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class QueueRejoinApiMapper {

    public RejoinQueueTicketCommand toCommand(
        RejoinQueueTicketRequest request,
        UUID storeId,
        UUID queueTicketId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new RejoinQueueTicketCommand(
            actor.tenantId(),
            storeId,
            queueTicketId,
            trimToNull(request.note()),
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType())
        );
    }

    public RejoinQueueTicketResponse toResponse(QueueRejoinResult result) {
        return new RejoinQueueTicketResponse(
            true,
            result.queueTicketId(),
            result.queueTicketNumber(),
            result.queueTicketStatus(),
            result.queuePosition(),
            result.reservationId(),
            result.reservationCode(),
            result.reservationStatus(),
            result.rejoinedAt(),
            result.alreadyRejoined(),
            result.events(),
            new ApiIdempotencyResponse(idempotencyStatus(result), result.replayed())
        );
    }

    private static String idempotencyStatus(QueueRejoinResult result) {
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
