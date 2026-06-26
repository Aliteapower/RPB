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
    private static final String DEFAULT_MEDIA_SEED_KEY = "restaurant_media_default";

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

    @Transactional(readOnly = true)
    public PlatformCallScreenMediaSeedSet getMediaSeed() {
        return repository.findMediaBySeedKey(DEFAULT_MEDIA_SEED_KEY)
            .filter(seed -> "media".equals(seed.adType()) || "image".equals(seed.adType()))
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

    @Transactional
    public PlatformCallScreenMediaSeedSet updateMediaSeed(PlatformCallScreenMediaSeedCommand command) {
        PlatformCallScreenMediaSeedSet current = getMediaSeed();
        NormalizedMediaSeed input = normalizeMedia(command);
        if (input.version() != null && input.version() != current.version()) {
            throw new PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode.VERSION_CONFLICT);
        }
        return repository.updateMediaSeed(current.id(), input.displayName(), input.status(), input.mediaSlides());
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

    private static NormalizedMediaSeed normalizeMedia(PlatformCallScreenMediaSeedCommand command) {
        if (command == null || command.mediaSlides() == null) {
            throw new PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode.REQUEST_INVALID);
        }
        List<PlatformCallScreenMediaSeedSlideCommand> slides = command.mediaSlides().stream()
            .map(PlatformCallScreenSeedService::normalizeMediaSlide)
            .toList();
        if ("active".equals(command.status()) && slides.isEmpty()) {
            throw new PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode.REQUEST_INVALID);
        }
        ensureUniqueMediaSortOrders(slides);
        return new NormalizedMediaSeed(
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

    private static PlatformCallScreenMediaSeedSlideCommand normalizeMediaSlide(PlatformCallScreenMediaSeedSlideCommand slide) {
        if (slide == null || slide.mediaAssetId() == null || slide.sortOrder() == null || slide.sortOrder() <= 0) {
            throw new PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode.REQUEST_INVALID);
        }
        return new PlatformCallScreenMediaSeedSlideCommand(
            slide.id(),
            slide.mediaAssetId(),
            normalizeMediaKind(slide.mediaKind()),
            optionalText(slide.title()),
            optionalText(slide.altText()),
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

    private static void ensureUniqueMediaSortOrders(List<PlatformCallScreenMediaSeedSlideCommand> slides) {
        Set<Integer> seen = new HashSet<>();
        for (PlatformCallScreenMediaSeedSlideCommand slide : slides) {
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

    private static String normalizeMediaKind(String value) {
        if (!"image".equals(value) && !"video".equals(value)) {
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

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
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

    private record NormalizedMediaSeed(
        String displayName,
        String status,
        List<PlatformCallScreenMediaSeedSlideCommand> mediaSlides,
        Integer version
    ) {
    }
}
