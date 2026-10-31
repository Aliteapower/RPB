package com.rpb.reservation.payment.api;

import com.rpb.reservation.payment.application.PaymentProofTemplateCommand;
import com.rpb.reservation.payment.application.PaymentProofTemplateContributionReviewCommand;
import com.rpb.reservation.payment.application.PaymentProofTemplateService;
import com.rpb.reservation.payment.application.PaymentServiceErrorCode;
import com.rpb.reservation.payment.application.PaymentServiceException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/platform/payment")
public class PlatformPaymentProofTemplateController {
    private static final String PLATFORM_TEMPLATE_MANAGE_PERMISSION = "platform.payment_proof_template.manage";

    private final PaymentProofTemplateService service;
    private final CurrentActorProvider currentActorProvider;

    public PlatformPaymentProofTemplateController(
        PaymentProofTemplateService service,
        CurrentActorProvider currentActorProvider
    ) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping("/proof-templates")
    public ResponseEntity<PaymentProofTemplateResponses.ListResponse> listTemplates() {
        CurrentActor actor = requirePlatformActor();
        return ResponseEntity.ok(PaymentProofTemplateResponses.ListResponse.from(service.listPlatformTemplates(actor)));
    }

    @PostMapping("/proof-templates")
    public ResponseEntity<PaymentProofTemplateResponses.SingleResponse> createTemplate(
        @RequestBody(required = false) PaymentProofTemplateRequest request
    ) {
        CurrentActor actor = requirePlatformActor();
        if (request == null) {
            throw new PaymentApiException(PaymentApiErrorCode.REQUEST_INVALID);
        }
        return ResponseEntity.ok(PaymentProofTemplateResponses.SingleResponse.from(
            service.createPlatformTemplate(toCommand(request), actor)
        ));
    }

    @PatchMapping("/proof-templates/{templateId}")
    public ResponseEntity<PaymentProofTemplateResponses.SingleResponse> updateTemplate(
        @PathVariable UUID templateId,
        @RequestBody(required = false) PaymentProofTemplateRequest request
    ) {
        CurrentActor actor = requirePlatformActor();
        if (request == null || request.version() == null) {
            throw new PaymentApiException(PaymentApiErrorCode.REQUEST_INVALID);
        }
        return ResponseEntity.ok(PaymentProofTemplateResponses.SingleResponse.from(
            service.updatePlatformTemplate(templateId, toCommand(request), actor)
        ));
    }

    @PostMapping(value = "/proof-templates/rule-suggestions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentProofTemplateResponses.RuleSuggestionResponse> suggestRule(
        @RequestParam("image") MultipartFile image,
        @RequestParam("bankCode") String bankCode,
        @RequestParam("bankName") String bankName,
        @RequestParam(value = "locale", required = false) String locale
    ) {
        requirePlatformActor();
        try {
            return ResponseEntity.ok(PaymentProofTemplateResponses.RuleSuggestionResponse.from(service.suggestPlatformRule(
                image.getOriginalFilename(), image.getContentType(), image.getBytes(), bankCode, bankName, locale
            )));
        } catch (IOException exception) {
            throw new PaymentApiException(PaymentApiErrorCode.REQUEST_INVALID);
        }
    }

    @GetMapping("/proof-template-contributions")
    public ResponseEntity<PaymentProofTemplateResponses.ContributionListResponse> listContributions(
        @RequestParam(value = "status", required = false) String status
    ) {
        CurrentActor actor = requirePlatformActor();
        return ResponseEntity.ok(PaymentProofTemplateResponses.ContributionListResponse.from(
            service.listPlatformContributions(status, actor)
        ));
    }

    @PostMapping("/proof-template-contributions/{contributionId}/accept")
    public ResponseEntity<PaymentProofTemplateResponses.ContributionResponse> acceptContribution(
        @PathVariable UUID contributionId,
        @RequestBody(required = false) PaymentProofTemplateContributionReviewCommand command
    ) {
        CurrentActor actor = requirePlatformActor();
        if (command == null) {
            throw new PaymentApiException(PaymentApiErrorCode.REQUEST_INVALID);
        }
        return ResponseEntity.ok(PaymentProofTemplateResponses.ContributionResponse.from(
            service.acceptContribution(contributionId, command, actor)
        ));
    }

    @PostMapping("/proof-template-contributions/{contributionId}/reject")
    public ResponseEntity<PaymentProofTemplateResponses.ContributionResponse> rejectContribution(
        @PathVariable UUID contributionId,
        @RequestBody(required = false) PaymentProofTemplateContributionReviewCommand command
    ) {
        CurrentActor actor = requirePlatformActor();
        if (command == null) {
            throw new PaymentApiException(PaymentApiErrorCode.REQUEST_INVALID);
        }
        return ResponseEntity.ok(PaymentProofTemplateResponses.ContributionResponse.from(
            service.rejectContribution(contributionId, command, actor)
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

    private CurrentActor requirePlatformActor() {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new PaymentApiException(PaymentApiErrorCode.UNAUTHENTICATED));
        if (!actor.roles().contains("platform_admin") || !actor.hasPermission(PLATFORM_TEMPLATE_MANAGE_PERMISSION)) {
            throw new PaymentApiException(PaymentApiErrorCode.FORBIDDEN);
        }
        return actor;
    }

    private static PaymentProofTemplateCommand toCommand(PaymentProofTemplateRequest request) {
        return new PaymentProofTemplateCommand(
            request.bankCode(), request.bankName(), request.locale(), request.templateName(), request.status(),
            request.priority() == null ? 100 : request.priority(), request.layoutJson(), request.version() == null ? 0 : request.version()
        );
    }

    private static ResponseEntity<PaymentApiErrorResponse> apiError(PaymentApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(PaymentApiErrorResponse.of(code));
    }

    private static PaymentApiErrorCode toApiError(PaymentServiceErrorCode code) {
        return switch (code) {
            case VERSION_CONFLICT -> PaymentApiErrorCode.VERSION_CONFLICT;
            default -> PaymentApiErrorCode.REQUEST_INVALID;
        };
    }
}
