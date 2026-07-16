package com.rpb.reservation.queuedisplay.api;

import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedCommand;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedService;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedServiceErrorCode;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedServiceException;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedSlideCommand;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/call-screen/text-seed")
public class PlatformCallScreenSeedController {
    private static final String PLATFORM_ADMIN = "platform_admin";
    private static final String PLATFORM_CALL_SCREEN_AD_MANAGE = "platform.call_screen_ad.manage";
    private static final String PLATFORM_TENANT_MANAGE = "platform.tenant.manage";

    private final PlatformCallScreenSeedService service;
    private final CurrentActorProvider currentActorProvider;

    public PlatformCallScreenSeedController(
        PlatformCallScreenSeedService service,
        CurrentActorProvider currentActorProvider
    ) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    public ResponseEntity<PlatformCallScreenSeedResponses.TextSeedResponse> getTextSeed() {
        requirePlatformTemplateManager();
        return ResponseEntity.ok(PlatformCallScreenSeedResponses.TextSeedResponse.from(service.getTextSeed()));
    }

    @PatchMapping
    public ResponseEntity<PlatformCallScreenSeedResponses.TextSeedResponse> updateTextSeed(
        @RequestBody(required = false) PlatformCallScreenSeedRequests.TextSeedRequest request
    ) {
        requirePlatformTemplateManager();
        return ResponseEntity.ok(PlatformCallScreenSeedResponses.TextSeedResponse.from(
            service.updateTextSeed(toCommand(request))
        ));
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

    private static PlatformCallScreenSeedCommand toCommand(PlatformCallScreenSeedRequests.TextSeedRequest request) {
        if (request == null) {
            return null;
        }
        List<PlatformCallScreenSeedSlideCommand> slides = request.slides() == null
            ? List.of()
            : request.slides().stream()
                .map(PlatformCallScreenSeedController::toCommand)
                .toList();
        return new PlatformCallScreenSeedCommand(request.displayName(), request.status(), slides, request.version());
    }

    private static PlatformCallScreenSeedSlideCommand toCommand(PlatformCallScreenSeedRequests.TextSeedSlideRequest request) {
        if (request == null) {
            return null;
        }
        return new PlatformCallScreenSeedSlideCommand(
            request.id(),
            request.title(),
            request.subtitle(),
            request.tagline(),
            request.sortOrder(),
            request.status(),
            request.version()
        );
    }
}
