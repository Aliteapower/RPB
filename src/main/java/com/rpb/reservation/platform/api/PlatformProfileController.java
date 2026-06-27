package com.rpb.reservation.platform.api;

import com.rpb.reservation.platform.application.PlatformProfileMutationCommand;
import com.rpb.reservation.platform.application.PlatformProfileService;
import com.rpb.reservation.platform.application.PlatformProfileServiceErrorCode;
import com.rpb.reservation.platform.application.PlatformProfileServiceException;
import com.rpb.reservation.platform.application.PlatformSocialLinkMutationCommand;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceErrorCode;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.UUID;
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
@RequestMapping("/api/v1/platform/profile")
public class PlatformProfileController {
    private static final String PLATFORM_ADMIN = "platform_admin";
    private static final String PLATFORM_TENANT_MANAGE = "platform.tenant.manage";

    private final PlatformProfileService service;
    private final CurrentActorProvider currentActorProvider;

    public PlatformProfileController(PlatformProfileService service, CurrentActorProvider currentActorProvider) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    public ResponseEntity<PlatformProfileResponse> getProfile() {
        requirePlatformProfileManager();
        return ResponseEntity.ok(PlatformProfileResponse.from(service.getProfile()));
    }

    @PatchMapping
    public ResponseEntity<PlatformProfileResponse> updateProfile(
        @RequestBody(required = false) PlatformProfileRequests.ProfileRequest request
    ) {
        requirePlatformProfileManager();
        return ResponseEntity.ok(PlatformProfileResponse.from(service.updateProfile(toCommand(request))));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlatformProfileResponse> uploadProfileLogo(@RequestParam("file") MultipartFile file) {
        requirePlatformProfileManager();
        return ResponseEntity.ok(PlatformProfileResponse.from(service.uploadProfileLogo(file)));
    }

    @DeleteMapping("/logo")
    public ResponseEntity<PlatformProfileResponse> clearProfileLogo() {
        requirePlatformProfileManager();
        return ResponseEntity.ok(PlatformProfileResponse.from(service.clearProfileLogo()));
    }

    @PostMapping("/social-links")
    public ResponseEntity<PlatformProfileResponse> createSocialLink(
        @RequestBody(required = false) PlatformProfileRequests.SocialLinkRequest request
    ) {
        requirePlatformProfileManager();
        return ResponseEntity.status(201).body(PlatformProfileResponse.from(service.createSocialLink(toCommand(request))));
    }

    @PatchMapping("/social-links/{linkId}")
    public ResponseEntity<PlatformProfileResponse> updateSocialLink(
        @PathVariable UUID linkId,
        @RequestBody(required = false) PlatformProfileRequests.SocialLinkRequest request
    ) {
        requirePlatformProfileManager();
        return ResponseEntity.ok(PlatformProfileResponse.from(service.updateSocialLink(linkId, toCommand(request))));
    }

    @DeleteMapping("/social-links/{linkId}")
    public ResponseEntity<PlatformProfileResponse> deleteSocialLink(@PathVariable UUID linkId) {
        requirePlatformProfileManager();
        return ResponseEntity.ok(PlatformProfileResponse.from(service.deleteSocialLink(linkId)));
    }

    @PostMapping(value = "/social-links/{linkId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlatformProfileResponse> uploadSocialLinkLogo(
        @PathVariable UUID linkId,
        @RequestParam("file") MultipartFile file
    ) {
        requirePlatformProfileManager();
        return ResponseEntity.ok(PlatformProfileResponse.from(service.uploadSocialLinkLogo(linkId, file)));
    }

    @DeleteMapping("/social-links/{linkId}/logo")
    public ResponseEntity<PlatformProfileResponse> clearSocialLinkLogo(@PathVariable UUID linkId) {
        requirePlatformProfileManager();
        return ResponseEntity.ok(PlatformProfileResponse.from(service.clearSocialLinkLogo(linkId)));
    }

    @ExceptionHandler(PlatformProfileApiException.class)
    public ResponseEntity<PlatformProfileApiErrorResponse> handleApiException(PlatformProfileApiException exception) {
        return apiError(exception.code());
    }

    @ExceptionHandler(PlatformProfileServiceException.class)
    public ResponseEntity<PlatformProfileApiErrorResponse> handleServiceException(PlatformProfileServiceException exception) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(CallScreenMediaServiceException.class)
    public ResponseEntity<PlatformProfileApiErrorResponse> handleMediaServiceException(CallScreenMediaServiceException exception) {
        return apiError(toApiError(exception.code()));
    }

    private CurrentActor requirePlatformProfileManager() {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PlatformProfileApiException(PlatformProfileApiErrorCode.UNAUTHENTICATED));
        if (!actor.roles().contains(PLATFORM_ADMIN) || !actor.hasPermission(PLATFORM_TENANT_MANAGE)) {
            throw new PlatformProfileApiException(PlatformProfileApiErrorCode.FORBIDDEN);
        }
        return actor;
    }

    private static ResponseEntity<PlatformProfileApiErrorResponse> apiError(PlatformProfileApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(PlatformProfileApiErrorResponse.of(code));
    }

    private static PlatformProfileApiErrorCode toApiError(PlatformProfileServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> PlatformProfileApiErrorCode.REQUEST_INVALID;
            case PROFILE_NOT_FOUND -> PlatformProfileApiErrorCode.PROFILE_NOT_FOUND;
            case SOCIAL_LINK_NOT_FOUND -> PlatformProfileApiErrorCode.SOCIAL_LINK_NOT_FOUND;
            case PERSISTENCE_ERROR -> PlatformProfileApiErrorCode.PERSISTENCE_ERROR;
        };
    }

    private static PlatformProfileApiErrorCode toApiError(CallScreenMediaServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> PlatformProfileApiErrorCode.REQUEST_INVALID;
            case MEDIA_NOT_FOUND -> PlatformProfileApiErrorCode.MEDIA_NOT_FOUND;
            case PERSISTENCE_ERROR -> PlatformProfileApiErrorCode.PERSISTENCE_ERROR;
        };
    }

    private static PlatformProfileMutationCommand toCommand(PlatformProfileRequests.ProfileRequest request) {
        if (request == null) {
            return null;
        }
        return new PlatformProfileMutationCommand(
            request.platformName(),
            request.uen(),
            request.address(),
            request.phone(),
            request.email(),
            request.website()
        );
    }

    private static PlatformSocialLinkMutationCommand toCommand(PlatformProfileRequests.SocialLinkRequest request) {
        if (request == null) {
            return null;
        }
        return new PlatformSocialLinkMutationCommand(
            request.displayName(),
            request.url(),
            request.sortOrder(),
            request.status()
        );
    }
}
