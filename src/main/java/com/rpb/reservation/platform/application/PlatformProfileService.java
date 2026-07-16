package com.rpb.reservation.platform.application;

import com.rpb.reservation.platform.persistence.PlatformProfileRepository;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaAsset;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaService;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PlatformProfileService {
    private static final Set<String> SOCIAL_LINK_STATUSES = Set.of("active", "disabled");

    private final PlatformProfileRepository repository;
    private final CallScreenMediaService mediaService;

    public PlatformProfileService(PlatformProfileRepository repository, CallScreenMediaService mediaService) {
        this.repository = repository;
        this.mediaService = mediaService;
    }

    @Transactional(readOnly = true)
    public PlatformProfile getProfile() {
        return repository.findProfile()
            .orElseThrow(() -> new PlatformProfileServiceException(PlatformProfileServiceErrorCode.PROFILE_NOT_FOUND));
    }

    @Transactional
    public PlatformProfile updateProfile(PlatformProfileMutationCommand command) {
        if (command == null) {
            throw new PlatformProfileServiceException(PlatformProfileServiceErrorCode.REQUEST_INVALID);
        }
        try {
            return repository.upsertProfile(
                requiredText(command.platformName()),
                optionalText(command.uen()),
                optionalText(command.address()),
                optionalText(command.phone()),
                optionalText(command.email()),
                optionalText(command.website())
            );
        } catch (DataIntegrityViolationException exception) {
            throw new PlatformProfileServiceException(PlatformProfileServiceErrorCode.REQUEST_INVALID);
        }
    }

    @Transactional
    public PlatformProfile uploadProfileLogo(MultipartFile file) {
        CallScreenMediaAsset asset = mediaService.uploadPlatformLogoMedia(file);
        return repository.updateProfileLogo(asset.id());
    }

    @Transactional
    public PlatformProfile clearProfileLogo() {
        return repository.updateProfileLogo(null);
    }

    @Transactional
    public PlatformProfile createSocialLink(PlatformSocialLinkMutationCommand command) {
        NormalizedSocialLink input = normalizeSocialLink(command, repository.nextSocialLinkSortOrder());
        try {
            repository.createSocialLink(UUID.randomUUID(), input.displayName(), input.url(), input.sortOrder(), input.status());
            return getProfile();
        } catch (DataIntegrityViolationException exception) {
            throw new PlatformProfileServiceException(PlatformProfileServiceErrorCode.REQUEST_INVALID);
        }
    }

    @Transactional
    public PlatformProfile updateSocialLink(UUID linkId, PlatformSocialLinkMutationCommand command) {
        repository.findSocialLink(linkId)
            .orElseThrow(() -> new PlatformProfileServiceException(PlatformProfileServiceErrorCode.SOCIAL_LINK_NOT_FOUND));
        NormalizedSocialLink input = normalizeSocialLink(command, repository.nextSocialLinkSortOrder());
        try {
            repository.updateSocialLink(linkId, input.displayName(), input.url(), input.sortOrder(), input.status())
                .orElseThrow(() -> new PlatformProfileServiceException(PlatformProfileServiceErrorCode.SOCIAL_LINK_NOT_FOUND));
            return getProfile();
        } catch (DataIntegrityViolationException exception) {
            throw new PlatformProfileServiceException(PlatformProfileServiceErrorCode.REQUEST_INVALID);
        }
    }

    @Transactional
    public PlatformProfile deleteSocialLink(UUID linkId) {
        repository.softDeleteSocialLink(linkId)
            .orElseThrow(() -> new PlatformProfileServiceException(PlatformProfileServiceErrorCode.SOCIAL_LINK_NOT_FOUND));
        return getProfile();
    }

    @Transactional
    public PlatformProfile uploadSocialLinkLogo(UUID linkId, MultipartFile file) {
        repository.findSocialLink(linkId)
            .orElseThrow(() -> new PlatformProfileServiceException(PlatformProfileServiceErrorCode.SOCIAL_LINK_NOT_FOUND));
        CallScreenMediaAsset asset = mediaService.uploadPlatformLogoMedia(file);
        repository.updateSocialLinkLogo(linkId, asset.id())
            .orElseThrow(() -> new PlatformProfileServiceException(PlatformProfileServiceErrorCode.SOCIAL_LINK_NOT_FOUND));
        return getProfile();
    }

    @Transactional
    public PlatformProfile clearSocialLinkLogo(UUID linkId) {
        repository.updateSocialLinkLogo(linkId, null)
            .orElseThrow(() -> new PlatformProfileServiceException(PlatformProfileServiceErrorCode.SOCIAL_LINK_NOT_FOUND));
        return getProfile();
    }

    private static NormalizedSocialLink normalizeSocialLink(PlatformSocialLinkMutationCommand command, int defaultSortOrder) {
        if (command == null) {
            throw new PlatformProfileServiceException(PlatformProfileServiceErrorCode.REQUEST_INVALID);
        }
        int sortOrder = command.sortOrder() == null ? defaultSortOrder : command.sortOrder();
        if (sortOrder <= 0) {
            throw new PlatformProfileServiceException(PlatformProfileServiceErrorCode.REQUEST_INVALID);
        }
        return new NormalizedSocialLink(
            requiredText(command.displayName()),
            requiredText(command.url()),
            sortOrder,
            normalizeStatus(command.status())
        );
    }

    private static String normalizeStatus(String status) {
        String normalized = firstText(status, "active").toLowerCase(Locale.ROOT);
        if (!SOCIAL_LINK_STATUSES.contains(normalized)) {
            throw new PlatformProfileServiceException(PlatformProfileServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String firstText(String value, String fallback) {
        String normalized = optionalText(value);
        return normalized == null ? fallback : normalized;
    }

    private static String requiredText(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new PlatformProfileServiceException(PlatformProfileServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record NormalizedSocialLink(String displayName, String url, int sortOrder, String status) {
    }
}
