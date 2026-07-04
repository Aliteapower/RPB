package com.rpb.reservation.customerauth.api;

import com.rpb.reservation.customerauth.application.CustomerAuthApplicationService;
import com.rpb.reservation.customerauth.application.CustomerAuthEmailCodeResult;
import com.rpb.reservation.customerauth.application.CustomerAuthError;
import com.rpb.reservation.customerauth.application.CustomerAuthLoginResult;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.customerauth.application.CustomerAuthServiceException;
import com.rpb.reservation.publicbooking.application.PublicBookingStoreProfile;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/customer-auth")
public class CustomerAuthController {

    private final CustomerAuthApplicationService service;
    private final CustomerAuthCookieService cookieService;
    private final PublicBookingStoreRepositoryPort storeRepository;
    private final boolean exposeDevCode;

    public CustomerAuthController(
        CustomerAuthApplicationService service,
        CustomerAuthCookieService cookieService,
        PublicBookingStoreRepositoryPort storeRepository,
        @Value("${rpb.customer-auth.expose-dev-code:false}") boolean exposeDevCode
    ) {
        this.service = service;
        this.cookieService = cookieService;
        this.storeRepository = storeRepository;
        this.exposeDevCode = exposeDevCode;
    }

    @PostMapping("/email-code")
    public ResponseEntity<CustomerAuthEmailCodeResponse> requestEmailCode(
        @RequestBody EmailCodeRequest request,
        HttpServletRequest servletRequest
    ) {
        PublicBookingStoreProfile store = store(request == null ? null : request.storeId());
        CustomerAuthEmailCodeResult result = service.requestEmailCode(
            store.scope().tenantId().value(),
            store.scope().storeId().value(),
            request.email(),
            servletRequest.getRemoteAddr(),
            servletRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(CustomerAuthEmailCodeResponse.from(result, exposeDevCode));
    }

    @PostMapping("/email-login")
    public ResponseEntity<CustomerAuthLoginResponse> loginWithEmail(
        @RequestBody EmailLoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        PublicBookingStoreProfile store = store(request == null ? null : request.storeId());
        CustomerAuthLoginResult result = service.loginWithEmail(
            store.scope().tenantId().value(),
            store.scope().storeId().value(),
            request.email(),
            request.code(),
            request.displayName(),
            servletRequest.getRemoteAddr(),
            servletRequest.getHeader("User-Agent")
        );
        cookieService.writeSessionCookie(servletResponse, result.sessionToken(), result.expiresAt());
        return ResponseEntity.ok(CustomerAuthLoginResponse.from(result));
    }

    @PostMapping("/oauth-login")
    public ResponseEntity<CustomerAuthLoginResponse> loginWithProvider(
        @RequestBody OAuthLoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        PublicBookingStoreProfile store = store(request == null ? null : request.storeId());
        CustomerAuthLoginResult result = service.loginWithProvider(
            store.scope().tenantId().value(),
            store.scope().storeId().value(),
            request.provider(),
            request.token(),
            servletRequest.getRemoteAddr(),
            servletRequest.getHeader("User-Agent")
        );
        cookieService.writeSessionCookie(servletResponse, result.sessionToken(), result.expiresAt());
        return ResponseEntity.ok(CustomerAuthLoginResponse.from(result));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        Optional<CustomerAuthPrincipal> principal = cookieService.readSessionToken(request)
            .flatMap(service::authenticateSession);
        if (principal.isEmpty()) {
            return authError(CustomerAuthError.UNAUTHENTICATED);
        }
        return ResponseEntity.ok(new CustomerAuthMeResponse(true, PrincipalResponse.from(principal.get())));
    }

    @PostMapping("/logout")
    public ResponseEntity<CustomerAuthMeResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        cookieService.readSessionToken(request).ifPresent(service::logout);
        cookieService.clearSessionCookie(response);
        return ResponseEntity.ok(new CustomerAuthMeResponse(true, null));
    }

    @ExceptionHandler(CustomerAuthServiceException.class)
    public ResponseEntity<CustomerAuthErrorResponse> handleServiceException(CustomerAuthServiceException exception) {
        return authError(exception.error());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CustomerAuthErrorResponse> handleBadRequest(IllegalArgumentException exception) {
        return authError(CustomerAuthError.REQUEST_INVALID);
    }

    private PublicBookingStoreProfile store(UUID storeId) {
        if (storeId == null) {
            throw new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID);
        }
        return storeRepository.findActiveStoreProfileByStoreId(storeId)
            .orElseThrow(() -> new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID));
    }

    private static ResponseEntity<CustomerAuthErrorResponse> authError(CustomerAuthError error) {
        HttpStatus status = switch (error) {
            case ACCOUNT_DISABLED, UNAUTHENTICATED, PROVIDER_TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case EMAIL_CHANNEL_NOT_CONFIGURED, PROVIDER_NOT_CONFIGURED -> HttpStatus.CONFLICT;
            case EMAIL_DELIVERY_FAILED -> HttpStatus.BAD_GATEWAY;
            case PERSISTENCE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new CustomerAuthErrorResponse(false, error.name().toLowerCase()));
    }

    public record EmailCodeRequest(UUID storeId, String email) {
    }

    public record EmailLoginRequest(UUID storeId, String email, String code, String displayName) {
    }

    public record OAuthLoginRequest(
        UUID storeId,
        String provider,
        String token
    ) {
    }

    public record CustomerAuthEmailCodeResponse(
        boolean success,
        String email,
        Instant expiresAt,
        String devCode
    ) {
        static CustomerAuthEmailCodeResponse from(CustomerAuthEmailCodeResult result, boolean exposeDevCode) {
            return new CustomerAuthEmailCodeResponse(
                result.success(),
                result.email(),
                result.expiresAt(),
                exposeDevCode ? result.devCode() : null
            );
        }
    }

    public record CustomerAuthLoginResponse(
        boolean success,
        PrincipalResponse principal,
        Instant expiresAt
    ) {
        static CustomerAuthLoginResponse from(CustomerAuthLoginResult result) {
            return new CustomerAuthLoginResponse(true, PrincipalResponse.from(result.principal()), result.expiresAt());
        }
    }

    public record CustomerAuthMeResponse(boolean success, PrincipalResponse principal) {
    }

    public record PrincipalResponse(
        UUID tenantId,
        UUID customerId,
        String email,
        String displayName
    ) {
        static PrincipalResponse from(CustomerAuthPrincipal principal) {
            return new PrincipalResponse(
                principal.tenantId(),
                principal.customerId(),
                principal.email(),
                principal.displayName()
            );
        }
    }

    public record CustomerAuthErrorResponse(boolean success, String error) {
    }
}
