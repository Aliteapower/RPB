package com.rpb.reservation.payment.api;

import com.rpb.reservation.payment.application.PaymentIntent;
import com.rpb.reservation.payment.application.PaymentIntentCreateResult;
import com.rpb.reservation.payment.application.PaymentSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
}
