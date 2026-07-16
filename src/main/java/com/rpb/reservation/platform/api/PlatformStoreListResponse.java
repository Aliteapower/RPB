package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformStore;
import java.util.List;

public record PlatformStoreListResponse(
    boolean success,
    List<PlatformStoreItemResponse> stores
) {
    public static PlatformStoreListResponse from(List<PlatformStore> stores) {
        return new PlatformStoreListResponse(
            true,
            stores.stream().map(PlatformStoreItemResponse::from).toList()
        );
    }
}
