package com.rpb.reservation.payment.api;

import com.rpb.reservation.payment.application.PaymentIntent;
import com.rpb.reservation.payment.application.PaymentIntentCreateResult;
import com.rpb.reservation.payment.application.PaymentQuickPayConfig;
import com.rpb.reservation.payment.application.PaymentSession;
import com.rpb.reservation.payment.application.QuickPayRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PaymentIntentResponses {
    private PaymentIntentResponses() {
    }

    public record CreateIntentResponse(
        boolean success,
        boolean replayed,
        IntentResponse intent,
        SessionResponse session,
        int nextDisplayNumber
    ) {
        public static CreateIntentResponse from(PaymentIntentCreateResult result) {
            return new CreateIntentResponse(
                result.success(),
                result.replayed(),
                IntentResponse.from(result.intent()),
                SessionResponse.from(result.session()),
                result.nextDisplayNumber()
            );
        }
    }

    public record IntentResponse(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String intentNo,
        String sourceType,
        UUID sourceId,
        String method,
        BigDecimal amount,
        String currency,
        String paymentReference,
        String status,
        OffsetDateTime expiresAt,
        int version
    ) {
        public static IntentResponse from(PaymentIntent intent) {
            return new IntentResponse(
                intent.id(),
                intent.tenantId(),
                intent.storeId(),
                intent.intentNo(),
                intent.sourceType(),
                intent.sourceId(),
                intent.method(),
                intent.amount(),
                intent.currency(),
                intent.paymentReference(),
                intent.status(),
                intent.expiresAt(),
                intent.version()
            );
        }
    }

    public record SessionResponse(
        UUID id,
        UUID tenantId,
        UUID storeId,
        UUID intentId,
        String sessionNo,
        int displayNumber,
        LocalDate businessDate,
        String status,
        String qrPayloadsJson,
        OffsetDateTime expiresAt,
        int version
    ) {
        public static SessionResponse from(PaymentSession session) {
            return new SessionResponse(
                session.id(),
                session.tenantId(),
                session.storeId(),
                session.intentId(),
                session.sessionNo(),
                session.displayNumber(),
                session.businessDate(),
                session.status(),
                session.qrPayloadsJson(),
                session.expiresAt(),
                session.version()
            );
        }
    }

    public record TerminalConfigResponse(
        boolean success,
        TerminalConfig terminalConfig
    ) {
        public static TerminalConfigResponse from(PaymentQuickPayConfig config) {
            return new TerminalConfigResponse(true, TerminalConfig.from(config));
        }
    }

    public record TerminalConfig(
        String referencePrefix,
        int dailyStartNumber,
        List<String> presetAmounts
    ) {
        static TerminalConfig from(PaymentQuickPayConfig config) {
            return new TerminalConfig(
                config.referencePrefix(),
                config.dailyStartNumber(),
                config.presetAmounts().stream().map(PaymentIntentResponses::amountText).toList()
            );
        }
    }

    public record QuickPayRecordsResponse(
        boolean success,
        List<QuickPayRecordResponse> records,
        QuickPayRecordSummary summary
    ) {
        public static QuickPayRecordsResponse from(List<QuickPayRecord> records) {
            List<QuickPayRecordResponse> rows = records.stream().map(QuickPayRecordResponse::from).toList();
            BigDecimal totalAmount = records.stream()
                .map(QuickPayRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paidAmount = records.stream()
                .filter(record -> "paid".equals(record.intentStatus()))
                .map(QuickPayRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            long pendingCount = records.stream().filter(record -> "pending".equals(record.intentStatus())).count();
            long paidCount = records.stream().filter(record -> "paid".equals(record.intentStatus())).count();
            return new QuickPayRecordsResponse(
                true,
                rows,
                new QuickPayRecordSummary(records.size(), pendingCount, paidCount, amountText(totalAmount), amountText(paidAmount), "SGD")
            );
        }
    }

    public record QuickPayRecordResponse(
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
        static QuickPayRecordResponse from(QuickPayRecord record) {
            return new QuickPayRecordResponse(
                record.intentId(),
                record.sessionId(),
                record.intentNo(),
                record.sessionNo(),
                record.displayNumber(),
                record.businessDate(),
                record.amount(),
                record.currency(),
                record.paymentReference(),
                record.intentStatus(),
                record.sessionStatus(),
                record.terminalCode(),
                record.cashierName(),
                record.createdAt(),
                record.expiresAt()
            );
        }
    }

    public record QuickPayRecordSummary(
        int count,
        long pendingCount,
        long paidCount,
        String totalAmount,
        String paidAmount,
        String currency
    ) {
    }

    private static String amountText(BigDecimal amount) {
        BigDecimal normalized = amount.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
    }
}
