package com.rpb.reservation.queuedisplay.application;

import java.util.UUID;

public record CallScreenSetting(
    UUID activeAdSetId,
    String adMode,
    String status,
    int slideDurationSeconds,
    int statePollSeconds,
    boolean showWaitingPreview,
    int version
) {
}
