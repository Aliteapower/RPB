package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.application.TenantAdminProfile;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantAdminProfileResponse(
    boolean success,
    ProfileItemResponse profile
) {
    public static TenantAdminProfileResponse from(StoreScope scope, TenantAdminProfile profile) {
        return new TenantAdminProfileResponse(true, ProfileItemResponse.from(scope, profile));
    }

    public record ProfileItemResponse(
        UUID tenantId,
        String tenantCode,
        UUID storeId,
        String storeCode,
        String displayName,
        String status,
        String defaultLocale,
        String contactPhone,
        String address,
        String principalName,
        String logoMediaUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        private static ProfileItemResponse from(StoreScope scope, TenantAdminProfile profile) {
            return new ProfileItemResponse(
                profile.tenantId(),
                profile.tenantCode(),
                profile.storeId(),
                profile.storeCode(),
                profile.displayName(),
                profile.status(),
                profile.defaultLocale(),
                profile.contactPhone(),
                profile.address(),
                profile.principalName(),
                TenantAdminProfileResponse.logoMediaUrl(scope, profile.logoMediaAssetId()),
                profile.createdAt(),
                profile.updatedAt()
            );
        }
    }

    private static String logoMediaUrl(StoreScope scope, UUID assetId) {
        return assetId == null
            ? null
            : "/api/v1/stores/" + scope.storeId().value() + "/tenant-admin/profile/logo/media/" + assetId;
    }
}
