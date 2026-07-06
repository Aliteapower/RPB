package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.i18n.application.I18nCatalogStoredMessage;
import com.rpb.reservation.i18n.application.port.out.I18nCatalogRepository;
import com.rpb.reservation.i18n.application.port.out.I18nRuntimeMessageRepository;
import com.rpb.reservation.reservation.application.PlatformReservationShareTemplateSeed;
import com.rpb.reservation.reservation.application.PlatformReservationShareTemplateSeedCommand;
import com.rpb.reservation.reservation.application.port.out.PlatformReservationShareTemplateSeedRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformReservationShareTemplateSeedService {
    private static final String FALLBACK_LOCALE = "en-SG";
    private static final String ZH_LOCALE = "zh-CN";
    private static final Set<String> SUPPORTED_LOCALES = Set.of(ZH_LOCALE, FALLBACK_LOCALE);
    private static final String RESERVATION_SHARE_TEMPLATE_I18N_KEY =
        "reservation.share." + ReservationShareTemplateCatalog.defaultSeedKey();

    private final PlatformReservationShareTemplateSeedRepository repository;
    private final ReservationShareTemplateRenderer templateRenderer;
    private final I18nRuntimeMessageRepository runtimeMessageRepository;
    private final I18nCatalogRepository catalogRepository;

    public PlatformReservationShareTemplateSeedService(PlatformReservationShareTemplateSeedRepository repository) {
        this(repository, new ReservationShareTemplateRenderer(), null, null);
    }

    @Autowired
    public PlatformReservationShareTemplateSeedService(
        PlatformReservationShareTemplateSeedRepository repository,
        ReservationShareTemplateRenderer templateRenderer,
        I18nRuntimeMessageRepository runtimeMessageRepository,
        I18nCatalogRepository catalogRepository
    ) {
        this.repository = repository;
        this.templateRenderer = templateRenderer;
        this.runtimeMessageRepository = runtimeMessageRepository;
        this.catalogRepository = catalogRepository;
    }

    @Transactional(readOnly = true)
    public PlatformReservationShareTemplateSeed getDefaultSeed() {
        return repository.findBySeedKey(ReservationShareTemplateCatalog.defaultSeedKey())
            .orElseThrow(() -> new PlatformReservationShareTemplateSeedServiceException(
                PlatformReservationShareTemplateSeedServiceErrorCode.SEED_NOT_FOUND
            ));
    }

    @Transactional(readOnly = true)
    public PlatformReservationShareTemplateSeed getDefaultSeed(String locale) {
        String normalizedLocale = normalizeOptionalLocale(locale);
        if (normalizedLocale == null || !supportsLocaleCatalog()) {
            return getDefaultSeed();
        }

        PlatformReservationShareTemplateSeed legacySeed = getDefaultSeed();
        return platformTemplateMessage(normalizedLocale)
            .map(message -> seedFromMessage(message, legacySeed))
            .orElseGet(() -> missingLocaleSeed(normalizedLocale, legacySeed));
    }

    @Transactional
    public PlatformReservationShareTemplateSeed updateDefaultSeed(PlatformReservationShareTemplateSeedCommand command) {
        NormalizedSeed input = normalize(command);
        PlatformReservationShareTemplateSeed current = getDefaultSeed(input.locale());
        if (input.version() != null && input.version() != current.version()) {
            throw new PlatformReservationShareTemplateSeedServiceException(
                PlatformReservationShareTemplateSeedServiceErrorCode.VERSION_CONFLICT
            );
        }
        if (!templateRenderer.unknownVariables(input.templateText()).isEmpty()) {
            throw new PlatformReservationShareTemplateSeedServiceException(
                PlatformReservationShareTemplateSeedServiceErrorCode.TEMPLATE_UNKNOWN_VARIABLE
            );
        }
        if (SUPPORTED_LOCALES.contains(input.locale()) && supportsLocaleCatalog()) {
            return updateLocaleSeed(current, input);
        }
        return repository.update(
            current.seedKey(),
            input.displayName(),
            input.locale(),
            input.templateText(),
            input.status()
        );
    }

    private PlatformReservationShareTemplateSeed updateLocaleSeed(
        PlatformReservationShareTemplateSeed current,
        NormalizedSeed input
    ) {
        boolean updated = catalogRepository.upsertMessage(
            null,
            null,
            RESERVATION_SHARE_TEMPLATE_I18N_KEY,
            input.locale(),
            input.templateText(),
            toCatalogStatus(input.status()),
            input.version()
        );
        if (!updated) {
            throw new PlatformReservationShareTemplateSeedServiceException(
                PlatformReservationShareTemplateSeedServiceErrorCode.VERSION_CONFLICT
            );
        }
        if (FALLBACK_LOCALE.equals(input.locale())) {
            repository.update(
                current.seedKey(),
                input.displayName(),
                input.locale(),
                input.templateText(),
                input.status()
            );
        }
        return getDefaultSeed(input.locale());
    }

    private Optional<I18nCatalogStoredMessage> platformTemplateMessage(String locale) {
        return runtimeMessageRepository.findMessages(null, null, List.of(RESERVATION_SHARE_TEMPLATE_I18N_KEY))
            .stream()
            .filter(message -> RESERVATION_SHARE_TEMPLATE_I18N_KEY.equals(message.i18nKey()))
            .filter(message -> locale.equals(message.locale()))
            .findFirst();
    }

    private static PlatformReservationShareTemplateSeed seedFromMessage(
        I18nCatalogStoredMessage message,
        PlatformReservationShareTemplateSeed legacySeed
    ) {
        return new PlatformReservationShareTemplateSeed(
            legacySeed.seedKey(),
            displayNameFor(message.locale(), legacySeed.displayName()),
            message.locale(),
            ReservationShareTemplateTextNormalizer.normalize(message.message()),
            fromCatalogStatus(message.status()),
            message.version(),
            ReservationShareTemplateCatalog.allowedVariables()
        );
    }

    private static PlatformReservationShareTemplateSeed missingLocaleSeed(
        String locale,
        PlatformReservationShareTemplateSeed legacySeed
    ) {
        return new PlatformReservationShareTemplateSeed(
            legacySeed.seedKey(),
            displayNameFor(locale, legacySeed.displayName()),
            locale,
            legacySeed.templateText(),
            legacySeed.status(),
            0,
            ReservationShareTemplateCatalog.allowedVariables()
        );
    }

    private boolean supportsLocaleCatalog() {
        return runtimeMessageRepository != null && catalogRepository != null;
    }

    private static NormalizedSeed normalize(PlatformReservationShareTemplateSeedCommand command) {
        if (command == null) {
            throw new PlatformReservationShareTemplateSeedServiceException(
                PlatformReservationShareTemplateSeedServiceErrorCode.REQUEST_INVALID
            );
        }
        return new NormalizedSeed(
            requiredText(command.displayName()),
            requiredText(command.locale()),
            requiredText(command.templateText()),
            normalizeStatus(command.status()),
            command.version()
        );
    }

    private static String normalizeStatus(String value) {
        String normalized = requiredText(value);
        if (!"active".equals(normalized) && !"disabled".equals(normalized)) {
            throw new PlatformReservationShareTemplateSeedServiceException(
                PlatformReservationShareTemplateSeedServiceErrorCode.REQUEST_INVALID
            );
        }
        return normalized;
    }

    private static String normalizeOptionalLocale(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return SUPPORTED_LOCALES.contains(normalized) ? normalized : FALLBACK_LOCALE;
    }

    private static String toCatalogStatus(String value) {
        return "active".equals(value) ? "active" : "inactive";
    }

    private static String fromCatalogStatus(String value) {
        return "active".equals(value) ? "active" : "disabled";
    }

    private static String displayNameFor(String locale, String fallback) {
        if (ZH_LOCALE.equals(locale)) {
            return "餐厅预约确认模板 V1";
        }
        return fallback == null || fallback.isBlank() ? "Restaurant reservation confirmation template V1" : fallback.trim();
    }

    private static String requiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new PlatformReservationShareTemplateSeedServiceException(
                PlatformReservationShareTemplateSeedServiceErrorCode.REQUEST_INVALID
            );
        }
        return value.trim();
    }

    private record NormalizedSeed(
        String displayName,
        String locale,
        String templateText,
        String status,
        Integer version
    ) {
    }
}
