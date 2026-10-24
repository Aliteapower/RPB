package com.rpb.reservation.dailyledger.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.dailyledger.domain.LedgerException;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceException;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceErrorCode;
import com.rpb.reservation.tenantadmin.application.TenantAdminProfileService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only adapter to the existing shared profile; ledger has no branding write API. */
@Service
@Transactional(readOnly = true)
public class LedgerBrandingService {
    private final TenantAdminProfileService profiles;
    private final ObjectMapper json;

    public LedgerBrandingService(TenantAdminProfileService profiles, ObjectMapper json) {
        this.profiles = profiles;
        this.json = json;
    }

    public ObjectNode resolve(StoreScope scope, JsonNode legacy) {
        var profile = profiles.getProfile(scope);
        String logo = profile.logoMediaAssetId() != null
            ? "/api/v1/stores/" + scope.storeId().value() + "/daily-ledger/branding/logo/" + profile.logoMediaAssetId()
            : "";
        return json.createObjectNode().put("store_name", profile.displayName())
            .put("store_name_en", "").put("logo_data", logo);
    }

    public CallScreenMediaContent logo(StoreScope scope, UUID assetId) {
        if (!assetId.equals(profiles.getProfile(scope).logoMediaAssetId())) {
            throw new LedgerException("LOGO_NOT_FOUND", 404, "Logo不存在或已更新");
        }
        try {
            return profiles.readLogoMedia(scope, assetId);
        } catch (CallScreenMediaServiceException error) {
            if (error.code() == CallScreenMediaServiceErrorCode.MEDIA_NOT_FOUND) {
                throw new LedgerException("LOGO_NOT_FOUND", 404, "Logo不存在或已更新");
            }
            throw new LedgerException("LOGO_READ_FAILED", 500, "Logo暂时无法读取");
        }
    }
}
