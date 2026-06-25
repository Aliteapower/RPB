package com.rpb.reservation.walkin.api;

import com.rpb.reservation.walkin.application.WalkInQueueResult;
import com.rpb.reservation.walkin.application.command.QueueWalkInCommand;
import com.rpb.reservation.queue.application.QueueTicketDisplayNumbers;
import org.springframework.stereotype.Component;

@Component
public class WalkInQueueApiMapper {

    public QueueWalkInCommand toCommand(
        QueueWalkInRequest request,
        java.util.UUID storeId,
        String idempotencyKey,
        CurrentActor actor
    ) {
        return new QueueWalkInCommand(
            actor.tenantId(),
            storeId,
            request.partySize(),
            request.customerId(),
            trimToNull(request.customerName()),
            trimToNull(request.customerNickname()),
            trimToNull(request.phoneE164()),
            trimToNull(request.note()),
            idempotencyKey.trim(),
            actor.actorId(),
            trimToNull(actor.actorType())
        );
    }

    public QueueWalkInResponse toResponse(WalkInQueueResult result) {
        return new QueueWalkInResponse(
            true,
            result.walkInId(),
            result.queueTicketId(),
            result.queueTicketNumber(),
            QueueTicketDisplayNumbers.fromGroupCode(result.partySizeGroup(), result.queueTicketNumber()),
            result.queueTicketStatus(),
            result.partySize(),
            result.partySizeGroup(),
            result.businessDate() == null ? null : result.businessDate().toString(),
            result.queuePosition(),
            result.alreadyQueued(),
            result.events(),
            new ApiIdempotencyResponse(idempotencyStatus(result), result.replayed())
        );
    }

    private static String idempotencyStatus(WalkInQueueResult result) {
        return hasText(result.idempotencyStatus()) ? result.idempotencyStatus().trim() : "completed";
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
