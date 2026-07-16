package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformTenantListResult;
import com.rpb.reservation.platform.application.PlatformTenantPage;
import java.util.List;

public record PlatformTenantListResponse(
    boolean success,
    List<PlatformTenantItemResponse> tenants,
    PageResponse page
) {
    public static PlatformTenantListResponse from(PlatformTenantListResult result) {
        return new PlatformTenantListResponse(
            true,
            result.tenants().stream().map(PlatformTenantItemResponse::from).toList(),
            PageResponse.from(result.page())
        );
    }

    public record PageResponse(
        int limit,
        int offset,
        int total
    ) {
        static PageResponse from(PlatformTenantPage page) {
            return new PageResponse(page.limit(), page.offset(), page.total());
        }
    }
}
