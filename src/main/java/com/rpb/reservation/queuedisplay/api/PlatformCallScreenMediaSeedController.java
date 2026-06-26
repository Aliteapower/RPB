package com.rpb.reservation.queuedisplay.api;

import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaService;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceErrorCode;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceException;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenMediaSeedCommand;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenMediaSeedSlideCommand;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedService;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedServiceErrorCode;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedServiceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/platform/call-screen")
public class PlatformCallScreenMediaSeedController {
    private static final String PLATFORM_ADMIN = "platform_admin";
    private static final String PLATFORM_CALL_SCREEN_AD_MANAGE = "platform.call_screen_ad.manage";
    private static final String PLATFORM_TENANT_MANAGE = "platform.tenant.manage";

    private final PlatformCallScreenSeedService seedService;
    private final CallScreenMediaService mediaService;
    private final CurrentActorProvider currentActorProvider;

    public PlatformCallScreenMediaSeedController(
        PlatformCallScreenSeedService seedService,
        CallScreenMediaService mediaService,
        CurrentActorProvider currentActorProvider
    ) {
        this.seedService = seedService;
        this.mediaService = mediaService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping("/media-seed")
    public ResponseEntity<PlatformCallScreenSeedResponses.MediaSeedResponse> getMediaSeed() {
        requirePlatformTemplateManager();
        return ResponseEntity.ok(PlatformCallScreenSeedResponses.MediaSeedResponse.from(seedService.getMediaSeed()));
    }

    @PatchMapping("/media-seed")
    public ResponseEntity<PlatformCallScreenSeedResponses.MediaSeedResponse> updateMediaSeed(
        @RequestBody(required = false) PlatformCallScreenSeedRequests.MediaSeedRequest request
    ) {
        requirePlatformTemplateManager();
        return ResponseEntity.ok(PlatformCallScreenSeedResponses.MediaSeedResponse.from(
            seedService.updateMediaSeed(toCommand(request))
        ));
    }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlatformCallScreenSeedResponses.MediaAssetResponse> uploadMedia(
        @RequestParam("file") MultipartFile file
    ) {
        requirePlatformTemplateManager();
        return ResponseEntity.status(201).body(PlatformCallScreenSeedResponses.MediaAssetResponse.from(
            mediaService.uploadPlatformMedia(file)
        ));
    }

    @GetMapping("/media/{assetId}")
    public ResponseEntity<Resource> readMedia(@PathVariable UUID assetId) {
        requirePlatformTemplateManager();
        CallScreenMediaContent content = mediaService.readPlatformMedia(assetId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType()))
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
            .header("X-Content-Type-Options", "nosniff")
            .body(content.resource());
    }

    @ExceptionHandler(PlatformCallScreenSeedApiException.class)
    public ResponseEntity<PlatformCallScreenSeedApiErrorResponse> handleApiException(
        PlatformCallScreenSeedApiException exception
    ) {
        return apiError(exception.code());
    }

    @ExceptionHandler(PlatformCallScreenSeedServiceException.class)
    public ResponseEntity<PlatformCallScreenSeedApiErrorResponse> handleServiceException(
        PlatformCallScreenSeedServiceException exception
    ) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(CallScreenMediaServiceException.class)
    public ResponseEntity<PlatformCallScreenSeedApiErrorResponse> handleMediaServiceException(
        CallScreenMediaServiceException exception
    ) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<PlatformCallScreenSeedApiErrorResponse> handleDataAccessException(DataAccessException exception) {
        return apiError(PlatformCallScreenSeedApiErrorCode.PERSISTENCE_ERROR);
    }

    private CurrentActor requirePlatformTemplateManager() {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PlatformCallScreenSeedApiException(PlatformCallScreenSeedApiErrorCode.UNAUTHENTICATED));
        if (!actor.roles().contains(PLATFORM_ADMIN) || !hasPlatformTemplateManagePermission(actor)) {
            throw new PlatformCallScreenSeedApiException(PlatformCallScreenSeedApiErrorCode.FORBIDDEN);
        }
        return actor;
    }

    private static boolean hasPlatformTemplateManagePermission(CurrentActor actor) {
        return actor.hasPermission(PLATFORM_CALL_SCREEN_AD_MANAGE) || actor.hasPermission(PLATFORM_TENANT_MANAGE);
    }

    private static ResponseEntity<PlatformCallScreenSeedApiErrorResponse> apiError(PlatformCallScreenSeedApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(PlatformCallScreenSeedApiErrorResponse.of(code));
    }

    private static PlatformCallScreenSeedApiErrorCode toApiError(PlatformCallScreenSeedServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> PlatformCallScreenSeedApiErrorCode.REQUEST_INVALID;
            case SEED_NOT_FOUND -> PlatformCallScreenSeedApiErrorCode.SEED_NOT_FOUND;
            case VERSION_CONFLICT -> PlatformCallScreenSeedApiErrorCode.VERSION_CONFLICT;
        };
    }

    private static PlatformCallScreenSeedApiErrorCode toApiError(CallScreenMediaServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> PlatformCallScreenSeedApiErrorCode.REQUEST_INVALID;
            case MEDIA_NOT_FOUND -> PlatformCallScreenSeedApiErrorCode.MEDIA_NOT_FOUND;
            case PERSISTENCE_ERROR -> PlatformCallScreenSeedApiErrorCode.PERSISTENCE_ERROR;
        };
    }

    private static PlatformCallScreenMediaSeedCommand toCommand(PlatformCallScreenSeedRequests.MediaSeedRequest request) {
        if (request == null) {
            return null;
        }
        List<PlatformCallScreenMediaSeedSlideCommand> slides = request.mediaSlides() == null
            ? List.of()
            : request.mediaSlides().stream()
                .map(PlatformCallScreenMediaSeedController::toCommand)
                .toList();
        return new PlatformCallScreenMediaSeedCommand(request.displayName(), request.status(), slides, request.version());
    }

    private static PlatformCallScreenMediaSeedSlideCommand toCommand(PlatformCallScreenSeedRequests.MediaSeedSlideRequest request) {
        if (request == null) {
            return null;
        }
        return new PlatformCallScreenMediaSeedSlideCommand(
            request.id(),
            request.mediaAssetId(),
            request.mediaKind(),
            request.title(),
            request.altText(),
            request.sortOrder(),
            request.status(),
            request.version()
        );
    }
}
