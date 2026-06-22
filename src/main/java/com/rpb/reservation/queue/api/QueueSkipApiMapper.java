package com.rpb.reservation.queue.api;

import com.rpb.reservation.queue.application.QueueSkipResult;
import com.rpb.reservation.queue.application.command.SkipQueueTicketCommand;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class QueueSkipApiMapper {

    public SkipQueueTicketCommand toCommand(
        SkipQueueTicketRequest request,
        UUID storeId,
        UUID queueTicketId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new SkipQueueTicketCommand(
            actor.tenantId(),
            storeId,
            queueTicketId,
            request.skippedAt(),
            trimToNull(request.reasonCode()),
            trimToNull(request.note()),
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType())
        );
    }

    public SkipQueueTicketResponse toResponse(QueueSkipResult result) {
        return new SkipQueueTicketResponse(
            true,
            result.queueTicketId(),
            result.queueTicketNumber(),
            result.queueTicketStatus(),
            result.reservationId(),
            result.reservationCode(),
            result.reservationStatus(),
            result.skippedAt(),
            result.alreadySkipped(),
            result.events(),
            new ApiIdempotencyResponse(idempotencyStatus(result), result.replayed())
        );
    }

    private static String idempotencyStatus(QueueSkipResult result) {
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
