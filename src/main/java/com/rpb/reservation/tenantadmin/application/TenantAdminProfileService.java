package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaAsset;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaService;
import com.rpb.reservation.tenantadmin.persistence.TenantAdminProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TenantAdminProfileService {
    private final TenantAdminProfileRepository repository;
    private final CallScreenMediaService mediaService;

    public TenantAdminProfileService(TenantAdminProfileRepository repository, CallScreenMediaService mediaService) {
        this.repository = repository;
        this.mediaService = mediaService;
    }

    @Transactional(readOnly = true)
    public TenantAdminProfile getProfile(StoreScope scope) {
        return repository.find(scope)
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.TENANT_PROFILE_NOT_FOUND));
    }

    @Transactional
    public TenantAdminProfile updateProfile(StoreScope scope, TenantAdminProfileCommand command) {
        NormalizedProfileInput input = normalize(command);
        TenantAdminProfile profile = repository.update(
                scope,
                input.displayName(),
                input.defaultLocale(),
                input.contactPhone(),
                input.address(),
                input.principalName()
            )
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.TENANT_PROFILE_NOT_FOUND));
        return profile;
    }

    @Transactional
    public TenantAdminProfile uploadLogo(StoreScope scope, MultipartFile file) {
        getProfile(scope);
        CallScreenMediaAsset asset = mediaService.uploadTenantLogoMedia(scope.tenantId().value(), file);
        return repository.updateLogoMediaAsset(scope, asset.id())
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.TENANT_PROFILE_NOT_FOUND));
    }

    @Transactional
    public TenantAdminProfile clearLogo(StoreScope scope) {
        getProfile(scope);
        return repository.updateLogoMediaAsset(scope, null)
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.TENANT_PROFILE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public CallScreenMediaContent readLogoMedia(StoreScope scope, java.util.UUID assetId) {
        getProfile(scope);
        return mediaService.readTenantLogoMedia(scope.tenantId().value(), assetId);
    }

    private static NormalizedProfileInput normalize(TenantAdminProfileCommand command) {
        if (command == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return new NormalizedProfileInput(
            requiredText(command.displayName()),
            firstText(command.defaultLocale(), "zh-CN"),
            optionalText(command.contactPhone()),
            optionalText(command.address()),
            optionalText(command.principalName())
        );
    }

    private static String firstText(String first, String fallback) {
        String normalized = optionalText(first);
        return normalized == null ? requiredText(fallback) : normalized;
    }

    private static String requiredText(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record NormalizedProfileInput(
        String displayName,
        String defaultLocale,
        String contactPhone,
        String address,
        String principalName
    ) {
    }
}
