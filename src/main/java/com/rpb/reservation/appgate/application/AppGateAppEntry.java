package com.rpb.reservation.appgate.application;

import java.util.Set;

public record AppGateAppEntry(
    String appKey,
    String appName,
    String status,
    String entryRoute,
    boolean entryVisible,
    Set<String> permissions
) {
    public AppGateAppEntry {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
