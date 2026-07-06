package com.rpb.reservation.auth.api;

import com.rpb.reservation.auth.application.AuthStoreAccess;
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
        String storeId,
        String storeCode,
        String storeName,
        String status,
        String locale,
        boolean defaultStore
    ) {
        static AuthStoreAccessItemResponse from(AuthStoreAccess store) {
            return new AuthStoreAccessItemResponse(
                store.storeId().toString(),
                store.storeCode(),
                store.storeName(),
                store.status(),
                store.locale(),
                store.defaultStore()
            );
        }
    }
}
