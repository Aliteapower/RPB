package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.TenantAdminSharePreview;

public record TenantAdminSharePreviewResponse(
    boolean success,
    PreviewBody preview
) {
    public static TenantAdminSharePreviewResponse from(TenantAdminSharePreview preview) {
        return new TenantAdminSharePreviewResponse(true, new PreviewBody(preview.shareText()));
    }

    public record PreviewBody(
        String shareText
    ) {
    }
}
