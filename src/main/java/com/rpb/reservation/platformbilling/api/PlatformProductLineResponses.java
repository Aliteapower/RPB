package com.rpb.reservation.platformbilling.api;

import com.rpb.reservation.platformbilling.application.PlatformProductLine;
import com.rpb.reservation.platformbilling.application.PlatformProductLinePage;
import com.rpb.reservation.platformbilling.application.PlatformProductLinePrice;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

record PlatformProductLineListResponse(
    boolean success,
    List<PlatformProductLineItemResponse> productLines,
    List<PlatformProductLineItemResponse> items,
    long total,
    int page,
    int size
) {
    static PlatformProductLineListResponse from(List<PlatformProductLine> productLines) {
        List<PlatformProductLineItemResponse> items = productLines.stream().map(PlatformProductLineItemResponse::from).toList();
        return new PlatformProductLineListResponse(
            true,
            items,
            items,
            items.size(),
            0,
            items.size()
        );
    }

    static PlatformProductLineListResponse from(PlatformProductLinePage page) {
        List<PlatformProductLineItemResponse> items = page.items().stream().map(PlatformProductLineItemResponse::from).toList();
        return new PlatformProductLineListResponse(
            true,
            items,
            items,
            page.total(),
            page.page(),
            page.size()
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
    OffsetDateTime updatedAt,
    List<PlatformProductLinePriceResponse> prices
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
            productLine.updatedAt(),
            productLine.prices().stream().map(PlatformProductLinePriceResponse::from).toList()
        );
    }
}

record PlatformProductLinePriceResponse(
    String billingCycle,
    BigDecimal amount,
    String currency,
    String status,
    int version
) {
    static PlatformProductLinePriceResponse from(PlatformProductLinePrice price) {
        return new PlatformProductLinePriceResponse(
            price.billingCycle(),
            price.amount(),
            price.currency(),
            price.status(),
            price.version()
        );
    }
}
