package com.rpb.reservation.queuedisplay.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record QueueDisplayResult(
    boolean success,
    QueueDisplayError error,
    Instant serverNow,
    String storeTimezone,
    String storeTimeText,
    LocalDate businessDate,
    QueueDisplayCurrentCall currentCall,
    int waitingCount,
    List<QueueDisplayWaitingPreviewItem> waitingPreview,
    QueueDisplayAds ads,
    String tenantLogoUrl
) {
    public QueueDisplayResult {
        waitingPreview = waitingPreview == null ? List.of() : List.copyOf(waitingPreview);
    }

    public static QueueDisplayResult success(
        Instant serverNow,
        String storeTimezone,
        String storeTimeText,
        LocalDate businessDate,
        QueueDisplayCurrentCall currentCall,
        int waitingCount,
        List<QueueDisplayWaitingPreviewItem> waitingPreview,
        QueueDisplayAds ads,
        String tenantLogoUrl
    ) {
        return new QueueDisplayResult(true, null, serverNow, storeTimezone, storeTimeText, businessDate, currentCall, waitingCount, waitingPreview, ads, tenantLogoUrl);
    }

    public static QueueDisplayResult failure(QueueDisplayError error) {
        return new QueueDisplayResult(false, error, null, null, null, null, null, 0, List.of(), null, null);
    }
}
