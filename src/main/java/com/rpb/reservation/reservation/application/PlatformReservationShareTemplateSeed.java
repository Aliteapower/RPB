package com.rpb.reservation.reservation.application;

import java.util.List;

public record PlatformReservationShareTemplateSeed(
    String seedKey,
    String displayName,
    String locale,
    String templateText,
    String status,
    int version,
    List<String> allowedVariables
) {
    public PlatformReservationShareTemplateSeed {
        allowedVariables = List.copyOf(allowedVariables == null ? List.of() : allowedVariables);
    }
}
