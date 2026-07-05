package com.rpb.reservation.i18n.api;

import com.rpb.reservation.i18n.application.I18nCatalogMessageCommand;
import com.rpb.reservation.i18n.application.I18nCatalogService;
import com.rpb.reservation.i18n.application.I18nCatalogServiceErrorCode;
import com.rpb.reservation.i18n.application.I18nCatalogServiceException;
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
@RequestMapping("/api/v1/platform/i18n/catalog")
public class PlatformI18nCatalogController {
    private static final String PLATFORM_ADMIN = "platform_admin";
    private static final String PLATFORM_TENANT_MANAGE = "platform.tenant.manage";

    private final I18nCatalogService service;
    private final CurrentActorProvider currentActorProvider;

    public PlatformI18nCatalogController(I18nCatalogService service, CurrentActorProvider currentActorProvider) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    public ResponseEntity<I18nCatalogResponse> getCatalog() {
        requirePlatformI18nManager();
        return ResponseEntity.ok(I18nCatalogResponse.from(service.platformCatalog()));
    }

    @PatchMapping
    public ResponseEntity<I18nCatalogResponse> updateCatalog(
        @RequestBody(required = false) I18nCatalogRequests.PlatformUpdateRequest request
    ) {
        requirePlatformI18nManager();
        return ResponseEntity.ok(I18nCatalogResponse.from(service.updatePlatformCatalog(toCommands(request))));
    }

    @ExceptionHandler(I18nCatalogApiException.class)
    public ResponseEntity<I18nCatalogApiErrorResponse> handleApiException(I18nCatalogApiException exception) {
        return apiError(exception.code());
    }

    @ExceptionHandler(I18nCatalogServiceException.class)
    public ResponseEntity<I18nCatalogApiErrorResponse> handleServiceException(I18nCatalogServiceException exception) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<I18nCatalogApiErrorResponse> handleDataAccessException(DataAccessException exception) {
        return apiError(I18nCatalogApiErrorCode.PERSISTENCE_ERROR);
    }

    private CurrentActor requirePlatformI18nManager() {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new I18nCatalogApiException(I18nCatalogApiErrorCode.UNAUTHENTICATED));
        if (!actor.roles().contains(PLATFORM_ADMIN) || !actor.hasPermission(PLATFORM_TENANT_MANAGE)) {
            throw new I18nCatalogApiException(I18nCatalogApiErrorCode.FORBIDDEN);
        }
        return actor;
    }

    private static List<I18nCatalogMessageCommand> toCommands(I18nCatalogRequests.PlatformUpdateRequest request) {
        if (request == null || request.messages() == null) {
            return null;
        }
        return request.messages().stream()
            .map(message -> message == null ? null : message.toCommand())
            .toList();
    }

    private static ResponseEntity<I18nCatalogApiErrorResponse> apiError(I18nCatalogApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(I18nCatalogApiErrorResponse.of(code));
    }

    private static I18nCatalogApiErrorCode toApiError(I18nCatalogServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> I18nCatalogApiErrorCode.REQUEST_INVALID;
            case KEY_NOT_ALLOWED -> I18nCatalogApiErrorCode.KEY_NOT_ALLOWED;
            case LOCALE_NOT_SUPPORTED -> I18nCatalogApiErrorCode.LOCALE_NOT_SUPPORTED;
            case PLACEHOLDER_UNKNOWN -> I18nCatalogApiErrorCode.PLACEHOLDER_UNKNOWN;
            case VERSION_CONFLICT -> I18nCatalogApiErrorCode.VERSION_CONFLICT;
        };
    }
}
