package com.rpb.reservation.payment.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentProofTemplateContributionCommand;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin/payment")
public class PaymentProofTemplateContributionController {
    private static final String TEMPLATE_MANAGE_PERMISSION = "payment.proof_template.manage";

    private final PaymentProofTemplateService service;
    private final CurrentActorProvider currentActorProvider;

    public PaymentProofTemplateContributionController(
        PaymentProofTemplateService service,
        CurrentActorProvider currentActorProvider
    ) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping("/proof-template-contributions")
    @RequireAppGate(appKey = "payment", permission = TEMPLATE_MANAGE_PERMISSION)
    public ResponseEntity<PaymentProofTemplateResponses.ContributionListResponse> list(@PathVariable UUID storeId) {
        CurrentActor actor = requireActor(storeId);
        return ResponseEntity.ok(PaymentProofTemplateResponses.ContributionListResponse.from(
            service.listTenantContributions(scope(actor, storeId), actor)
        ));
    }

    @PostMapping("/proof-template-contributions")
    @RequireAppGate(appKey = "payment", permission = TEMPLATE_MANAGE_PERMISSION)
    public ResponseEntity<PaymentProofTemplateResponses.ContributionResponse> submit(
        @PathVariable UUID storeId,
        @RequestBody(required = false) PaymentProofTemplateContributionRequest request
    ) {
        CurrentActor actor = requireActor(storeId);
        if (request == null) {
            throw new PaymentApiException(PaymentApiErrorCode.REQUEST_INVALID);
        }
        return ResponseEntity.ok(PaymentProofTemplateResponses.ContributionResponse.from(
            service.submitContribution(scope(actor, storeId), toCommand(request), actor)
        ));
    }

    @PostMapping(value = "/proof-template-rule-suggestions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireAppGate(appKey = "payment", permission = TEMPLATE_MANAGE_PERMISSION)
    public ResponseEntity<PaymentProofTemplateResponses.RuleSuggestionResponse> suggest(
        @PathVariable UUID storeId,
        @RequestParam("image") MultipartFile image,
        @RequestParam("bankCode") String bankCode,
        @RequestParam("bankName") String bankName,
        @RequestParam(value = "locale", required = false) String locale
    ) {
        CurrentActor actor = requireActor(storeId);
        try {
            return ResponseEntity.ok(PaymentProofTemplateResponses.RuleSuggestionResponse.from(service.suggestRule(
                scope(actor, storeId), image.getOriginalFilename(), image.getContentType(), image.getBytes(),
                bankCode, bankName, locale, actor
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
        return apiError(exception.code() == PaymentServiceErrorCode.VERSION_CONFLICT
            ? PaymentApiErrorCode.VERSION_CONFLICT
            : PaymentApiErrorCode.REQUEST_INVALID);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<PaymentApiErrorResponse> handleDataAccessException(DataAccessException exception) {
        return apiError(PaymentApiErrorCode.PERSISTENCE_ERROR);
    }

    private CurrentActor requireActor(UUID storeId) {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PaymentApiException(PaymentApiErrorCode.UNAUTHENTICATED));
        if (actor.tenantId() == null || !actor.canAccessStore(storeId) || !actor.hasPermission(TEMPLATE_MANAGE_PERMISSION)) {
            throw new PaymentApiException(PaymentApiErrorCode.FORBIDDEN);
        }
        return actor;
    }

    private static StoreScope scope(CurrentActor actor, UUID storeId) {
        return new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
    }

    private static PaymentProofTemplateContributionCommand toCommand(PaymentProofTemplateContributionRequest request) {
        return new PaymentProofTemplateContributionCommand(
            request.sourceTemplateId(), request.bankCode(), request.bankName(), request.locale(), request.templateName(),
            request.layoutJson(), request.sampleFileName(), request.sampleContentType(), request.sampleFileDigest(),
            request.sampleOcrReference(), request.sampleOcrAmount(), request.sampleRawText()
        );
    }

    private static ResponseEntity<PaymentApiErrorResponse> apiError(PaymentApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(PaymentApiErrorResponse.of(code));
    }
}
