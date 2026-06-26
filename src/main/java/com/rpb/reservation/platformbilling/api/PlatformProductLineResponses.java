package com.rpb.reservation.platformbilling.api;

import com.rpb.reservation.platformbilling.application.PlatformProductLine;
import java.time.OffsetDateTime;
import java.util.List;

record PlatformProductLineListResponse(
    boolean success,
    List<PlatformProductLineItemResponse> productLines
) {
    static PlatformProductLineListResponse from(List<PlatformProductLine> productLines) {
        return new PlatformProductLineListResponse(
            true,
            productLines.stream().map(PlatformProductLineItemResponse::from).toList()
        );
    }
}

record PlatformProductLineResponse(
    boolean success,
    PlatformProductLineItemResponse productLine
) {
    static PlatformProductLineResponse from(PlatformProductLine productLine) {
        return new PlatformProductLineResponse(true, PlatformProductLineItemResponse.from(productLine));
    }
}

record PlatformProductLineItemResponse(
    String appKey,
    String displayName,
    String status,
    String defaultEntryRoute,
    String description,
    int sortOrder,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    static PlatformProductLineItemResponse from(PlatformProductLine productLine) {
        return new PlatformProductLineItemResponse(
            productLine.appKey(),
            productLine.displayName(),
            productLine.status(),
            productLine.defaultEntryRoute(),
            productLine.description(),
            productLine.sortOrder(),
            productLine.createdAt(),
            productLine.updatedAt()
        );
    }
}
