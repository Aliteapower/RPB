package com.rpb.reservation.queuedisplay.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queuedisplay.persistence.CallScreenAdminRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallScreenAdminService {
    private static final String DEFAULT_SEED_KEY = "restaurant_default";

    private final CallScreenAdminRepository repository;

    public CallScreenAdminService(CallScreenAdminRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CallScreenSetting getSettings(StoreScope scope) {
        CallScreenAdSet defaultAdSet = ensureTenantDefaultTextSet(scope);
        return repository.findSetting(scope)
            .orElseGet(() -> repository.upsertDefaultSetting(scope, defaultAdSet.id()));
    }

    @Transactional
    public CallScreenSetting updateSettings(StoreScope scope, CallScreenSettingsCommand command) {
        NormalizedSettings settings = normalizeSettings(command);
        CallScreenSetting current = getSettings(scope);
        if (settings.version() != null && settings.version() != current.version()) {
            throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.VERSION_CONFLICT);
        }
        CallScreenAdSet adSet = repository.findAdSet(scope.tenantId().value(), settings.activeAdSetId())
            .orElseThrow(() -> new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.AD_SET_NOT_FOUND));
        if (!"active".equals(adSet.status())) {
            throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
        }
        if (!adModeMatches(adSet.adType(), settings.adMode())) {
            throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
        }
        return repository.updateSetting(
            scope,
            settings.activeAdSetId(),
            settings.adMode(),
            settings.status(),
            settings.slideDurationSeconds(),
            settings.statePollSeconds(),
            settings.showWaitingPreview()
        );
    }

    @Transactional
    public List<CallScreenAdSet> listAdSets(StoreScope scope) {
        ensureTenantDefaultTextSet(scope);
        return repository.listAdSets(scope.tenantId().value());
    }

    @Transactional
    public CallScreenAdSet createAdSet(StoreScope scope, CallScreenAdSetCommand command) {
        NormalizedAdSet input = normalizeAdSet(command, true);
        return repository.createTextAdSet(scope.tenantId().value(), input.name(), input.status(), input.textSlides());
    }

    @Transactional(readOnly = true)
    public CallScreenAdSet getAdSet(StoreScope scope, UUID adSetId) {
        return repository.findAdSet(scope.tenantId().value(), adSetId)
            .orElseThrow(() -> new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.AD_SET_NOT_FOUND));
    }

    @Transactional
    public CallScreenAdSet updateAdSet(StoreScope scope, UUID adSetId, CallScreenAdSetCommand command) {
        NormalizedAdSet input = normalizeAdSet(command, false);
        CallScreenAdSet current = repository.findAdSet(scope.tenantId().value(), adSetId)
            .orElseThrow(() -> new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.AD_SET_NOT_FOUND));
        if (!adModeMatches(current.adType(), input.adType())) {
            throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
        }
        if (input.version() != null && input.version() != current.version()) {
            throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.VERSION_CONFLICT);
        }
        return repository.updateTextAdSet(scope.tenantId().value(), adSetId, input.name(), input.status(), input.textSlides());
    }

    private CallScreenAdSet ensureTenantDefaultTextSet(StoreScope scope) {
        return repository.findDefaultTextSet(scope.tenantId().value(), DEFAULT_SEED_KEY)
            .orElseGet(() -> repository.cloneSeedTextSet(scope.tenantId().value(), DEFAULT_SEED_KEY));
    }

    private static NormalizedSettings normalizeSettings(CallScreenSettingsCommand command) {
        if (command == null || command.activeAdSetId() == null) {
            throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
        }
        String adMode = normalizeAdType(command.adMode());
        int slideDuration = range(command.slideDurationSeconds(), 3, 60);
        int statePoll = range(command.statePollSeconds(), 2, 30);
        return new NormalizedSettings(
            command.activeAdSetId(),
            adMode,
            "active".equals(command.status()) || "disabled".equals(command.status()) ? command.status() : "active",
            slideDuration,
            statePoll,
            command.showWaitingPreview() == null || command.showWaitingPreview(),
            command.version()
        );
    }

    private static NormalizedAdSet normalizeAdSet(CallScreenAdSetCommand command, boolean create) {
        if (command == null) {
            throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
        }
        String adType = normalizeAdType(command.adType());
        String name = requiredText(command.name());
        String status = normalizeStatus(command.status());
        List<CallScreenTextSlideCommand> textSlides = command.slides().stream()
            .map(CallScreenAdminService::normalizeTextSlide)
            .toList();
        if (textSlides.isEmpty()) {
            throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
        }
        ensureUniqueSortOrders(textSlides.stream().map(CallScreenTextSlideCommand::sortOrder).toList());
        return new NormalizedAdSet(adType, name, status, textSlides, create ? null : command.version());
    }

    private static CallScreenTextSlideCommand normalizeTextSlide(CallScreenTextSlideCommand slide) {
        if (slide == null || slide.sortOrder() == null || slide.sortOrder() <= 0) {
            throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
        }
        return new CallScreenTextSlideCommand(
            slide.id(),
            requiredText(slide.title()),
            requiredText(slide.subtitle()),
            requiredText(slide.tagline()),
            slide.sortOrder(),
            normalizeStatus(slide.status()),
            slide.version()
        );
    }

    private static void ensureUniqueSortOrders(List<Integer> sortOrders) {
        Set<Integer> seen = new HashSet<>();
        for (Integer sortOrder : sortOrders) {
            if (!seen.add(sortOrder)) {
                throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
            }
        }
    }

    private static int range(Integer value, int min, int max) {
        if (value == null || value < min || value > max) {
            throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
        }
        return value;
    }

    private static String normalizeStatus(String value) {
        if (!"active".equals(value) && !"disabled".equals(value)) {
            throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
        }
        return value;
    }

    private static String normalizeAdType(String value) {
        if ("text".equals(value)) {
            return "text";
        }
        throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
    }

    private static boolean adModeMatches(String adType, String adMode) {
        if (adType == null || adMode == null) {
            return false;
        }
        return normalizeAdType(adType).equals(normalizeAdType(adMode));
    }

    private static String requiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new CallScreenAdminServiceException(CallScreenAdminServiceErrorCode.REQUEST_INVALID);
        }
        return value.trim();
    }

    private record NormalizedSettings(
        UUID activeAdSetId,
        String adMode,
        String status,
        int slideDurationSeconds,
        int statePollSeconds,
        boolean showWaitingPreview,
        Integer version
    ) {
    }

    private record NormalizedAdSet(
        String adType,
        String name,
        String status,
        List<CallScreenTextSlideCommand> textSlides,
        Integer version
    ) {
    }
}
