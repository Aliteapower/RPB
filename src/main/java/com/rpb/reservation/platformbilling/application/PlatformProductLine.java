package com.rpb.reservation.platformbilling.application;

import java.time.OffsetDateTime;
import java.util.List;

public record PlatformProductLine(
    String appKey,
    String displayName,
    String status,
    String defaultEntryRoute,
    String description,
    int sortOrder,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<PlatformProductLinePrice> prices
) {
    public PlatformProductLine(
        String appKey,
        String displayName,
        String status,
        String defaultEntryRoute,
        String description,
        int sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        this(appKey, displayName, status, defaultEntryRoute, description, sortOrder, createdAt, updatedAt, List.of());
    }

    public PlatformProductLine withPrices(List<PlatformProductLinePrice> nextPrices) {
        return new PlatformProductLine(
            appKey,
            displayName,
            status,
            defaultEntryRoute,
            description,
            sortOrder,
            createdAt,
            updatedAt,
            nextPrices == null ? List.of() : List.copyOf(nextPrices)
        );
    }
}
