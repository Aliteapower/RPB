package com.rpb.reservation.payment.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentMethodProfileCommand;
import com.rpb.reservation.payment.application.PaymentMethodProfileService;
import com.rpb.reservation.payment.application.PaymentServiceErrorCode;
import com.rpb.reservation.payment.application.PaymentServiceException;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin/payment/profile")
public class PaymentProfileController {
    private static final String SETTINGS_MANAGE_PERMISSION = "payment.settings.manage";

    private final PaymentMethodProfileService service;
    private final CurrentActorProvider currentActorProvider;

    public PaymentProfileController(PaymentMethodProfileService service, CurrentActorProvider currentActorProvider) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    @RequireAppGate(appKey = "payment", permission = SETTINGS_MANAGE_PERMISSION)
    public ResponseEntity<PaymentProfileResponse> getProfile(@PathVariable UUID storeId) {
        StoreScope scope = requireStoreScope(storeId);
        return service.findEffectiveProfile(scope)
            .map(PaymentProfileResponse::from)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new PaymentApiException(PaymentApiErrorCode.PAYMENT_PROFILE_NOT_FOUND));
    }

    @PatchMapping
    @RequireAppGate(appKey = "payment", permission = SETTINGS_MANAGE_PERMISSION)
    public ResponseEntity<PaymentProfileResponse> updateProfile(
        @PathVariable UUID storeId,
        @RequestBody(required = false) PaymentProfileRequest request
    ) {
        if (request == null) {
            throw new PaymentApiException(PaymentApiErrorCode.REQUEST_INVALID);
        }
        StoreScope scope = requireStoreScope(storeId);
        return ResponseEntity.ok(PaymentProfileResponse.from(service.updateProfile(scope, toCommand(request))));
    }

    @ExceptionHandler(PaymentApiException.class)
    public ResponseEntity<PaymentApiErrorResponse> handleApiException(PaymentApiException exception) {
        return apiError(exception.code());
    }

    @ExceptionHandler(PaymentServiceException.class)
    public ResponseEntity<PaymentApiErrorResponse> handleServiceException(PaymentServiceException exception) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<PaymentApiErrorResponse> handleDataAccessException(DataAccessException exception) {
        return apiError(PaymentApiErrorCode.PERSISTENCE_ERROR);
    }

    private StoreScope requireStoreScope(UUID storeId) {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PaymentApiException(PaymentApiErrorCode.UNAUTHENTICATED));
        if (actor.tenantId() == null || !actor.canAccessStore(storeId)) {
            throw new PaymentApiException(PaymentApiErrorCode.FORBIDDEN);
        }
        return new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
    }

    private static PaymentMethodProfileCommand toCommand(PaymentProfileRequest request) {
        return new PaymentMethodProfileCommand(
            request.method(),
            request.status(),
            request.paynowType(),
            request.paynowMobile(),
            request.paynowUen(),
            request.merchantName(),
            request.currency(),
            request.configJson(),
            request.version() == null ? 0 : request.version()
        );
    }

    private static ResponseEntity<PaymentApiErrorResponse> apiError(PaymentApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(PaymentApiErrorResponse.of(code));
    }

    private static PaymentApiErrorCode toApiError(PaymentServiceErrorCode code) {
        return switch (code) {
            case PAYMENT_PROFILE_INVALID -> PaymentApiErrorCode.REQUEST_INVALID;
            case PAYMENT_PROFILE_NOT_FOUND -> PaymentApiErrorCode.PAYMENT_PROFILE_NOT_FOUND;
            case PAYMENT_PROFILE_DISABLED -> PaymentApiErrorCode.PAYMENT_PROFILE_DISABLED;
            case VERSION_CONFLICT -> PaymentApiErrorCode.VERSION_CONFLICT;
        };
    }
}
