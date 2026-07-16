package com.rpb.reservation.auth.api;

import com.rpb.reservation.auth.application.AuthApplicationService;
import com.rpb.reservation.auth.application.AuthLoginResult;
import com.rpb.reservation.auth.application.AuthPrincipal;
import com.rpb.reservation.auth.application.AuthStoreAccess;
import com.rpb.reservation.auth.security.AuthCookieService;
import com.rpb.reservation.common.web.HostPrefixContextResolver;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaService;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceErrorCode;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthApplicationService authService;
    private final AuthCookieService cookieService;
    private final HostPrefixContextResolver hostPrefixContextResolver;
    private final CallScreenMediaService mediaService;

    public AuthController(
        AuthApplicationService authService,
        AuthCookieService cookieService,
        HostPrefixContextResolver hostPrefixContextResolver,
        CallScreenMediaService mediaService
    ) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.hostPrefixContextResolver = hostPrefixContextResolver;
        this.mediaService = mediaService;
    }

    @PostMapping("/api/v1/auth/captcha/slider")
    public SliderCaptchaResponse createSliderCaptcha(HttpServletRequest request) {
        return authService.createSliderCaptcha(remoteAddr(request), userAgent(request));
    }

    @PostMapping("/api/v1/auth/login")
    public AuthLoginResponse login(
        @RequestBody(required = false) LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse response
    ) {
        AuthLoginResult result = authService.login(
            request,
            hostPrefixContextResolver.resolve(httpRequest),
            remoteAddr(httpRequest),
            userAgent(httpRequest)
        );
        cookieService.writeSessionCookie(response, result.sessionToken(), result.expiresAt());
        return new AuthLoginResponse(true, AuthUserResponse.from(result.principal()), result.expiresAt());
    }

    @GetMapping("/api/v1/auth/me")
    public AuthMeResponse me(HttpServletRequest request) {
        String token = cookieService.readSessionToken(request).orElseThrow(() ->
            new AuthApiException(AuthApiErrorCode.UNAUTHENTICATED)
        );
        AuthPrincipal principal = authService.currentUser(token);
        return new AuthMeResponse(true, AuthUserResponse.from(principal));
    }

    @GetMapping("/api/v1/me/stores")
    public AuthStoreAccessResponse stores(HttpServletRequest request) {
        String token = requireSessionToken(request);
        return AuthStoreAccessResponse.from(authService.currentStores(token));
    }

    @GetMapping("/api/v1/me/stores/{storeId}/logo/media/{assetId}")
    public ResponseEntity<Resource> storeLogoMedia(
        @PathVariable UUID storeId,
        @PathVariable UUID assetId,
        HttpServletRequest request
    ) {
        AuthStoreAccess store = authService.currentStoreLogoAccess(requireSessionToken(request), storeId, assetId);
        return mediaResponse(mediaService.readTenantLogoMedia(store.tenantId(), assetId));
    }

    @PostMapping("/api/v1/auth/logout")
    public AuthLogoutResponse logout(HttpServletRequest request, HttpServletResponse response) {
        cookieService.readSessionToken(request).ifPresent(authService::logout);
        cookieService.clearSessionCookie(response);
        return AuthLogoutResponse.ok();
    }

    @ExceptionHandler(AuthApiException.class)
    public ResponseEntity<AuthApiErrorResponse> handleAuthException(AuthApiException exception) {
        return ResponseEntity
            .status(exception.code().httpStatus())
            .body(AuthApiErrorResponse.of(exception.code(), exception.details()));
    }

    @ExceptionHandler(CallScreenMediaServiceException.class)
    public ResponseEntity<AuthApiErrorResponse> handleMediaException(CallScreenMediaServiceException exception) {
        AuthApiErrorCode code = toApiError(exception.code());
        return ResponseEntity.status(code.httpStatus()).body(AuthApiErrorResponse.of(code, java.util.Map.of()));
    }

    private String requireSessionToken(HttpServletRequest request) {
        return cookieService.readSessionToken(request).orElseThrow(() ->
            new AuthApiException(AuthApiErrorCode.UNAUTHENTICATED)
        );
    }

    private static ResponseEntity<Resource> mediaResponse(CallScreenMediaContent content) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType()))
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
            .header("X-Content-Type-Options", "nosniff")
            .body(content.resource());
    }

    private static AuthApiErrorCode toApiError(CallScreenMediaServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> AuthApiErrorCode.REQUEST_INVALID;
            case MEDIA_NOT_FOUND -> AuthApiErrorCode.MEDIA_NOT_FOUND;
            case PERSISTENCE_ERROR -> AuthApiErrorCode.PERSISTENCE_ERROR;
        };
    }

    private static String remoteAddr(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }
}
