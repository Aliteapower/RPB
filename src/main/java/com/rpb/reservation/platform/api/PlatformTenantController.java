package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformTenant;
import com.rpb.reservation.platform.application.PlatformTenantMutationCommand;
import com.rpb.reservation.platform.application.PlatformOperator;
import com.rpb.reservation.platform.application.PlatformTenantSearchCommand;
import com.rpb.reservation.platform.application.PlatformTenantService;
import com.rpb.reservation.platform.application.PlatformTenantServiceException;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceErrorCode;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantController {
    private static final String PLATFORM_ADMIN = "platform_admin";
    private static final String TENANT_MANAGE_PERMISSION = "platform.tenant.manage";

    private final PlatformTenantService tenantService;
    private final CurrentActorProvider currentActorProvider;

    public PlatformTenantController(
        PlatformTenantService tenantService,
        CurrentActorProvider currentActorProvider
    ) {
        this.tenantService = tenantService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    public ResponseEntity<PlatformTenantListResponse> listTenants(
        @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "limit", required = false) String limit,
        @RequestParam(value = "offset", required = false) String offset
    ) {
        requirePlatformAdmin();
        return ResponseEntity.ok(PlatformTenantListResponse.from(
            tenantService.listTenants(new PlatformTenantSearchCommand(keyword, status, includeDeleted, limit, offset))
        ));
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<PlatformTenantResponse> getTenant(@PathVariable UUID tenantId) {
        requirePlatformAdmin();
        return ResponseEntity.ok(PlatformTenantResponse.from(tenantService.getTenant(tenantId)));
    }

    @GetMapping("/{tenantId}/admin-store-access")
    public ResponseEntity<PlatformTenantAdminStoreAccessResponse> getTenantAdminStoreAccess(
        @PathVariable UUID tenantId
    ) {
        requirePlatformAdmin();
        return ResponseEntity.ok(PlatformTenantAdminStoreAccessResponse.from(
            tenantService.getTenantAdminStoreAccess(tenantId)
        ));
    }

    @PostMapping
    public ResponseEntity<PlatformTenantResponse> createTenant(
        @RequestBody(required = false) PlatformTenantMutationRequest request
    ) {
        CurrentActor actor = requirePlatformAdmin();
        PlatformTenant tenant = tenantService.createTenant(toCommand(request), toOperator(actor));
        return ResponseEntity.status(201).body(PlatformTenantResponse.from(tenant));
    }

    @PatchMapping("/{tenantId}")
    public ResponseEntity<PlatformTenantResponse> updateTenant(
        @PathVariable UUID tenantId,
        @RequestBody(required = false) PlatformTenantMutationRequest request
    ) {
        CurrentActor actor = requirePlatformAdmin();
        return ResponseEntity.ok(PlatformTenantResponse.from(
            tenantService.updateTenant(tenantId, toCommand(request), toOperator(actor))
        ));
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<PlatformTenantResponse> deleteTenant(@PathVariable UUID tenantId) {
        CurrentActor actor = requirePlatformAdmin();
        return ResponseEntity.ok(PlatformTenantResponse.from(tenantService.deleteTenant(tenantId, toOperator(actor))));
    }

    @PostMapping("/{tenantId}/restore")
    public ResponseEntity<PlatformTenantResponse> restoreTenant(@PathVariable UUID tenantId) {
        CurrentActor actor = requirePlatformAdmin();
        return ResponseEntity.ok(PlatformTenantResponse.from(tenantService.restoreTenant(tenantId, toOperator(actor))));
    }

    @PostMapping(value = "/{tenantId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlatformTenantResponse> uploadTenantLogo(
        @PathVariable UUID tenantId,
        @RequestParam("file") MultipartFile file
    ) {
        CurrentActor actor = requirePlatformAdmin();
        return ResponseEntity.ok(PlatformTenantResponse.from(tenantService.uploadTenantLogo(tenantId, file, toOperator(actor))));
    }

    @DeleteMapping("/{tenantId}/logo")
    public ResponseEntity<PlatformTenantResponse> clearTenantLogo(@PathVariable UUID tenantId) {
        CurrentActor actor = requirePlatformAdmin();
        return ResponseEntity.ok(PlatformTenantResponse.from(tenantService.clearTenantLogo(tenantId, toOperator(actor))));
    }

    @GetMapping("/{tenantId}/logo/media/{assetId}")
    public ResponseEntity<Resource> readTenantLogoMedia(@PathVariable UUID tenantId, @PathVariable UUID assetId) {
        requirePlatformAdmin();
        CallScreenMediaContent content = tenantService.readTenantLogoMedia(tenantId, assetId);
        return mediaResponse(content);
    }

    @ExceptionHandler(PlatformTenantApiException.class)
    public ResponseEntity<PlatformTenantApiErrorResponse> handlePlatformTenantException(
        PlatformTenantApiException exception
    ) {
        return apiError(exception.code());
    }

    @ExceptionHandler(PlatformTenantServiceException.class)
    public ResponseEntity<PlatformTenantApiErrorResponse> handlePlatformTenantServiceException(
        PlatformTenantServiceException exception
    ) {
        return apiError(PlatformTenantApiErrorCode.valueOf(exception.code().name()));
    }

    @ExceptionHandler(CallScreenMediaServiceException.class)
    public ResponseEntity<PlatformTenantApiErrorResponse> handleMediaServiceException(
        CallScreenMediaServiceException exception
    ) {
        return apiError(toApiError(exception.code()));
    }

    private CurrentActor requirePlatformAdmin() {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PlatformTenantApiException(PlatformTenantApiErrorCode.UNAUTHENTICATED));
        if (!actor.roles().contains(PLATFORM_ADMIN) || !actor.hasPermission(TENANT_MANAGE_PERMISSION)) {
            throw new PlatformTenantApiException(PlatformTenantApiErrorCode.FORBIDDEN);
        }
        return actor;
    }

    private static ResponseEntity<PlatformTenantApiErrorResponse> apiError(PlatformTenantApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(PlatformTenantApiErrorResponse.of(code));
    }

    private static ResponseEntity<Resource> mediaResponse(CallScreenMediaContent content) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType()))
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
            .header("X-Content-Type-Options", "nosniff")
            .body(content.resource());
    }

    private static PlatformTenantApiErrorCode toApiError(CallScreenMediaServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> PlatformTenantApiErrorCode.REQUEST_INVALID;
            case MEDIA_NOT_FOUND -> PlatformTenantApiErrorCode.MEDIA_NOT_FOUND;
            case PERSISTENCE_ERROR -> PlatformTenantApiErrorCode.PERSISTENCE_ERROR;
        };
    }

    private static PlatformTenantMutationCommand toCommand(PlatformTenantMutationRequest request) {
        if (request == null) {
            return null;
        }
        return new PlatformTenantMutationCommand(
            request.tenantCode(),
            request.displayName(),
            request.status(),
            request.defaultLocale(),
            request.contactPhone(),
            request.address(),
            request.principalName(),
            request.initialPassword(),
            request.password(),
            request.adminStoreIds(),
            request.defaultAdminStoreId()
        );
    }

    private static PlatformOperator toOperator(CurrentActor actor) {
        return new PlatformOperator(actor.actorId(), actor.actorType());
    }
}
