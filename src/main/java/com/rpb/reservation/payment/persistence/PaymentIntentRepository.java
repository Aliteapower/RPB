package com.rpb.reservation.payment.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentIntentCreateResult;
import com.rpb.reservation.payment.application.PaymentIntentDraft;
import com.rpb.reservation.payment.application.PaymentSession;
import com.rpb.reservation.payment.application.PaymentSessionDraft;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

public interface PaymentIntentRepository {
    Optional<PaymentIntentCreateResult> findCreateResultByIdempotencyKey(StoreScope scope, String idempotencyKey);

    Optional<PaymentSession> findSessionByNo(StoreScope scope, String sessionNo);

    int nextIntentSequence(StoreScope scope, YearMonth period);

    int allocateDisplayNumber(StoreScope scope, LocalDate businessDate, Integer requestedDisplayNumber);

    PaymentIntentCreateResult createIntentWithSession(
        StoreScope scope,
        PaymentIntentDraft intent,
        PaymentSessionDraft session
    );
}
