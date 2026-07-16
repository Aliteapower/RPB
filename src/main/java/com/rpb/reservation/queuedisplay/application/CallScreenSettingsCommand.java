package com.rpb.reservation.queuedisplay.application;

import java.util.UUID;

public record CallScreenSettingsCommand(
    UUID activeAdSetId,
    String adMode,
    String status,
    Integer slideDurationSeconds,
    Integer statePollSeconds,
    Boolean showWaitingPreview,
    Integer version
) {
}
