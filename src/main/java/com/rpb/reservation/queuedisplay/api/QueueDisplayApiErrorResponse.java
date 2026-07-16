package com.rpb.reservation.queuedisplay.api;

import java.util.Map;

public record QueueDisplayApiErrorResponse(boolean success, ErrorBody error) {
    public static QueueDisplayApiErrorResponse of(QueueDisplayApiErrorCode code) {
        return new QueueDisplayApiErrorResponse(false, new ErrorBody(code.name(), code.messageKey(), Map.of()));
    }

    public record ErrorBody(String code, String messageKey, Map<String, Object> details) {
    }
}
