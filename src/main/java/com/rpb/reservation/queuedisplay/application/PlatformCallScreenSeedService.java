package com.rpb.reservation.queuedisplay.application;

import com.rpb.reservation.queuedisplay.persistence.PlatformCallScreenSeedRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformCallScreenSeedService {
    private static final String DEFAULT_TEXT_SEED_KEY = "restaurant_default";

    private final PlatformCallScreenSeedRepository repository;

    public PlatformCallScreenSeedService(PlatformCallScreenSeedRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PlatformCallScreenSeedSet getTextSeed() {
        return repository.findBySeedKey(DEFAULT_TEXT_SEED_KEY)
            .filter(seed -> "text".equals(seed.adType()))
            .orElseThrow(() -> new PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode.SEED_NOT_FOUND));
    }

    @Transactional
    public PlatformCallScreenSeedSet updateTextSeed(PlatformCallScreenSeedCommand command) {
        PlatformCallScreenSeedSet current = getTextSeed();
        NormalizedTextSeed input = normalize(command);
        if (input.version() != null && input.version() != current.version()) {
            throw new PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode.VERSION_CONFLICT);
        }
        return repository.updateTextSeed(current.id(), input.displayName(), input.status(), input.slides());
    }

    private static NormalizedTextSeed normalize(PlatformCallScreenSeedCommand command) {
        if (command == null || command.slides() == null || command.slides().isEmpty()) {
            throw new PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode.REQUEST_INVALID);
        }
        List<PlatformCallScreenSeedSlideCommand> slides = command.slides().stream()
            .map(PlatformCallScreenSeedService::normalizeSlide)
            .toList();
        ensureUniqueSortOrders(slides);
        return new NormalizedTextSeed(
            requiredText(command.displayName()),
            normalizeStatus(command.status()),
            slides,
            command.version()
        );
    }

    private static PlatformCallScreenSeedSlideCommand normalizeSlide(PlatformCallScreenSeedSlideCommand slide) {
        if (slide == null || slide.sortOrder() == null || slide.sortOrder() <= 0) {
            throw new PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode.REQUEST_INVALID);
        }
        return new PlatformCallScreenSeedSlideCommand(
            slide.id(),
            requiredText(slide.title()),
            requiredText(slide.subtitle()),
            requiredText(slide.tagline()),
            slide.sortOrder(),
            normalizeStatus(slide.status()),
            slide.version()
        );
    }

    private static void ensureUniqueSortOrders(List<PlatformCallScreenSeedSlideCommand> slides) {
        Set<Integer> seen = new HashSet<>();
        for (PlatformCallScreenSeedSlideCommand slide : slides) {
            if (!seen.add(slide.sortOrder())) {
                throw new PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode.REQUEST_INVALID);
            }
        }
    }

    private static String normalizeStatus(String value) {
        if (!"active".equals(value) && !"disabled".equals(value)) {
            throw new PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode.REQUEST_INVALID);
        }
        return value;
    }

    private static String requiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode.REQUEST_INVALID);
        }
        return value.trim();
    }

    private record NormalizedTextSeed(
        String displayName,
        String status,
        List<PlatformCallScreenSeedSlideCommand> slides,
        Integer version
    ) {
    }

}
