package com.rpb.reservation.payment.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentBusinessDay;
import com.rpb.reservation.payment.application.PaymentIntent;
import com.rpb.reservation.payment.application.PaymentIntentCreateResult;
import com.rpb.reservation.payment.application.PaymentIntentDraft;
import com.rpb.reservation.payment.application.PaymentSession;
import com.rpb.reservation.payment.application.PaymentSessionDraft;
import com.rpb.reservation.payment.application.QuickPayRecord;
import com.rpb.reservation.payment.application.QuickPayRecordQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcPaymentIntentRepository implements PaymentIntentRepository {
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcTemplate jdbc;

    public JdbcPaymentIntentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PaymentIntentCreateResult> findCreateResultByIdempotencyKey(StoreScope scope, String idempotencyKey) {
        return jdbc.query(
            """
            select
                i.id as intent_id,
                i.tenant_id as intent_tenant_id,
                i.store_id as intent_store_id,
                i.intent_no,
                i.source_type,
                i.source_id,
                i.method,
                i.amount,
                i.currency,
                i.payment_reference,
                i.status as intent_status,
                i.expires_at as intent_expires_at,
                i.version as intent_version,
                s.id as session_id,
                s.tenant_id as session_tenant_id,
                s.store_id as session_store_id,
                s.intent_id as session_intent_id,
                s.session_no,
                s.display_number,
                s.business_date,
                s.status as session_status,
                s.qr_payloads_json::text as qr_payloads_json,
                s.expires_at as session_expires_at,
                s.version as session_version
            from payment_intents i
            join payment_sessions s
              on s.tenant_id = i.tenant_id
             and s.store_id = i.store_id
             and s.intent_id = i.id
            where i.tenant_id = ?
              and i.store_id = ?
              and i.idempotency_key = ?
            order by s.created_at asc
            limit 1
            """,
            (rs, rowNum) -> new PaymentIntentCreateResult(
                true,
                false,
                mapIntent(rs),
                mapSession(rs),
                rs.getInt("display_number") + 1
            ),
            scope.tenantId().value(),
            scope.storeId().value(),
            idempotencyKey
        ).stream().findFirst();
    }

    @Override
    public Optional<PaymentSession> findSessionByNo(StoreScope scope, String sessionNo) {
        return jdbc.query(
            """
            select
                s.id as session_id,
                s.tenant_id as session_tenant_id,
                s.store_id as session_store_id,
                s.intent_id as session_intent_id,
                s.session_no,
                s.display_number,
                s.business_date,
                s.status as session_status,
                s.qr_payloads_json::text as qr_payloads_json,
                s.expires_at as session_expires_at,
                s.version as session_version
            from payment_sessions s
            where s.tenant_id = ?
              and s.store_id = ?
              and s.session_no = ?
            """,
            (rs, rowNum) -> mapSession(rs),
            scope.tenantId().value(),
            scope.storeId().value(),
            sessionNo
        ).stream().findFirst();
    }

    @Override
    public List<QuickPayRecord> findQuickPayRecords(StoreScope scope, QuickPayRecordQuery query) {
        StringBuilder sql = new StringBuilder("""
            select
                i.id as intent_id,
                s.id as session_id,
                i.intent_no,
                s.session_no,
                s.display_number,
                s.business_date,
                i.amount,
                i.currency,
                i.payment_reference,
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
        List<Object> args = new ArrayList<>();
        args.add(scope.tenantId().value());
        args.add(scope.storeId().value());
        if (query.businessDate() != null) {
            sql.append(" and s.business_date = ?");
            args.add(query.businessDate());
        }
        if (!isBlank(query.status())) {
            sql.append(" and i.status = ?");
            args.add(query.status());
        }
        if (!isBlank(query.terminalCode())) {
            sql.append(" and s.terminal_code = ?");
            args.add(query.terminalCode());
        }
        if (!isBlank(query.search())) {
            sql.append("""
                 and (
                    i.intent_no ilike ?
                    or i.payment_reference ilike ?
                    or s.session_no ilike ?
                    or s.terminal_code ilike ?
                    or s.cashier_name ilike ?
                 )
                """);
            String like = "%" + query.search() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" order by i.created_at desc, s.created_at desc limit ?");
        args.add(query.limit());
        return jdbc.query(sql.toString(), (rs, rowNum) -> mapQuickPayRecord(rs), args.toArray());
    }

    @Override
    public int nextIntentSequence(StoreScope scope, YearMonth period) {
        String prefix = "PIT-" + period.format(PERIOD_FORMATTER) + "-";
        jdbc.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 0))",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            },
            "payment_intents:" + scope.tenantId().value() + ":" + period.format(PERIOD_FORMATTER)
        );
        Integer next = jdbc.queryForObject(
            """
            select count(*) + 1
            from payment_intents
            where tenant_id = ?
              and intent_no like ?
            """,
            Integer.class,
            scope.tenantId().value(),
            prefix + "%"
        );
        return next == null ? 1 : next;
    }

    @Override
    public int allocateDisplayNumber(StoreScope scope, LocalDate businessDate, Integer requestedDisplayNumber) {
        if (requestedDisplayNumber != null) {
            return requestedDisplayNumber;
        }
        Integer displayNumber = jdbc.queryForObject(
            """
            insert into payment_display_counters (
                tenant_id,
                store_id,
                business_date,
                next_display_number
            )
            values (?, ?, ?, 2)
            on conflict (tenant_id, store_id, business_date) do update
            set
                next_display_number = payment_display_counters.next_display_number + 1,
                updated_at = now(),
                version = payment_display_counters.version + 1
            returning next_display_number - 1
            """,
            Integer.class,
            scope.tenantId().value(),
            scope.storeId().value(),
            businessDate
        );
        return displayNumber == null ? 1 : displayNumber;
    }

    @Override
    public Optional<PaymentBusinessDay> findOpenBusinessDay(StoreScope scope) {
        return jdbc.query(
            """
            select business_date, status, opened_at, closed_at
            from payment_business_days
            where tenant_id = ?
              and store_id = ?
              and status = 'open'
            order by opened_at desc nulls last, created_at desc
            limit 1
            """,
            (rs, rowNum) -> mapBusinessDay(rs),
            scope.tenantId().value(),
            scope.storeId().value()
        ).stream().findFirst();
    }

    @Override
    public PaymentBusinessDay openBusinessDay(StoreScope scope, LocalDate businessDate, OffsetDateTime openedAt) {
        jdbc.update(
            """
            update payment_business_days
            set
                status = 'closed',
                closed_at = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and status = 'open'
              and business_date <> ?
            """,
            openedAt,
            scope.tenantId().value(),
            scope.storeId().value(),
            businessDate
        );
        return jdbc.query(
            """
            insert into payment_business_days (
                tenant_id,
                store_id,
                business_date,
                status,
                opened_at
            )
            values (?, ?, ?, 'open', ?)
            on conflict (tenant_id, store_id, business_date) do update
            set
                status = 'open',
                opened_at = coalesce(payment_business_days.opened_at, excluded.opened_at),
                closed_at = null,
                updated_at = now(),
                version = payment_business_days.version + 1
            returning business_date, status, opened_at, closed_at
            """,
            (rs, rowNum) -> mapBusinessDay(rs),
            scope.tenantId().value(),
            scope.storeId().value(),
            businessDate,
            openedAt
        ).stream().findFirst().orElseThrow();
    }

    @Override
    public Optional<PaymentBusinessDay> closeOpenBusinessDay(StoreScope scope, OffsetDateTime closedAt) {
        return jdbc.query(
            """
            update payment_business_days
            set
                status = 'closed',
                closed_at = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and status = 'open'
            returning business_date, status, opened_at, closed_at
            """,
            (rs, rowNum) -> mapBusinessDay(rs),
            closedAt,
            scope.tenantId().value(),
            scope.storeId().value()
        ).stream().findFirst();
    }

    @Override
    @Transactional
    public PaymentIntentCreateResult createIntentWithSession(
        StoreScope scope,
        PaymentIntentDraft intent,
        PaymentSessionDraft session
    ) {
        PaymentIntent savedIntent = jdbc.query(
            """
            insert into payment_intents (
                tenant_id,
                store_id,
                intent_no,
                source_type,
                source_id,
                method,
                amount,
                currency,
                payment_reference,
                status,
                expires_at,
                idempotency_key,
                metadata_json,
                created_by
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?, ?::jsonb, ?)
            returning id as intent_id, tenant_id as intent_tenant_id, store_id as intent_store_id,
                      intent_no, source_type, source_id, method, amount, currency, payment_reference,
                      status as intent_status, expires_at as intent_expires_at, version as intent_version
            """,
            (rs, rowNum) -> mapIntent(rs),
            scope.tenantId().value(),
            scope.storeId().value(),
            intent.intentNo(),
            intent.sourceType(),
            intent.sourceId(),
            intent.method(),
            intent.amount(),
            intent.currency(),
            intent.paymentReference(),
            intent.expiresAt(),
            intent.idempotencyKey(),
            intent.metadataJson(),
            intent.createdBy()
        ).stream().findFirst().orElseThrow();

        PaymentSession savedSession = jdbc.query(
            """
            insert into payment_sessions (
                tenant_id,
                store_id,
                intent_id,
                session_no,
                display_number,
                business_date,
                terminal_code,
                cashier_name,
                status,
                qr_payloads_json,
                expires_at,
                idempotency_key,
                created_by
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?::jsonb, ?, ?, ?)
            returning id as session_id, tenant_id as session_tenant_id, store_id as session_store_id,
                      intent_id as session_intent_id, session_no, display_number, business_date,
                      status as session_status, qr_payloads_json::text as qr_payloads_json,
                      expires_at as session_expires_at, version as session_version
            """,
            (rs, rowNum) -> mapSession(rs),
            scope.tenantId().value(),
            scope.storeId().value(),
            savedIntent.id(),
            session.sessionNo(),
            session.displayNumber(),
            session.businessDate(),
            session.terminalCode(),
            session.cashierName(),
            session.qrPayloadsJson(),
            session.expiresAt(),
            session.idempotencyKey(),
            session.createdBy()
        ).stream().findFirst().orElseThrow();

        return new PaymentIntentCreateResult(true, false, savedIntent, savedSession, savedSession.displayNumber() + 1);
    }

    private static PaymentIntent mapIntent(ResultSet rs, int rowNum) throws SQLException {
        return mapIntent(rs);
    }

    private static PaymentIntent mapIntent(ResultSet rs) throws SQLException {
        return new PaymentIntent(
            rs.getObject("intent_id", UUID.class),
            rs.getObject("intent_tenant_id", UUID.class),
            rs.getObject("intent_store_id", UUID.class),
            rs.getString("intent_no"),
            rs.getString("source_type"),
            rs.getObject("source_id", UUID.class),
            rs.getString("method"),
            rs.getBigDecimal("amount"),
            rs.getString("currency"),
            rs.getString("payment_reference"),
            rs.getString("intent_status"),
            rs.getObject("intent_expires_at", java.time.OffsetDateTime.class),
            rs.getInt("intent_version")
        );
    }

    private static PaymentSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        return mapSession(rs);
    }

    private static PaymentSession mapSession(ResultSet rs) throws SQLException {
        return new PaymentSession(
            rs.getObject("session_id", UUID.class),
            rs.getObject("session_tenant_id", UUID.class),
            rs.getObject("session_store_id", UUID.class),
            rs.getObject("session_intent_id", UUID.class),
            rs.getString("session_no"),
            rs.getInt("display_number"),
            rs.getObject("business_date", LocalDate.class),
            rs.getString("session_status"),
            rs.getString("qr_payloads_json"),
            rs.getObject("session_expires_at", java.time.OffsetDateTime.class),
            rs.getInt("session_version")
        );
    }

    private static QuickPayRecord mapQuickPayRecord(ResultSet rs) throws SQLException {
        return new QuickPayRecord(
            rs.getObject("intent_id", UUID.class),
            rs.getObject("session_id", UUID.class),
            rs.getString("intent_no"),
            rs.getString("session_no"),
            rs.getInt("display_number"),
            rs.getObject("business_date", LocalDate.class),
            rs.getBigDecimal("amount"),
            rs.getString("currency"),
            rs.getString("payment_reference"),
            rs.getString("intent_status"),
            rs.getString("session_status"),
            rs.getString("terminal_code"),
            rs.getString("cashier_name"),
            rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("expires_at", java.time.OffsetDateTime.class)
        );
    }

    private static PaymentBusinessDay mapBusinessDay(ResultSet rs) throws SQLException {
        return new PaymentBusinessDay(
            rs.getObject("business_date", LocalDate.class),
            rs.getString("status"),
            rs.getObject("opened_at", OffsetDateTime.class),
            rs.getObject("closed_at", OffsetDateTime.class)
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
