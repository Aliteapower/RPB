package com.rpb.reservation.auth.api;

import com.rpb.reservation.auth.application.AuthApplicationService;
import com.rpb.reservation.auth.application.AuthLoginResult;
import com.rpb.reservation.auth.application.AuthPrincipal;
import com.rpb.reservation.auth.security.AuthCookieService;
import com.rpb.reservation.common.web.HostPrefixContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthApplicationService authService;
    private final AuthCookieService cookieService;
    private final HostPrefixContextResolver hostPrefixContextResolver;

    public AuthController(
        AuthApplicationService authService,
        AuthCookieService cookieService,
        HostPrefixContextResolver hostPrefixContextResolver
    ) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.hostPrefixContextResolver = hostPrefixContextResolver;
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
        String token = cookieService.readSessionToken(request).orElseThrow(() ->
            new AuthApiException(AuthApiErrorCode.UNAUTHENTICATED)
        );
        return AuthStoreAccessResponse.from(authService.currentStores(token));
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

    private static String remoteAddr(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }
}
