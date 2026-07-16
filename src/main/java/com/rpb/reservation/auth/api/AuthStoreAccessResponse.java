package com.rpb.reservation.auth.api;

import com.rpb.reservation.auth.application.AuthStoreAccess;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaService;
import java.util.List;

public record AuthStoreAccessResponse(
    boolean success,
    List<AuthStoreAccessItemResponse> stores
) {
    public static AuthStoreAccessResponse from(List<AuthStoreAccess> stores) {
        return new AuthStoreAccessResponse(
            true,
            stores.stream().map(AuthStoreAccessItemResponse::from).toList()
        );
    }

    public record AuthStoreAccessItemResponse(
        String tenantId,
        String tenantCode,
        String operatingEntityId,
        String operatingEntityName,
        String storeId,
        String storeCode,
        String storeName,
        String shareDisplayName,
        String tenantLogoMediaUrl,
        String status,
        String locale,
        boolean defaultStore
    ) {
        static AuthStoreAccessItemResponse from(AuthStoreAccess store) {
            return new AuthStoreAccessItemResponse(
                store.tenantId().toString(),
                store.tenantCode(),
                store.operatingEntityId() == null ? null : store.operatingEntityId().toString(),
                store.operatingEntityName(),
                store.storeId().toString(),
                store.storeCode(),
                store.storeName(),
                store.shareDisplayName(),
                tenantLogoMediaUrl(store),
                store.status(),
                store.locale(),
                store.defaultStore()
            );
        }

        private static String tenantLogoMediaUrl(AuthStoreAccess store) {
            return store.tenantLogoMediaAssetId() == null
                ? null
                : CallScreenMediaService.tenantLogoMediaUrl(store.tenantId(), store.tenantLogoMediaAssetId());
        }
    }
}
