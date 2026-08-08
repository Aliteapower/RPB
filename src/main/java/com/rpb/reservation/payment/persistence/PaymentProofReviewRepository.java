package com.rpb.reservation.payment.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentProofCandidate;
import com.rpb.reservation.payment.application.PaymentProofChecks;
import com.rpb.reservation.payment.application.PaymentProofOcrFields;
import com.rpb.reservation.payment.application.PaymentProofScanCommand;
import com.rpb.reservation.payment.application.PaymentProofScanResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentProofReviewRepository {
    Optional<PaymentProofScanResult> findScanResultByIdempotencyKey(
        StoreScope scope,
        String idempotencyKey,
        String fileDigest
    );

    List<PaymentProofCandidate> findCandidates(StoreScope scope, LocalDate businessDate, String terminalCode, int limit);

    Optional<PaymentProofCandidate> findUniqueActiveCandidateByReferences(
        StoreScope scope,
        List<String> paymentReferences,
        LocalDate businessDate,
        String terminalCode
    );

    PaymentProofScanResult createMatchedProofAndMaybeConfirm(
        StoreScope scope,
        PaymentProofCandidate candidate,
        PaymentProofScanCommand command,
        PaymentProofOcrFields fields,
        PaymentProofChecks checks,
        boolean autoConfirm,
        UUID actorId,
        String fileDigest
    );

    PaymentProofScanResult createNoMatchResult(
        StoreScope scope,
        PaymentProofScanCommand command,
        PaymentProofOcrFields fields,
        PaymentProofChecks checks,
        UUID actorId,
        String fileDigest
    );
}
