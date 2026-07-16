package com.rpb.reservation.platformbilling.application;

import java.util.List;

public record PlatformProductLinePage(
    List<PlatformProductLine> items,
    long total,
    int page,
    int size
) {
    public PlatformProductLinePage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
