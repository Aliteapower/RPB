package com.rpb.reservation.queue.api;

import java.util.Map;

public record QueueTicketListApiErrorResponse(
    boolean success,
    ErrorBody error
) {

    public static QueueTicketListApiErrorResponse of(QueueTicketListApiErrorCode code) {
        return new QueueTicketListApiErrorResponse(
            false,
            new ErrorBody(code.name(), code.messageKey(), Map.of())
        );
    }

    public record ErrorBody(
        String code,
        String messageKey,
        Map<String, Object> details
    ) {
    }
}
