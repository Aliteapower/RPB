package com.rpb.reservation.payment.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentBusinessDayService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/payments/business-day")
public class PaymentBusinessDayController {
    private static final String INTENT_CREATE_PERMISSION = "payment.intent.create";

    private final PaymentBusinessDayService service;
    private final CurrentActorProvider currentActorProvider;

    public PaymentBusinessDayController(PaymentBusinessDayService service, CurrentActorProvider currentActorProvider) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    @RequireAppGate(appKey = "payment", permission = INTENT_CREATE_PERMISSION)
    public ResponseEntity<PaymentBusinessDayResponse> getBusinessDay(@PathVariable UUID storeId) {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PaymentApiException(PaymentApiErrorCode.UNAUTHENTICATED));
        return ResponseEntity.ok(PaymentBusinessDayResponse.from(service.current(scope(storeId, actor), actor)));
    }

    @PostMapping
    @RequireAppGate(appKey = "payment", permission = INTENT_CREATE_PERMISSION)
    public ResponseEntity<PaymentBusinessDayResponse> openBusinessDay(
        @PathVariable UUID storeId,
        @RequestBody(required = false) PaymentBusinessDayRequest request
    ) {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PaymentApiException(PaymentApiErrorCode.UNAUTHENTICATED));
        return ResponseEntity.ok(PaymentBusinessDayResponse.from(service.openToday(scope(storeId, actor), actor)));
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

    private static StoreScope scope(UUID storeId, CurrentActor actor) {
        if (actor.tenantId() == null || !actor.canAccessStore(storeId)) {
            throw new PaymentApiException(PaymentApiErrorCode.FORBIDDEN);
        }
        return new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
    }

    private static ResponseEntity<PaymentApiErrorResponse> apiError(PaymentApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(PaymentApiErrorResponse.of(code));
    }

    private static PaymentApiErrorCode toApiError(PaymentServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> PaymentApiErrorCode.REQUEST_INVALID;
            case PAYMENT_PROFILE_INVALID -> PaymentApiErrorCode.REQUEST_INVALID;
            case PAYMENT_PROFILE_NOT_FOUND -> PaymentApiErrorCode.PAYMENT_PROFILE_NOT_FOUND;
            case PAYMENT_PROFILE_DISABLED -> PaymentApiErrorCode.PAYMENT_PROFILE_DISABLED;
            case PAYMENT_INTENT_NOT_FOUND -> PaymentApiErrorCode.REQUEST_INVALID;
            case PAYMENT_SESSION_NOT_FOUND -> PaymentApiErrorCode.PAYMENT_SESSION_NOT_FOUND;
            case PAYMENT_INTENT_STATE_CONFLICT -> PaymentApiErrorCode.REQUEST_INVALID;
            case IDEMPOTENCY_CONFLICT -> PaymentApiErrorCode.REQUEST_INVALID;
            case VERSION_CONFLICT -> PaymentApiErrorCode.VERSION_CONFLICT;
        };
    }
}
