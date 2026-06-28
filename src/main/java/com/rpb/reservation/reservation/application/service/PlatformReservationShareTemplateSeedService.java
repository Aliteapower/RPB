package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.reservation.application.PlatformReservationShareTemplateSeed;
import com.rpb.reservation.reservation.application.PlatformReservationShareTemplateSeedCommand;
import com.rpb.reservation.reservation.application.port.out.PlatformReservationShareTemplateSeedRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformReservationShareTemplateSeedService {
    private final PlatformReservationShareTemplateSeedRepository repository;
    private final ReservationShareTemplateRenderer templateRenderer;

    public PlatformReservationShareTemplateSeedService(PlatformReservationShareTemplateSeedRepository repository) {
        this(repository, new ReservationShareTemplateRenderer());
    }

    @Autowired
    public PlatformReservationShareTemplateSeedService(
        PlatformReservationShareTemplateSeedRepository repository,
        ReservationShareTemplateRenderer templateRenderer
    ) {
        this.repository = repository;
        this.templateRenderer = templateRenderer;
    }

    @Transactional(readOnly = true)
    public PlatformReservationShareTemplateSeed getDefaultSeed() {
        return repository.findBySeedKey(ReservationShareTemplateCatalog.defaultSeedKey())
            .orElseThrow(() -> new PlatformReservationShareTemplateSeedServiceException(
                PlatformReservationShareTemplateSeedServiceErrorCode.SEED_NOT_FOUND
            ));
    }

    @Transactional
    public PlatformReservationShareTemplateSeed updateDefaultSeed(PlatformReservationShareTemplateSeedCommand command) {
        PlatformReservationShareTemplateSeed current = getDefaultSeed();
        NormalizedSeed input = normalize(command);
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
        return repository.update(
            current.seedKey(),
            input.displayName(),
            input.locale(),
            input.templateText(),
            input.status()
        );
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
