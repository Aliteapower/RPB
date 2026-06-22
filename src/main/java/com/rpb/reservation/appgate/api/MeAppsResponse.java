package com.rpb.reservation.appgate.api;

import com.rpb.reservation.appgate.application.AppGateAppEntry;
import java.util.List;
import java.util.Set;

public record MeAppsResponse(
    boolean success,
    List<AppEntryResponse> apps
) {
    public static MeAppsResponse of(List<AppGateAppEntry> apps) {
        return new MeAppsResponse(true, apps.stream().map(AppEntryResponse::from).toList());
    }

    public static MeAppsResponse empty() {
        return new MeAppsResponse(true, List.of());
    }

    public record AppEntryResponse(
        String appKey,
        String appName,
        String status,
        String entryRoute,
        boolean entryVisible,
        Set<String> permissions
    ) {
        private static AppEntryResponse from(AppGateAppEntry entry) {
            return new AppEntryResponse(
                entry.appKey(),
                entry.appName(),
                entry.status(),
                entry.entryRoute(),
                entry.entryVisible(),
                entry.permissions()
            );
        }
    }
}
