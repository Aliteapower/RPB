package com.rpb.reservation.payment.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentProofTemplate;
import com.rpb.reservation.payment.application.PaymentProofTemplateCommand;
import com.rpb.reservation.payment.application.PaymentProofTemplateSample;
import com.rpb.reservation.payment.application.PaymentProofTemplateSampleCommand;
import com.rpb.reservation.payment.application.PaymentServiceErrorCode;
import com.rpb.reservation.payment.application.PaymentServiceException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaymentProofTemplateRepository implements PaymentProofTemplateRepository {
    private final JdbcTemplate jdbc;

    public JdbcPaymentProofTemplateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PaymentProofTemplate> findEffectiveTemplates(StoreScope scope) {
        return jdbc.query(
            """
            select id, tenant_id, bank_code, bank_name, locale, template_name, source, status,
                   priority, version, layout_json::text as layout_json, created_at, updated_at
            from payment_proof_templates
            where status = 'active'
              and (tenant_id is null or tenant_id = ?)
            order by case when tenant_id = ? then 0 else 1 end, priority asc, bank_code asc, template_name asc
            """,
            JdbcPaymentProofTemplateRepository::mapTemplate,
            scope.tenantId().value(),
            scope.tenantId().value()
        );
    }

    @Override
    public List<PaymentProofTemplate> findManageableTemplates(StoreScope scope) {
        return jdbc.query(
            """
            select id, tenant_id, bank_code, bank_name, locale, template_name, source, status,
                   priority, version, layout_json::text as layout_json, created_at, updated_at
            from payment_proof_templates
            where tenant_id is null or tenant_id = ?
            order by case when tenant_id = ? then 0 else 1 end, priority asc, bank_code asc, template_name asc
            """,
            JdbcPaymentProofTemplateRepository::mapTemplate,
            scope.tenantId().value(),
            scope.tenantId().value()
        );
    }

    @Override
    public PaymentProofTemplate createTenantTemplate(
        StoreScope scope,
        PaymentProofTemplateCommand command,
        UUID actorId
    ) {
        return jdbc.query(
            """
            insert into payment_proof_templates (
                tenant_id, bank_code, bank_name, locale, template_name, source, status,
                priority, layout_json, created_by, version
            ) values (?, ?, ?, ?, ?, 'tenant_custom', ?, ?, ?::jsonb, ?, 0)
            returning id, tenant_id, bank_code, bank_name, locale, template_name, source, status,
                      priority, version, layout_json::text as layout_json, created_at, updated_at
            """,
            JdbcPaymentProofTemplateRepository::mapTemplate,
            scope.tenantId().value(),
            command.bankCode(),
            command.bankName(),
            command.locale(),
            command.templateName(),
            command.status(),
            command.priority(),
            command.layoutJson(),
            actorId
        ).stream().findFirst().orElseThrow();
    }

    @Override
    public Optional<PaymentProofTemplate> findTemplateForTenant(StoreScope scope, UUID templateId) {
        return jdbc.query(
            """
            select id, tenant_id, bank_code, bank_name, locale, template_name, source, status,
                   priority, version, layout_json::text as layout_json, created_at, updated_at
            from payment_proof_templates
            where id = ?
              and (tenant_id is null or tenant_id = ?)
            """,
            JdbcPaymentProofTemplateRepository::mapTemplate,
            templateId,
            scope.tenantId().value()
        ).stream().findFirst();
    }

    @Override
    public PaymentProofTemplate updateTenantTemplate(
        StoreScope scope,
        UUID templateId,
        PaymentProofTemplateCommand command
    ) {
        return jdbc.query(
            """
            update payment_proof_templates
            set bank_code = ?,
                bank_name = ?,
                locale = ?,
                template_name = ?,
                status = ?,
                priority = ?,
                layout_json = ?::jsonb,
                updated_at = now(),
                version = version + 1
            where id = ?
              and tenant_id = ?
              and version = ?
            returning id, tenant_id, bank_code, bank_name, locale, template_name, source, status,
                      priority, version, layout_json::text as layout_json, created_at, updated_at
            """,
            JdbcPaymentProofTemplateRepository::mapTemplate,
            command.bankCode(),
            command.bankName(),
            command.locale(),
            command.templateName(),
            command.status(),
            command.priority(),
            command.layoutJson(),
            templateId,
            scope.tenantId().value(),
            command.version()
        ).stream().findFirst().orElseThrow(() -> new PaymentServiceException(PaymentServiceErrorCode.VERSION_CONFLICT));
    }

    @Override
    public PaymentProofTemplateSample createSample(
        StoreScope scope,
        UUID templateId,
        PaymentProofTemplateSampleCommand command,
        UUID actorId
    ) {
        return jdbc.query(
            """
            insert into payment_proof_template_samples (
                tenant_id, template_id, file_name, content_type, file_digest,
                ocr_reference, ocr_amount, raw_text, created_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            returning id, template_id, file_name, content_type, file_digest,
                      ocr_reference, ocr_amount, raw_text, created_at
            """,
            JdbcPaymentProofTemplateRepository::mapSample,
            scope.tenantId().value(),
            templateId,
            command.fileName(),
            command.contentType(),
            command.fileDigest(),
            command.ocrReference(),
            command.ocrAmount(),
            command.rawText(),
            actorId
        ).stream().findFirst().orElseThrow();
    }

    private static PaymentProofTemplate mapTemplate(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentProofTemplate(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("bank_code"),
            rs.getString("bank_name"),
            rs.getString("locale"),
            rs.getString("template_name"),
            rs.getString("source"),
            rs.getString("status"),
            rs.getInt("priority"),
            rs.getInt("version"),
            rs.getString("layout_json"),
            rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }

    private static PaymentProofTemplateSample mapSample(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentProofTemplateSample(
            rs.getObject("id", UUID.class),
            rs.getObject("template_id", UUID.class),
            rs.getString("file_name"),
            rs.getString("content_type"),
            rs.getString("file_digest"),
            rs.getString("ocr_reference"),
            rs.getBigDecimal("ocr_amount"),
            rs.getString("raw_text"),
            rs.getObject("created_at", java.time.OffsetDateTime.class)
        );
    }
}
