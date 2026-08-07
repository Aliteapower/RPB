package com.rpb.reservation.payment.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentBusinessDay;
import com.rpb.reservation.payment.application.PaymentIntentCreateResult;
import com.rpb.reservation.payment.application.PaymentIntentDraft;
import com.rpb.reservation.payment.application.PaymentSession;
import com.rpb.reservation.payment.application.PaymentSessionDraft;
import com.rpb.reservation.payment.application.QuickPayRecord;
import com.rpb.reservation.payment.application.QuickPayRecordQuery;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface PaymentIntentRepository {
    Optional<PaymentIntentCreateResult> findCreateResultByIdempotencyKey(StoreScope scope, String idempotencyKey);

    Optional<PaymentSession> findSessionByNo(StoreScope scope, String sessionNo);

    List<QuickPayRecord> findQuickPayRecords(StoreScope scope, QuickPayRecordQuery query);

    int nextIntentSequence(StoreScope scope, YearMonth period);

    int allocateDisplayNumber(StoreScope scope, LocalDate businessDate, Integer requestedDisplayNumber);

    Optional<PaymentBusinessDay> findOpenBusinessDay(StoreScope scope);

    PaymentBusinessDay openBusinessDay(StoreScope scope, LocalDate businessDate, OffsetDateTime openedAt);

    Optional<PaymentBusinessDay> closeOpenBusinessDay(StoreScope scope, OffsetDateTime closedAt);

    PaymentIntentCreateResult createIntentWithSession(
        StoreScope scope,
        PaymentIntentDraft intent,
        PaymentSessionDraft session
    );
}
