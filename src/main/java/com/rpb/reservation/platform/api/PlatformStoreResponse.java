package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformStore;

public record PlatformStoreResponse(
    boolean success,
    PlatformStoreItemResponse store
) {
    public static PlatformStoreResponse from(PlatformStore store) {
        return new PlatformStoreResponse(true, PlatformStoreItemResponse.from(store));
    }
}
