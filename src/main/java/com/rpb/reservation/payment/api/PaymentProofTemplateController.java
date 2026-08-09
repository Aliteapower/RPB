package com.rpb.reservation.payment.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentProofTemplateCommand;
import com.rpb.reservation.payment.application.PaymentProofTemplateService;
import com.rpb.reservation.payment.application.PaymentServiceErrorCode;
import com.rpb.reservation.payment.application.PaymentServiceException;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.io.IOException;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin/payment/proof-templates")
public class PaymentProofTemplateController {
    private static final String TEMPLATE_MANAGE_PERMISSION = "payment.proof_template.manage";

    private final PaymentProofTemplateService service;
    private final CurrentActorProvider currentActorProvider;

    public PaymentProofTemplateController(PaymentProofTemplateService service, CurrentActorProvider currentActorProvider) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    @RequireAppGate(appKey = "payment", permission = TEMPLATE_MANAGE_PERMISSION)
    public ResponseEntity<PaymentProofTemplateResponses.ListResponse> listTemplates(@PathVariable UUID storeId) {
        CurrentActor actor = requireActor(storeId);
        return ResponseEntity.ok(PaymentProofTemplateResponses.ListResponse.from(
            service.listTemplates(scope(actor, storeId), actor)
        ));
    }

    @PostMapping
    @RequireAppGate(appKey = "payment", permission = TEMPLATE_MANAGE_PERMISSION)
    public ResponseEntity<PaymentProofTemplateResponses.SingleResponse> createTemplate(
        @PathVariable UUID storeId,
        @RequestBody(required = false) PaymentProofTemplateRequest request
    ) {
        if (request == null) {
            throw new PaymentApiException(PaymentApiErrorCode.REQUEST_INVALID);
        }
        CurrentActor actor = requireActor(storeId);
        return ResponseEntity.ok(PaymentProofTemplateResponses.SingleResponse.from(
            service.createTenantTemplate(scope(actor, storeId), toCommand(request), actor)
        ));
    }

    @PatchMapping("/{templateId}")
    @RequireAppGate(appKey = "payment", permission = TEMPLATE_MANAGE_PERMISSION)
    public ResponseEntity<PaymentProofTemplateResponses.SingleResponse> updateTemplate(
        @PathVariable UUID storeId,
        @PathVariable UUID templateId,
        @RequestBody(required = false) PaymentProofTemplateRequest request
    ) {
        if (request == null) {
            throw new PaymentApiException(PaymentApiErrorCode.REQUEST_INVALID);
        }
        CurrentActor actor = requireActor(storeId);
        return ResponseEntity.ok(PaymentProofTemplateResponses.SingleResponse.from(
            service.updateTenantTemplate(scope(actor, storeId), templateId, toCommand(request), actor)
        ));
    }

    @PostMapping(value = "/test-scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireAppGate(appKey = "payment", permission = TEMPLATE_MANAGE_PERMISSION)
    public ResponseEntity<PaymentProofTemplateResponses.TestScanResponse> testScan(
        @PathVariable UUID storeId,
        @RequestParam("image") MultipartFile image
    ) {
        CurrentActor actor = requireActor(storeId);
        try {
            return ResponseEntity.ok(PaymentProofTemplateResponses.TestScanResponse.from(service.testScan(
                scope(actor, storeId),
                image.getOriginalFilename(),
                image.getContentType(),
                image.getBytes(),
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

    private static StoreScope scope(CurrentActor actor, UUID storeId) {
        return new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
    }

    private static PaymentProofTemplateCommand toCommand(PaymentProofTemplateRequest request) {
        return new PaymentProofTemplateCommand(
            request.bankCode(),
            request.bankName(),
            request.locale(),
            request.templateName(),
            request.status(),
            request.priority() == null ? 100 : request.priority(),
            request.layoutJson(),
            request.version() == null ? 0 : request.version()
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
