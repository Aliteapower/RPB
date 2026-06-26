package com.rpb.reservation.queuedisplay.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queuedisplay.application.CallScreenAdSetCommand;
import com.rpb.reservation.queuedisplay.application.CallScreenAdminService;
import com.rpb.reservation.queuedisplay.application.CallScreenAdminServiceErrorCode;
import com.rpb.reservation.queuedisplay.application.CallScreenAdminServiceException;
import com.rpb.reservation.queuedisplay.application.CallScreenSettingsCommand;
import com.rpb.reservation.queuedisplay.application.CallScreenTextSlideCommand;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin/call-screen")
public class CallScreenAdminController {
    private static final String TENANT_ADMIN = "tenant_admin";
    private static final String TENANT_ADMIN_MANAGE = "tenant.admin.manage";

    private final CallScreenAdminService service;
    private final CurrentActorProvider currentActorProvider;

    public CallScreenAdminController(
        CallScreenAdminService service,
        CurrentActorProvider currentActorProvider
    ) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping("/settings")
    public ResponseEntity<CallScreenAdminResponses.SettingsResponse> getSettings(@PathVariable UUID storeId) {
        return ResponseEntity.ok(CallScreenAdminResponses.SettingsResponse.from(service.getSettings(requireTenantAdminScope(storeId))));
    }

    @PatchMapping("/settings")
    public ResponseEntity<CallScreenAdminResponses.SettingsResponse> updateSettings(
        @PathVariable UUID storeId,
        @RequestBody(required = false) CallScreenAdminRequests.SettingsRequest request
    ) {
        return ResponseEntity.ok(CallScreenAdminResponses.SettingsResponse.from(
            service.updateSettings(requireTenantAdminScope(storeId), toCommand(request))
        ));
    }

    @GetMapping("/ad-sets")
    public ResponseEntity<CallScreenAdminResponses.AdSetListResponse> listAdSets(@PathVariable UUID storeId) {
        return ResponseEntity.ok(CallScreenAdminResponses.AdSetListResponse.from(service.listAdSets(requireTenantAdminScope(storeId))));
    }

    @PostMapping("/ad-sets")
    public ResponseEntity<CallScreenAdminResponses.AdSetResponse> createAdSet(
        @PathVariable UUID storeId,
        @RequestBody(required = false) CallScreenAdminRequests.AdSetRequest request
    ) {
        return ResponseEntity.status(201).body(CallScreenAdminResponses.AdSetResponse.from(
            service.createAdSet(requireTenantAdminScope(storeId), toCommand(request))
        ));
    }

    @GetMapping("/ad-sets/{adSetId}")
    public ResponseEntity<CallScreenAdminResponses.AdSetResponse> getAdSet(
        @PathVariable UUID storeId,
        @PathVariable UUID adSetId
    ) {
        return ResponseEntity.ok(CallScreenAdminResponses.AdSetResponse.from(service.getAdSet(requireTenantAdminScope(storeId), adSetId)));
    }

    @PatchMapping("/ad-sets/{adSetId}")
    public ResponseEntity<CallScreenAdminResponses.AdSetResponse> updateAdSet(
        @PathVariable UUID storeId,
        @PathVariable UUID adSetId,
        @RequestBody(required = false) CallScreenAdminRequests.AdSetRequest request
    ) {
        return ResponseEntity.ok(CallScreenAdminResponses.AdSetResponse.from(
            service.updateAdSet(requireTenantAdminScope(storeId), adSetId, toCommand(request))
        ));
    }

    @ExceptionHandler(CallScreenAdminApiException.class)
    public ResponseEntity<CallScreenAdminApiErrorResponse> handleApiException(CallScreenAdminApiException exception) {
        return apiError(exception.code());
    }

    @ExceptionHandler(CallScreenAdminServiceException.class)
    public ResponseEntity<CallScreenAdminApiErrorResponse> handleServiceException(CallScreenAdminServiceException exception) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<CallScreenAdminApiErrorResponse> handleDataAccessException(DataAccessException exception) {
        return apiError(CallScreenAdminApiErrorCode.PERSISTENCE_ERROR);
    }

    private StoreScope requireTenantAdminScope(UUID storeId) {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new CallScreenAdminApiException(CallScreenAdminApiErrorCode.UNAUTHENTICATED));
        if (!actor.roles().contains(TENANT_ADMIN) || !actor.hasPermission(TENANT_ADMIN_MANAGE)) {
            throw new CallScreenAdminApiException(CallScreenAdminApiErrorCode.FORBIDDEN);
        }
        if (actor.tenantId() == null || !actor.storeIds().contains(storeId)) {
            throw new CallScreenAdminApiException(CallScreenAdminApiErrorCode.STORE_SCOPE_MISMATCH);
        }
        return new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
    }

    private static ResponseEntity<CallScreenAdminApiErrorResponse> apiError(CallScreenAdminApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(CallScreenAdminApiErrorResponse.of(code));
    }

    private static CallScreenAdminApiErrorCode toApiError(CallScreenAdminServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> CallScreenAdminApiErrorCode.REQUEST_INVALID;
            case AD_SET_NOT_FOUND -> CallScreenAdminApiErrorCode.AD_SET_NOT_FOUND;
            case VERSION_CONFLICT -> CallScreenAdminApiErrorCode.VERSION_CONFLICT;
            case PERSISTENCE_ERROR -> CallScreenAdminApiErrorCode.PERSISTENCE_ERROR;
        };
    }

    private static CallScreenSettingsCommand toCommand(CallScreenAdminRequests.SettingsRequest request) {
        if (request == null) {
            return null;
        }
        return new CallScreenSettingsCommand(
            request.activeAdSetId(),
            request.adMode(),
            request.status(),
            request.slideDurationSeconds(),
            request.statePollSeconds(),
            request.showWaitingPreview(),
            request.version()
        );
    }

    private static CallScreenAdSetCommand toCommand(CallScreenAdminRequests.AdSetRequest request) {
        if (request == null) {
            return null;
        }
        List<CallScreenTextSlideCommand> slides = request.slides() == null ? List.of() : request.slides().stream()
            .map(CallScreenAdminController::toCommand)
            .toList();
        return new CallScreenAdSetCommand(request.name(), request.adType(), request.status(), slides, request.version());
    }

    private static CallScreenTextSlideCommand toCommand(CallScreenAdminRequests.TextSlideRequest request) {
        return new CallScreenTextSlideCommand(
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
