package com.rpb.reservation.queue.api;

import com.rpb.reservation.queue.application.QueueTicketListItem;
import com.rpb.reservation.queue.application.QueueTicketListResult;
import com.rpb.reservation.queue.application.query.QueueTicketListQuery;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class QueueTicketListApiMapper {

    public QueueTicketListQuery toQuery(
        UUID storeId,
        String status,
        String limit,
        String offset,
        CurrentActor actor
    ) {
        return new QueueTicketListQuery(
            actor.tenantId(),
            storeId,
            actor.actorId(),
            trimToNull(actor.actorType()),
            trimToNull(status),
            trimToNull(limit),
            trimToNull(offset)
        );
    }

    public QueueTicketListResponse toResponse(QueueTicketListResult result) {
        return new QueueTicketListResponse(
            true,
            items(result.items()),
            new QueueTicketListResponse.PageResponse(
                result.page().limit(),
                result.page().offset(),
                result.page().total()
            )
        );
    }

    private static List<QueueTicketListResponse.ItemResponse> items(List<QueueTicketListItem> items) {
        return items.stream().map(QueueTicketListApiMapper::item).toList();
    }

    private static QueueTicketListResponse.ItemResponse item(QueueTicketListItem item) {
        return new QueueTicketListResponse.ItemResponse(
            item.queueTicketId(),
            item.queueTicketNumber(),
            item.queueTicketStatus(),
            item.partySize(),
            item.partySizeGroup(),
            item.reservationId(),
            item.reservationCode(),
            item.reservationStatus(),
            item.customerName(),
            item.customerPhoneMasked(),
            item.createdAt(),
            item.calledAt(),
            item.holdUntilAt(),
            item.expiresAt()
        );
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
