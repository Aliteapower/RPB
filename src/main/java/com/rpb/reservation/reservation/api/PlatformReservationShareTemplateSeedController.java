package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.PlatformReservationShareTemplateSeedCommand;
import com.rpb.reservation.reservation.application.service.PlatformReservationShareTemplateSeedService;
import com.rpb.reservation.reservation.application.service.PlatformReservationShareTemplateSeedServiceErrorCode;
import com.rpb.reservation.reservation.application.service.PlatformReservationShareTemplateSeedServiceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/reservation/share-template-seed")
public class PlatformReservationShareTemplateSeedController {
    private static final String PLATFORM_ADMIN = "platform_admin";
    private static final String PLATFORM_RESERVATION_SHARE_TEMPLATE_MANAGE = "platform.reservation_share_template.manage";

    private final PlatformReservationShareTemplateSeedService service;
    private final CurrentActorProvider currentActorProvider;

    public PlatformReservationShareTemplateSeedController(
        PlatformReservationShareTemplateSeedService service,
        CurrentActorProvider currentActorProvider
    ) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    public ResponseEntity<PlatformReservationShareTemplateSeedResponse> getDefaultSeed(
        @RequestParam(required = false) String locale
    ) {
        requirePlatformTemplateManager();
        return ResponseEntity.ok(PlatformReservationShareTemplateSeedResponse.from(service.getDefaultSeed(locale)));
    }

    @PatchMapping
    public ResponseEntity<PlatformReservationShareTemplateSeedResponse> updateDefaultSeed(
        @RequestBody(required = false) PlatformReservationShareTemplateSeedRequest request
    ) {
        requirePlatformTemplateManager();
        return ResponseEntity.ok(PlatformReservationShareTemplateSeedResponse.from(
            service.updateDefaultSeed(toCommand(request))
        ));
    }

    @ExceptionHandler(PlatformReservationShareTemplateSeedApiException.class)
    public ResponseEntity<PlatformReservationShareTemplateSeedApiErrorResponse> handleApiException(
        PlatformReservationShareTemplateSeedApiException exception
    ) {
        return apiError(exception.code());
    }

    @ExceptionHandler(PlatformReservationShareTemplateSeedServiceException.class)
    public ResponseEntity<PlatformReservationShareTemplateSeedApiErrorResponse> handleServiceException(
        PlatformReservationShareTemplateSeedServiceException exception
    ) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<PlatformReservationShareTemplateSeedApiErrorResponse> handleDataAccessException(
        DataAccessException exception
    ) {
        return apiError(PlatformReservationShareTemplateSeedApiErrorCode.PERSISTENCE_ERROR);
    }

    private CurrentActor requirePlatformTemplateManager() {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PlatformReservationShareTemplateSeedApiException(
                PlatformReservationShareTemplateSeedApiErrorCode.UNAUTHENTICATED
            ));
        if (!actor.roles().contains(PLATFORM_ADMIN) || !hasPlatformTemplateManagePermission(actor)) {
            throw new PlatformReservationShareTemplateSeedApiException(PlatformReservationShareTemplateSeedApiErrorCode.FORBIDDEN);
        }
        return actor;
    }

    private static boolean hasPlatformTemplateManagePermission(CurrentActor actor) {
        return actor.hasPermission(PLATFORM_RESERVATION_SHARE_TEMPLATE_MANAGE);
    }

    private static ResponseEntity<PlatformReservationShareTemplateSeedApiErrorResponse> apiError(
        PlatformReservationShareTemplateSeedApiErrorCode code
    ) {
        return ResponseEntity.status(code.httpStatus()).body(PlatformReservationShareTemplateSeedApiErrorResponse.of(code));
    }

    private static PlatformReservationShareTemplateSeedApiErrorCode toApiError(
        PlatformReservationShareTemplateSeedServiceErrorCode code
    ) {
        return switch (code) {
            case REQUEST_INVALID -> PlatformReservationShareTemplateSeedApiErrorCode.REQUEST_INVALID;
            case SEED_NOT_FOUND -> PlatformReservationShareTemplateSeedApiErrorCode.SEED_NOT_FOUND;
            case VERSION_CONFLICT -> PlatformReservationShareTemplateSeedApiErrorCode.VERSION_CONFLICT;
            case TEMPLATE_UNKNOWN_VARIABLE -> PlatformReservationShareTemplateSeedApiErrorCode.TEMPLATE_UNKNOWN_VARIABLE;
        };
    }

    private static PlatformReservationShareTemplateSeedCommand toCommand(PlatformReservationShareTemplateSeedRequest request) {
        if (request == null) {
            return null;
        }
        return new PlatformReservationShareTemplateSeedCommand(
            request.displayName(),
            request.locale(),
            request.templateText(),
            request.status(),
            request.version()
        );
    }
}
