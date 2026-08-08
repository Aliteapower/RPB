package com.rpb.reservation.payment.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentIntentCreateCommand;
import com.rpb.reservation.payment.application.PaymentIntentService;
import com.rpb.reservation.payment.application.PaymentServiceErrorCode;
import com.rpb.reservation.payment.application.PaymentServiceException;
import com.rpb.reservation.payment.application.QuickPayRecordQuery;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/payments/intents")
public class PaymentIntentController {
    private static final String INTENT_CREATE_PERMISSION = "payment.intent.create";
    private static final String INTENT_VIEW_PERMISSION = "payment.intent.view";

    private final PaymentIntentService service;
    private final CurrentActorProvider currentActorProvider;

    public PaymentIntentController(PaymentIntentService service, CurrentActorProvider currentActorProvider) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @PostMapping
    @RequireAppGate(appKey = "payment", permission = INTENT_CREATE_PERMISSION)
    public ResponseEntity<PaymentIntentResponses.CreateIntentResponse> createIntent(
        @PathVariable UUID storeId,
        @RequestBody(required = false) PaymentIntentRequests.CreateIntentRequest request
    ) {
        if (request == null) {
            throw new PaymentApiException(PaymentApiErrorCode.REQUEST_INVALID);
        }
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PaymentApiException(PaymentApiErrorCode.UNAUTHENTICATED));
        if (actor.tenantId() == null || !actor.canAccessStore(storeId)) {
            throw new PaymentApiException(PaymentApiErrorCode.FORBIDDEN);
        }
        StoreScope scope = new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
        return ResponseEntity.ok(PaymentIntentResponses.CreateIntentResponse.from(
            service.createQuickPay(scope, toCommand(request), actor)
        ));
    }

    @GetMapping("/sessions/{sessionNo}")
    @RequireAppGate(appKey = "payment", permission = INTENT_VIEW_PERMISSION)
    public ResponseEntity<PaymentIntentResponses.SessionResponse> getSession(
        @PathVariable UUID storeId,
        @PathVariable String sessionNo
    ) {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PaymentApiException(PaymentApiErrorCode.UNAUTHENTICATED));
        if (actor.tenantId() == null || !actor.canAccessStore(storeId)) {
            throw new PaymentApiException(PaymentApiErrorCode.FORBIDDEN);
        }
        StoreScope scope = new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
        return ResponseEntity.ok(PaymentIntentResponses.SessionResponse.from(
            service.findSessionByNo(scope, sessionNo, actor)
        ));
    }

    @GetMapping("/terminal-config")
    @RequireAppGate(appKey = "payment", permission = INTENT_CREATE_PERMISSION)
    public ResponseEntity<PaymentIntentResponses.TerminalConfigResponse> getTerminalConfig(@PathVariable UUID storeId) {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PaymentApiException(PaymentApiErrorCode.UNAUTHENTICATED));
        if (actor.tenantId() == null || !actor.canAccessStore(storeId)) {
            throw new PaymentApiException(PaymentApiErrorCode.FORBIDDEN);
        }
        StoreScope scope = new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
        return ResponseEntity.ok(PaymentIntentResponses.TerminalConfigResponse.from(
            service.findTerminalConfig(scope, actor)
        ));
    }

    @GetMapping("/quick-pay-records")
    @RequireAppGate(appKey = "payment", permission = INTENT_VIEW_PERMISSION)
    public ResponseEntity<PaymentIntentResponses.QuickPayRecordsResponse> getQuickPayRecords(
        @PathVariable UUID storeId,
        @RequestParam(required = false) java.time.LocalDate businessDate,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String terminalCode,
        @RequestParam(name = "q", required = false) String search,
        @RequestParam(required = false, defaultValue = "80") int limit
    ) {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PaymentApiException(PaymentApiErrorCode.UNAUTHENTICATED));
        if (actor.tenantId() == null || !actor.canAccessStore(storeId)) {
            throw new PaymentApiException(PaymentApiErrorCode.FORBIDDEN);
        }
        StoreScope scope = new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
        return ResponseEntity.ok(PaymentIntentResponses.QuickPayRecordsResponse.from(
            service.findQuickPayRecords(
                scope,
                new QuickPayRecordQuery(
                    businessDate,
                    status,
                    terminalCode,
                    search,
                    limit
                ),
                actor
            )
        ));
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

    private static PaymentIntentCreateCommand toCommand(PaymentIntentRequests.CreateIntentRequest request) {
        return new PaymentIntentCreateCommand(
            request.idempotencyKey(),
            request.sourceType(),
            request.sourceId(),
            request.method(),
            request.amount(),
            request.currency(),
            request.terminalCode(),
            request.cashierName(),
            request.requestedDisplayNumber(),
            request.metadataJson()
        );
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
            case PAYMENT_SESSION_NOT_FOUND -> PaymentApiErrorCode.PAYMENT_SESSION_NOT_FOUND;
            case PAYMENT_INTENT_NOT_FOUND -> PaymentApiErrorCode.REQUEST_INVALID;
            case PAYMENT_OCR_UNAVAILABLE -> PaymentApiErrorCode.PAYMENT_OCR_UNAVAILABLE;
            case PAYMENT_INTENT_STATE_CONFLICT -> PaymentApiErrorCode.PAYMENT_INTENT_STATE_CONFLICT;
            case IDEMPOTENCY_CONFLICT -> PaymentApiErrorCode.IDEMPOTENCY_CONFLICT;
            case VERSION_CONFLICT -> PaymentApiErrorCode.VERSION_CONFLICT;
        };
    }
}
