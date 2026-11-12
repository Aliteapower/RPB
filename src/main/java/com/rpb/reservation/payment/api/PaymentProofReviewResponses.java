package com.rpb.reservation.payment.api;

import com.rpb.reservation.payment.application.PaymentProofCandidate;
import com.rpb.reservation.payment.application.PaymentProofChecks;
import com.rpb.reservation.payment.application.PaymentProofOcrFields;
import com.rpb.reservation.payment.application.PaymentProofScanResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PaymentProofReviewResponses {
    private PaymentProofReviewResponses() {
    }

    public record CandidatesResponse(
        boolean success,
        LocalDate businessDate,
        List<CandidateResponse> candidates
    ) {
        public static CandidatesResponse from(LocalDate businessDate, List<PaymentProofCandidate> candidates) {
            return new CandidatesResponse(true, businessDate, candidates.stream().map(CandidateResponse::from).toList());
        }
    }

    public record CandidateResponse(
        UUID intentId,
        UUID sessionId,
        String intentNo,
        String sessionNo,
        int displayNumber,
        LocalDate businessDate,
        BigDecimal amount,
        String currency,
        String paymentReference,
        String intentStatus,
        String sessionStatus,
        String terminalCode,
        String cashierName,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
    ) {
        static CandidateResponse from(PaymentProofCandidate candidate) {
            return new CandidateResponse(
                candidate.intentId(),
                candidate.sessionId(),
                candidate.intentNo(),
                candidate.sessionNo(),
                candidate.displayNumber(),
                candidate.businessDate(),
                candidate.amount(),
                candidate.currency(),
                candidate.paymentReference(),
                candidate.intentStatus(),
                candidate.sessionStatus(),
                candidate.terminalCode(),
                candidate.cashierName(),
                candidate.createdAt(),
                candidate.expiresAt()
            );
        }
    }

    public record ScanResponse(
        boolean success,
        String outcome,
        boolean replayed,
        UUID intentId,
        UUID sessionId,
        UUID proofId,
        UUID verificationId,
        String paymentReference,
        BigDecimal expectedAmount,
        OcrResponse ocr,
        ChecksResponse checks
    ) {
        public static ScanResponse from(PaymentProofScanResult result) {
            return new ScanResponse(
                result.success(),
                result.outcome(),
                result.replayed(),
                result.intentId(),
                result.sessionId(),
                result.proofId(),
                result.verificationId(),
                result.paymentReference(),
                result.expectedAmount(),
                OcrResponse.from(result.ocr()),
                ChecksResponse.from(result.checks())
            );
        }
    }

    public record OcrResponse(
        String extractedReference,
        BigDecimal extractedAmount,
        OffsetDateTime extractedPaidAt,
        String bankCode,
        boolean successDetected,
        BigDecimal confidence
    ) {
        static OcrResponse from(PaymentProofOcrFields fields) {
            if (fields == null) {
                return null;
            }
            return new OcrResponse(
                fields.extractedReference(),
                fields.extractedAmount(),
                fields.extractedPaidAt(),
                fields.bankCode(),
                fields.successDetected(),
                fields.confidence()
            );
        }
    }

    public record ChecksResponse(
        String reference,
        String amount
    ) {
        static ChecksResponse from(PaymentProofChecks checks) {
            return checks == null ? null : new ChecksResponse(checks.reference(), checks.amount());
        }
    }
}
