package com.rpb.reservation.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.persistence.PaymentProofReviewRepository;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class PaymentProofReviewServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    private final StoreScope scope = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));
    private final CurrentActor actor = CurrentActor.storeStaff(
        TENANT_ID,
        ACTOR_ID,
        "tenant_staff",
        Set.of("store_staff"),
        Set.of("payment.proof.review"),
        Set.of(STORE_ID)
    );
    private InMemoryPaymentProofReviewRepository repository;
    private FakeOcrAdapter ocr;
    private PaymentProofReviewService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPaymentProofReviewRepository();
        ocr = new FakeOcrAdapter();
        service = new PaymentProofReviewService(repository, ocr);
    }

    @Test
    void autoConfirmsWhenExtractedReferenceAndAmountMatchOnePendingIntent() {
        PaymentProofCandidate candidate = candidate("PIT-202608-0021", new BigDecimal("0.10"));
        repository.candidate = Optional.of(candidate);
        ocr.fields = new PaymentProofOcrFields(
            "PIT-202608-0021",
            new BigDecimal("0.10"),
            null,
            "ocbc",
            true,
            new BigDecimal("0.9600"),
            "讯息 PIT-202608-0021 您已支付 0.10 SGD",
            "{}"
        );

        PaymentProofScanResult result = service.scanAndMatch(scope, command("proof-001"), actor);

        assertThat(result.outcome()).isEqualTo("auto_confirmed");
        assertThat(result.paymentReference()).isEqualTo("PIT-202608-0021");
        assertThat(repository.confirmedIntentId).isEqualTo(candidate.intentId());
        assertThat(repository.createdProofStatus).isEqualTo("confirmed");
        assertThat(repository.createdVerificationStatus).isEqualTo("confirmed");
    }

    @Test
    void noMatchWhenReferenceIsMissing() {
        ocr.fields = new PaymentProofOcrFields(
            null,
            new BigDecimal("0.10"),
            null,
            "ocbc",
            true,
            new BigDecimal("0.5200"),
            "Transaction ID 2605160110303261 SGD 0.10",
            "{}"
        );

        PaymentProofScanResult result = service.scanAndMatch(scope, command("proof-002"), actor);

        assertThat(result.outcome()).isEqualTo("no_match");
        assertThat(repository.confirmedIntentId).isNull();
    }

    @Test
    void needsReviewWhenReferenceMatchesButAmountDoesNotMatch() {
        PaymentProofCandidate candidate = candidate("QP-202608-0040-87D0", new BigDecimal("1.00"));
        repository.candidate = Optional.of(candidate);
        ocr.fields = new PaymentProofOcrFields(
            "QP-202608-0040-87D0",
            new BigDecimal("0.10"),
            null,
            "ocbc",
            true,
            new BigDecimal("0.8600"),
            "QP-202608-0040-87D0 SGD 0.10",
            "{}"
        );

        PaymentProofScanResult result = service.scanAndMatch(scope, command("proof-003"), actor);

        assertThat(result.outcome()).isEqualTo("needs_review");
        assertThat(result.checks().amount()).isEqualTo("mismatch");
        assertThat(repository.createdVerificationStatus).isEqualTo("pending");
        assertThat(repository.confirmedIntentId).isNull();
    }

    @Test
    void mapsDuplicateProofIdempotencyKeyToBusinessConflict() {
        repository.throwDuplicateOnCreate = true;
        repository.candidate = Optional.of(candidate("QP-202608-0040-87D0", new BigDecimal("1.00")));
        ocr.fields = new PaymentProofOcrFields(
            "QP-202608-0040-87D0",
            new BigDecimal("1.00"),
            null,
            "ocbc",
            true,
            new BigDecimal("0.8600"),
            "QP-202608-0040-87D0 SGD 1.00",
            "{}"
        );

        assertThatThrownBy(() -> service.scanAndMatch(scope, command("proof-duplicate"), actor))
            .isInstanceOf(PaymentServiceException.class)
            .extracting(error -> ((PaymentServiceException) error).code())
            .isEqualTo(PaymentServiceErrorCode.IDEMPOTENCY_CONFLICT);
    }

    private PaymentProofScanCommand command(String idempotencyKey) {
        return new PaymentProofScanCommand(
            idempotencyKey,
            "proof.jpg",
            "image/jpeg",
            "fake-image".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            LocalDate.parse("2026-08-08"),
            "T1"
        );
    }

    private PaymentProofCandidate candidate(String reference, BigDecimal amount) {
        return new PaymentProofCandidate(
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            "PIT-202608-0001",
            "PRS-ABCDEF1234567890",
            1,
            LocalDate.parse("2026-08-08"),
            reference,
            amount,
            "SGD",
            "pending",
            "pending",
            "T1",
            "Alice",
            OffsetDateTime.parse("2026-08-08T04:10:00Z"),
            OffsetDateTime.parse("2026-08-08T04:12:00Z")
        );
    }

    private static final class FakeOcrAdapter implements PaymentProofOcrAdapter {
        private PaymentProofOcrFields fields;

        @Override
        public PaymentProofOcrFields extract(Path file, PaymentProofOcrExpected expected) {
            return fields;
        }
    }

    private static final class InMemoryPaymentProofReviewRepository implements PaymentProofReviewRepository {
        private Optional<PaymentProofCandidate> candidate = Optional.empty();
        private UUID confirmedIntentId;
        private String createdProofStatus;
        private String createdVerificationStatus;
        private boolean throwDuplicateOnCreate;

        @Override
        public Optional<PaymentProofScanResult> findScanResultByIdempotencyKey(
            StoreScope scope,
            String idempotencyKey,
            String fileDigest
        ) {
            return Optional.empty();
        }

        @Override
        public List<PaymentProofCandidate> findCandidates(StoreScope scope, LocalDate businessDate, String terminalCode, int limit) {
            return candidate.stream().toList();
        }

        @Override
        public Optional<PaymentProofCandidate> findActiveCandidateByReference(
            StoreScope scope,
            String paymentReference,
            LocalDate businessDate,
            String terminalCode
        ) {
            return candidate.filter(value -> value.paymentReference().equals(paymentReference));
        }

        @Override
        public PaymentProofScanResult createMatchedProofAndMaybeConfirm(
            StoreScope scope,
            PaymentProofCandidate candidate,
            PaymentProofScanCommand command,
            PaymentProofOcrFields fields,
            PaymentProofChecks checks,
            boolean autoConfirm,
            UUID actorId,
            String fileDigest
        ) {
            if (throwDuplicateOnCreate) {
                throw new DuplicateKeyException("duplicate idempotency key");
            }
            UUID proofId = UUID.fromString("70000000-0000-0000-0000-000000000001");
            UUID verificationId = UUID.fromString("80000000-0000-0000-0000-000000000001");
            createdProofStatus = autoConfirm ? "confirmed" : "matched";
            createdVerificationStatus = autoConfirm ? "confirmed" : "pending";
            if (autoConfirm) {
                confirmedIntentId = candidate.intentId();
            }
            return new PaymentProofScanResult(
                true,
                false,
                autoConfirm ? "auto_confirmed" : "needs_review",
                candidate.intentId(),
                candidate.sessionId(),
                proofId,
                verificationId,
                candidate.paymentReference(),
                candidate.amount(),
                fields,
                checks
            );
        }

        @Override
        public PaymentProofScanResult createNoMatchResult(
            StoreScope scope,
            PaymentProofScanCommand command,
            PaymentProofOcrFields fields,
            PaymentProofChecks checks,
            UUID actorId,
            String fileDigest
        ) {
            return PaymentProofScanResult.noMatch(false, fields, checks);
        }
    }
}
