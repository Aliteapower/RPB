package com.rpb.reservation.payment.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentProofScanCommand;
import com.rpb.reservation.payment.application.PaymentProofReviewService;
import com.rpb.reservation.payment.application.PaymentServiceErrorCode;
import com.rpb.reservation.payment.application.PaymentServiceException;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/payments/proof-review")
public class PaymentProofReviewController {
    private static final String PAYMENT_PROOF_REVIEW_PERMISSION = "payment.proof.review";

    private final PaymentProofReviewService service;
    private final CurrentActorProvider currentActorProvider;

    public PaymentProofReviewController(PaymentProofReviewService service, CurrentActorProvider currentActorProvider) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping("/candidates")
    @RequireAppGate(appKey = "payment", permission = PAYMENT_PROOF_REVIEW_PERMISSION)
    public ResponseEntity<PaymentProofReviewResponses.CandidatesResponse> getCandidates(
        @PathVariable UUID storeId,
        @RequestParam(required = false) LocalDate businessDate,
        @RequestParam(required = false) String terminalCode,
        @RequestParam(required = false, defaultValue = "80") int limit
    ) {
        CurrentActor actor = requireActor(storeId);
        StoreScope scope = new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
        return ResponseEntity.ok(PaymentProofReviewResponses.CandidatesResponse.from(
            businessDate,
            service.findCandidates(scope, businessDate, terminalCode, limit, actor)
        ));
    }

    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireAppGate(appKey = "payment", permission = PAYMENT_PROOF_REVIEW_PERMISSION)
    public ResponseEntity<PaymentProofReviewResponses.ScanResponse> scanProof(
        @PathVariable UUID storeId,
        @RequestParam("image") MultipartFile image,
        @RequestParam String idempotencyKey,
        @RequestParam(required = false) LocalDate businessDate,
        @RequestParam(required = false) String terminalCode
    ) {
        CurrentActor actor = requireActor(storeId);
        StoreScope scope = new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
        try {
            return ResponseEntity.ok(PaymentProofReviewResponses.ScanResponse.from(service.scanAndMatch(
                scope,
                new PaymentProofScanCommand(
                    idempotencyKey,
                    image.getOriginalFilename(),
                    image.getContentType(),
                    image.getBytes(),
                    businessDate,
                    terminalCode
                ),
                actor
            )));
        } catch (IOException exception) {
            throw new PaymentApiException(PaymentApiErrorCode.REQUEST_INVALID);
        }
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

    private CurrentActor requireActor(UUID storeId) {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PaymentApiException(PaymentApiErrorCode.UNAUTHENTICATED));
        if (actor.tenantId() == null || !actor.canAccessStore(storeId)) {
            throw new PaymentApiException(PaymentApiErrorCode.FORBIDDEN);
        }
        return actor;
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
