package com.rpb.reservation.payment.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentProofCandidate;
import com.rpb.reservation.payment.application.PaymentProofChecks;
import com.rpb.reservation.payment.application.PaymentProofOcrFields;
import com.rpb.reservation.payment.application.PaymentProofScanCommand;
import com.rpb.reservation.payment.application.PaymentProofScanResult;
import com.rpb.reservation.payment.application.PaymentServiceErrorCode;
import com.rpb.reservation.payment.application.PaymentServiceException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcPaymentProofReviewRepository implements PaymentProofReviewRepository {
    private final JdbcTemplate jdbc;

    public JdbcPaymentProofReviewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PaymentProofScanResult> findScanResultByIdempotencyKey(
        StoreScope scope,
        String idempotencyKey,
        String fileDigest
    ) {
        return jdbc.query(
            """
            select
                v.status as verification_status,
                v.id as verification_id,
                p.id as proof_id,
                p.intent_id,
                p.session_id,
                p.expected_reference,
                p.expected_amount,
                o.extracted_reference,
                o.extracted_amount,
                o.extracted_paid_at,
                o.bank_code,
                o.confidence,
                o.raw_text,
                o.raw_json::text as raw_json,
                v.matched_reference,
                v.matched_amount
            from payment_proofs p
            join payment_verifications v
              on v.tenant_id = p.tenant_id
             and v.store_id = p.store_id
             and v.proof_id = p.id
            left join payment_ocr_results o
              on o.tenant_id = p.tenant_id
             and o.store_id = p.store_id
             and o.proof_id = p.id
            where p.tenant_id = ?
              and p.store_id = ?
              and p.idempotency_key = ?
              and p.metadata_json ->> 'fileDigest' = ?
            order by v.created_at desc
            limit 1
            """,
            (rs, rowNum) -> mapScanResult(rs, true),
            scope.tenantId().value(),
            scope.storeId().value(),
            idempotencyKey,
            fileDigest
        ).stream().findFirst();
    }

    @Override
    public List<PaymentProofCandidate> findCandidates(StoreScope scope, LocalDate businessDate, String terminalCode, int limit) {
        StringBuilder sql = candidateSql(true);
        List<Object> args = candidateArgs(scope);
        appendCandidateFilters(sql, args, businessDate, terminalCode);
        sql.append(" order by s.created_at desc limit ?");
        args.add(Math.max(1, Math.min(limit <= 0 ? 80 : limit, 200)));
        return jdbc.query(sql.toString(), (rs, rowNum) -> mapCandidate(rs), args.toArray());
    }

    @Override
    public Optional<PaymentProofCandidate> findUniqueActiveCandidateByReferences(
        StoreScope scope,
        List<String> paymentReferences,
        LocalDate businessDate,
        String terminalCode
    ) {
        return findUniqueCandidateByReferences(scope, paymentReferences, businessDate, terminalCode, true);
    }

    @Override
    public Optional<PaymentProofCandidate> findUniqueCandidateByReferences(
        StoreScope scope,
        List<String> paymentReferences,
        LocalDate businessDate,
        String terminalCode
    ) {
        return findUniqueCandidateByReferences(scope, paymentReferences, businessDate, terminalCode, false);
    }

    private Optional<PaymentProofCandidate> findUniqueCandidateByReferences(
        StoreScope scope,
        List<String> paymentReferences,
        LocalDate businessDate,
        String terminalCode,
        boolean activeOnly
    ) {
        if (paymentReferences == null || paymentReferences.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder sql = candidateSql(activeOnly);
        List<Object> args = candidateArgs(scope);
        appendCandidateFilters(sql, args, businessDate, terminalCode);
        sql.append(" and upper(i.payment_reference) in (")
            .append(String.join(", ", Collections.nCopies(paymentReferences.size(), "upper(?)")))
            .append(") order by s.created_at desc limit 2");
        args.addAll(paymentReferences);
        List<PaymentProofCandidate> rows = jdbc.query(sql.toString(), (rs, rowNum) -> mapCandidate(rs), args.toArray());
        return rows.size() == 1 ? Optional.of(rows.get(0)) : Optional.empty();
    }

    @Override
    @Transactional
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
        UUID proofId = insertProof(scope, candidate, command, autoConfirm ? "confirmed" : "matched", actorId, fileDigest);
        insertOcr(scope, proofId, fields);
        UUID verificationId = insertVerification(scope, candidate, proofId, command, checks, autoConfirm, actorId, fileDigest);
        if (autoConfirm) {
            markPaid(scope, candidate);
            insertEvent(scope, candidate.intentId(), candidate.sessionId(), proofId, verificationId, "verification_confirmed", actorId, command.idempotencyKey());
        } else {
            markAwaitingVerification(scope, candidate);
            insertEvent(scope, candidate.intentId(), candidate.sessionId(), proofId, verificationId, "verification_pending", actorId, command.idempotencyKey());
        }
        insertEvent(scope, candidate.intentId(), candidate.sessionId(), proofId, verificationId, "proof_submitted", actorId, command.idempotencyKey());
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

    private StringBuilder candidateSql(boolean activeOnly) {
        StringBuilder sql = new StringBuilder("""
            select
                i.id as intent_id,
                s.id as session_id,
                i.intent_no,
                s.session_no,
                s.display_number,
                s.business_date,
                i.payment_reference,
                i.amount,
                i.currency,
                i.status as intent_status,
                s.status as session_status,
                s.terminal_code,
                s.cashier_name,
                i.created_at,
                coalesce(s.expires_at, i.expires_at) as expires_at
            from payment_intents i
            join payment_sessions s
              on s.tenant_id = i.tenant_id
             and s.store_id = i.store_id
             and s.intent_id = i.id
            where i.tenant_id = ?
              and i.store_id = ?
              and i.source_type = 'quick_pay'
            """);
        if (activeOnly) {
            sql.append("""
              and i.status in ('pending', 'awaiting_verification')
              and s.status in ('pending', 'awaiting_verification')
            """);
        }
        return sql;
    }

    private List<Object> candidateArgs(StoreScope scope) {
        List<Object> args = new ArrayList<>();
        args.add(scope.tenantId().value());
        args.add(scope.storeId().value());
        return args;
    }

    private void appendCandidateFilters(
        StringBuilder sql,
        List<Object> args,
        LocalDate businessDate,
        String terminalCode
    ) {
        if (businessDate != null) {
            sql.append(" and s.business_date = ?");
            args.add(businessDate);
        }
        if (terminalCode != null && !terminalCode.isBlank()) {
            sql.append(" and s.terminal_code = ?");
            args.add(terminalCode.trim());
        }
    }

    private UUID insertProof(
        StoreScope scope,
        PaymentProofCandidate candidate,
        PaymentProofScanCommand command,
        String status,
        UUID actorId,
        String fileDigest
    ) {
        return jdbc.query(
            """
            insert into payment_proofs (
                tenant_id,
                store_id,
                intent_id,
                session_id,
                status,
                storage_key,
                file_name,
                content_type,
                expected_reference,
                expected_amount,
                submitted_by,
                idempotency_key,
                metadata_json
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            returning id
            """,
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            scope.tenantId().value(),
            scope.storeId().value(),
            candidate.intentId(),
            candidate.sessionId(),
            status,
            fileDigest,
            command.originalFileName(),
            command.contentType(),
            candidate.paymentReference(),
            candidate.amount(),
            actorId,
            command.idempotencyKey(),
            metadataJson(fileDigest)
        ).stream().findFirst().orElseThrow();
    }

    private void insertOcr(StoreScope scope, UUID proofId, PaymentProofOcrFields fields) {
        jdbc.update(
            """
            insert into payment_ocr_results (
                tenant_id,
                store_id,
                proof_id,
                extracted_reference,
                extracted_amount,
                extracted_paid_at,
                bank_code,
                confidence,
                raw_text,
                raw_json
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """,
            scope.tenantId().value(),
            scope.storeId().value(),
            proofId,
            fields.extractedReference(),
            fields.extractedAmount(),
            fields.extractedPaidAt(),
            fields.bankCode(),
            fields.confidence(),
            fields.rawText(),
            blankToJson(fields.rawJson())
        );
    }

    private UUID insertVerification(
        StoreScope scope,
        PaymentProofCandidate candidate,
        UUID proofId,
        PaymentProofScanCommand command,
        PaymentProofChecks checks,
        boolean autoConfirm,
        UUID actorId,
        String fileDigest
    ) {
        return jdbc.query(
            """
            insert into payment_verifications (
                tenant_id,
                store_id,
                intent_id,
                proof_id,
                status,
                matched_reference,
                matched_amount,
                reviewed_by,
                reviewed_at,
                idempotency_key,
                metadata_json
            ) values (?, ?, ?, ?, ?, ?, ?, ?, case when ? then now() else null end, ?, ?::jsonb)
            returning id
            """,
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            scope.tenantId().value(),
            scope.storeId().value(),
            candidate.intentId(),
            proofId,
            autoConfirm ? "confirmed" : "pending",
            "match".equals(checks.reference()),
            "match".equals(checks.amount()),
            autoConfirm ? actorId : null,
            autoConfirm,
            command.idempotencyKey(),
            metadataJson(fileDigest)
        ).stream().findFirst().orElseThrow();
    }

    private void markPaid(StoreScope scope, PaymentProofCandidate candidate) {
        int intents = jdbc.update(
            """
            update payment_intents
            set status = 'paid',
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and id = ?
              and status = 'pending'
            """,
            scope.tenantId().value(),
            scope.storeId().value(),
            candidate.intentId()
        );
        int sessions = jdbc.update(
            """
            update payment_sessions
            set status = 'paid',
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and intent_id = ?
              and status = 'pending'
            """,
            scope.tenantId().value(),
            scope.storeId().value(),
            candidate.intentId()
        );
        if (intents == 0 || sessions == 0) {
            throw new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_INTENT_STATE_CONFLICT);
        }
    }

    private void markAwaitingVerification(StoreScope scope, PaymentProofCandidate candidate) {
        jdbc.update(
            """
            update payment_intents
            set status = 'awaiting_verification',
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and id = ?
              and status = 'pending'
            """,
            scope.tenantId().value(),
            scope.storeId().value(),
            candidate.intentId()
        );
        jdbc.update(
            """
            update payment_sessions
            set status = 'awaiting_verification',
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and id = ?
              and status = 'pending'
            """,
            scope.tenantId().value(),
            scope.storeId().value(),
            candidate.sessionId()
        );
    }

    private void insertEvent(
        StoreScope scope,
        UUID intentId,
        UUID sessionId,
        UUID proofId,
        UUID verificationId,
        String eventType,
        UUID actorId,
        String idempotencyKey
    ) {
        jdbc.update(
            """
            insert into payment_events (
                tenant_id,
                store_id,
                intent_id,
                session_id,
                proof_id,
                verification_id,
                event_type,
                actor_user_id,
                idempotency_key,
                event_payload
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb)
            on conflict do nothing
            """,
            scope.tenantId().value(),
            scope.storeId().value(),
            intentId,
            sessionId,
            proofId,
            verificationId,
            eventType,
            actorId,
            idempotencyKey + ":" + eventType
        );
    }

    private static PaymentProofCandidate mapCandidate(ResultSet rs) throws SQLException {
        return new PaymentProofCandidate(
            rs.getObject("intent_id", UUID.class),
            rs.getObject("session_id", UUID.class),
            rs.getString("intent_no"),
            rs.getString("session_no"),
            rs.getInt("display_number"),
            rs.getObject("business_date", LocalDate.class),
            rs.getString("payment_reference"),
            rs.getBigDecimal("amount"),
            rs.getString("currency"),
            rs.getString("intent_status"),
            rs.getString("session_status"),
            rs.getString("terminal_code"),
            rs.getString("cashier_name"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("expires_at", OffsetDateTime.class)
        );
    }

    private static PaymentProofScanResult mapScanResult(ResultSet rs, boolean replayed) throws SQLException {
        PaymentProofOcrFields ocr = new PaymentProofOcrFields(
            rs.getString("extracted_reference"),
            rs.getBigDecimal("extracted_amount"),
            rs.getObject("extracted_paid_at", OffsetDateTime.class),
            rs.getString("bank_code"),
            false,
            rs.getBigDecimal("confidence"),
            rs.getString("raw_text"),
            rs.getString("raw_json")
        );
        PaymentProofChecks checks = new PaymentProofChecks(
            Boolean.TRUE.equals(rs.getObject("matched_reference", Boolean.class)) ? "match" : "mismatch",
            Boolean.TRUE.equals(rs.getObject("matched_amount", Boolean.class)) ? "match" : "mismatch"
        );
        String status = rs.getString("verification_status");
        return new PaymentProofScanResult(
            true,
            replayed,
            "confirmed".equals(status) ? "auto_confirmed" : "needs_review",
            rs.getObject("intent_id", UUID.class),
            rs.getObject("session_id", UUID.class),
            rs.getObject("proof_id", UUID.class),
            rs.getObject("verification_id", UUID.class),
            rs.getString("expected_reference"),
            rs.getBigDecimal("expected_amount"),
            ocr,
            checks
        );
    }

    private static String metadataJson(String fileDigest) {
        return "{\"fileDigest\":\"" + fileDigest + "\"}";
    }

    private static String blankToJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }
}
