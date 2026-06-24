package com.rpb.reservation.queue.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueueTicketListResponse(
    boolean success,
    List<ItemResponse> items,
    PageResponse page
) {

    public QueueTicketListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemResponse(
        UUID queueTicketId,
        int queueTicketNumber,
        String queueTicketStatus,
        int partySize,
        String partySizeGroup,
        UUID reservationId,
        String reservationCode,
        String reservationStatus,
        String customerName,
        String customerPhoneMasked,
        String assignedResourceType,
        UUID assignedResourceId,
        String assignedResourceCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant calledAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant holdUntilAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant expiresAt
    ) {
    }

    public record PageResponse(
        int limit,
        int offset,
        int total
    ) {
    }
}
