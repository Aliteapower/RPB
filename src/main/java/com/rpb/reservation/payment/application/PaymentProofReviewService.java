package com.rpb.reservation.payment.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.domain.PaymentReferencePattern;
import com.rpb.reservation.payment.persistence.PaymentProofReviewRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentProofReviewService {
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/png", "image/jpeg", "image/webp");

    private final PaymentProofReviewRepository repository;
    private final PaymentProofOcrAdapter ocrAdapter;
    private final PaymentProofTemplateService templateService;

    public PaymentProofReviewService(PaymentProofReviewRepository repository, PaymentProofOcrAdapter ocrAdapter) {
        this(repository, ocrAdapter, null);
    }

    @Autowired
    public PaymentProofReviewService(
        PaymentProofReviewRepository repository,
        PaymentProofOcrAdapter ocrAdapter,
        PaymentProofTemplateService templateService
    ) {
        this.repository = Objects.requireNonNull(repository, "payment_proof_review_repository_required");
        this.ocrAdapter = Objects.requireNonNull(ocrAdapter, "payment_proof_ocr_adapter_required");
        this.templateService = templateService;
    }

    @Transactional(readOnly = true)
    public List<PaymentProofCandidate> findCandidates(
        StoreScope scope,
        LocalDate businessDate,
        String terminalCode,
        int limit,
        CurrentActor actor
    ) {
        validateActor(scope, actor);
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 80 : limit, 200));
        return repository.findCandidates(scope, businessDate, trim(terminalCode), safeLimit);
    }

    @Transactional
    public PaymentProofScanResult scanAndMatch(StoreScope scope, PaymentProofScanCommand command, CurrentActor actor) {
        validateActor(scope, actor);
        PaymentProofScanCommand normalized = normalized(command);
        String fileDigest = fileDigest(normalized.fileBytes());
        return repository.findScanResultByIdempotencyKey(scope, normalized.idempotencyKey(), fileDigest)
            .map(PaymentProofScanResult::asReplay)
            .orElseGet(() -> {
                try {
                    return scanNew(scope, normalized, actor, fileDigest);
                } catch (DuplicateKeyException exception) {
                    throw new PaymentServiceException(PaymentServiceErrorCode.IDEMPOTENCY_CONFLICT);
                }
            });
    }

    private PaymentProofScanResult scanNew(
        StoreScope scope,
        PaymentProofScanCommand command,
        CurrentActor actor,
        String fileDigest
    ) {
        PaymentProofOcrFields fields = extract(scope, command);
        String extractedRef = PaymentReferencePattern.normalize(fields.extractedReference());
        List<String> referenceVariants = PaymentReferencePattern.lookupVariants(extractedRef);
        if (referenceVariants.isEmpty()) {
            return repository.createNoMatchResult(
                scope,
                command,
                fields,
                new PaymentProofChecks("missing", "not_checked"),
                actor.actorId(),
                fileDigest
            );
        }

        return repository.findUniqueActiveCandidateByReferences(
            scope,
            referenceVariants,
            command.businessDate(),
            command.terminalCode()
        )
            .map(candidate -> matchedResult(scope, command, actor, fileDigest, fields, extractedRef, candidate))
            .or(() -> repository.findUniqueCandidateByReferences(
                scope,
                referenceVariants,
                command.businessDate(),
                command.terminalCode()
            ).flatMap(candidate -> alreadyConfirmedResult(fields, extractedRef, candidate)))
            .orElseGet(() -> repository.createNoMatchResult(
                scope,
                command,
                fields,
                new PaymentProofChecks("not_found", "not_checked"),
                actor.actorId(),
                fileDigest
            ));
    }

    private Optional<PaymentProofScanResult> alreadyConfirmedResult(
        PaymentProofOcrFields fields,
        String extractedRef,
        PaymentProofCandidate candidate
    ) {
        String referenceCheck = PaymentReferencePattern.matches(extractedRef, candidate.paymentReference())
            ? "match"
            : "mismatch";
        String amountCheck = amountMatches(fields.extractedAmount(), candidate.amount())
            ? "match"
            : (fields.extractedAmount() == null ? "missing" : "mismatch");
        if (!"match".equals(referenceCheck)
            || !"match".equals(amountCheck)
            || !"paid".equals(candidate.intentStatus())
            || !"paid".equals(candidate.sessionStatus())) {
            return Optional.empty();
        }
        return Optional.of(new PaymentProofScanResult(
            true,
            false,
            "already_confirmed",
            candidate.intentId(),
            candidate.sessionId(),
            null,
            null,
            candidate.paymentReference(),
            candidate.amount(),
            fields,
            new PaymentProofChecks(referenceCheck, amountCheck)
        ));
    }

    private PaymentProofScanResult matchedResult(
        StoreScope scope,
        PaymentProofScanCommand command,
        CurrentActor actor,
        String fileDigest,
        PaymentProofOcrFields fields,
        String extractedRef,
        PaymentProofCandidate candidate
    ) {
        String referenceCheck = PaymentReferencePattern.matches(extractedRef, candidate.paymentReference())
            ? "match"
            : "mismatch";
        String amountCheck = amountMatches(fields.extractedAmount(), candidate.amount())
            ? "match"
            : (fields.extractedAmount() == null ? "missing" : "mismatch");
        PaymentProofChecks checks = new PaymentProofChecks(referenceCheck, amountCheck);
        boolean autoConfirm = "match".equals(referenceCheck)
            && "match".equals(amountCheck)
            && "pending".equals(candidate.intentStatus())
            && "pending".equals(candidate.sessionStatus());
        return repository.createMatchedProofAndMaybeConfirm(
            scope,
            candidate,
            command,
            fields,
            checks,
            autoConfirm,
            actor.actorId(),
            fileDigest
        );
    }

    private PaymentProofOcrFields extract(StoreScope scope, PaymentProofScanCommand command) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("rpb-payment-proof-", suffix(command.originalFileName()));
            Files.write(tempFile, command.fileBytes());
            PaymentProofOcrFields fields = ocrAdapter.extract(tempFile, new PaymentProofOcrExpected(null, null));
            return templateService == null ? fields : templateService.enhance(scope, fields);
        } catch (IOException exception) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static PaymentProofScanCommand normalized(PaymentProofScanCommand command) {
        if (command == null
            || isBlank(command.idempotencyKey())
            || command.fileBytes() == null
            || command.fileBytes().length == 0
            || !ALLOWED_CONTENT_TYPES.contains(trimLower(command.contentType()))) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
        return new PaymentProofScanCommand(
            trim(command.idempotencyKey()),
            trim(command.originalFileName()),
            trimLower(command.contentType()),
            command.fileBytes().clone(),
            command.businessDate(),
            trim(command.terminalCode())
        );
    }

    private static boolean amountMatches(BigDecimal extracted, BigDecimal expected) {
        if (extracted == null || expected == null) {
            return false;
        }
        return extracted.subtract(expected).abs().compareTo(AMOUNT_TOLERANCE) <= 0;
    }

    private static void validateActor(StoreScope scope, CurrentActor actor) {
        if (scope == null
            || actor == null
            || actor.tenantId() == null
            || !actor.tenantId().equals(scope.tenantId().value())
            || !actor.canAccessStore(scope.storeId().value())) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static String fileDigest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha_256_required", exception);
        }
    }

    private static String suffix(String fileName) {
        String safe = trimLower(fileName);
        if (safe == null || !safe.contains(".")) {
            return ".img";
        }
        String suffix = safe.substring(safe.lastIndexOf('.'));
        return suffix.matches("\\.[a-z0-9]{2,5}") ? suffix : ".img";
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimLower(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
