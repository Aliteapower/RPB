package com.rpb.reservation.payment.api;

import com.rpb.reservation.payment.application.PaymentProofOcrFields;
import com.rpb.reservation.payment.application.PaymentProofTemplate;
import com.rpb.reservation.payment.application.PaymentProofTemplateTestScanResult;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PaymentProofTemplateResponses {
    private PaymentProofTemplateResponses() {
    }

    public record ListResponse(
        boolean success,
        List<TemplateResponse> templates
    ) {
        public static ListResponse from(List<PaymentProofTemplate> templates) {
            return new ListResponse(true, templates.stream().map(TemplateResponse::from).toList());
        }
    }

    public record SingleResponse(
        boolean success,
        TemplateResponse template
    ) {
        public static SingleResponse from(PaymentProofTemplate template) {
            return new SingleResponse(true, TemplateResponse.from(template));
        }
    }

    public record TestScanResponse(
        boolean success,
        TemplateResponse template,
        OcrResponse ocr
    ) {
        public static TestScanResponse from(PaymentProofTemplateTestScanResult result) {
            return new TestScanResponse(
                true,
                result.template() == null ? null : TemplateResponse.from(result.template()),
                OcrResponse.from(result.ocr())
            );
        }
    }

    public record TemplateResponse(
        UUID id,
        UUID tenantId,
        String bankCode,
        String bankName,
        String locale,
        String templateName,
        String source,
        String status,
        int priority,
        int version,
        String layoutJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        static TemplateResponse from(PaymentProofTemplate template) {
            return new TemplateResponse(
                template.id(),
                template.tenantId(),
                template.bankCode(),
                template.bankName(),
                template.locale(),
                template.templateName(),
                template.source(),
                template.status(),
                template.priority(),
                template.version(),
                template.layoutJson(),
                template.createdAt(),
                template.updatedAt()
            );
        }
    }

    public record OcrResponse(
        String extractedReference,
        BigDecimal extractedAmount,
        String bankCode,
        boolean successDetected,
        BigDecimal confidence,
        String rawText
    ) {
        static OcrResponse from(PaymentProofOcrFields fields) {
            if (fields == null) {
                return null;
            }
            return new OcrResponse(
                fields.extractedReference(),
                fields.extractedAmount(),
                fields.bankCode(),
                fields.successDetected(),
                fields.confidence(),
                fields.rawText()
            );
        }
    }
}
