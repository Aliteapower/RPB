package com.rpb.reservation.queue.api;

import com.rpb.reservation.queue.application.QueueCancelResult;
import com.rpb.reservation.queue.application.command.CancelQueueTicketCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class QueueCancelApiMapper {

    public CancelQueueTicketCommand toCommand(
        CancelQueueTicketRequest request,
        UUID storeId,
        UUID queueTicketId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new CancelQueueTicketCommand(
            actor.tenantId(),
            storeId,
            queueTicketId,
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType()),
            request.cancelledAt(),
            trimToNull(request.reasonCode()),
            trimToNull(request.note())
        );
    }

    public CancelQueueTicketResponse toResponse(QueueCancelResult result) {
        return new CancelQueueTicketResponse(
            true,
            result.queueTicketId(),
            result.queueTicketNumber(),
            result.queueTicketStatus(),
            result.reservationId(),
            result.reservationCode(),
            result.reservationStatus(),
            result.walkInId(),
            result.cancelledAt() == null ? null : result.cancelledAt().toString(),
            result.cancellationReasonCode(),
            result.alreadyCancelled(),
            result.events(),
            new ApiIdempotencyResponse(idempotencyStatus(result), result.replayed())
        );
    }

    private static String idempotencyStatus(QueueCancelResult result) {
        return hasText(result.idempotencyStatus()) ? result.idempotencyStatus().trim() : "completed";
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
