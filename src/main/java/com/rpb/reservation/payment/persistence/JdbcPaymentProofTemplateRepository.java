package com.rpb.reservation.payment.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentProofTemplate;
import com.rpb.reservation.payment.application.PaymentProofTemplateCommand;
import com.rpb.reservation.payment.application.PaymentProofTemplateContribution;
import com.rpb.reservation.payment.application.PaymentProofTemplateContributionCommand;
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

    @Override
    public List<PaymentProofTemplate> findPlatformTemplates() {
        return jdbc.query(
            """
            select id, tenant_id, bank_code, bank_name, locale, template_name, source, status,
                   priority, version, layout_json::text as layout_json, created_at, updated_at
            from payment_proof_templates
            where tenant_id is null
            order by priority asc, bank_code asc, template_name asc
            """,
            JdbcPaymentProofTemplateRepository::mapTemplate
        );
    }

    @Override
    public PaymentProofTemplate createPlatformTemplate(PaymentProofTemplateCommand command, UUID actorId) {
        return jdbc.query(
            """
            insert into payment_proof_templates (
                tenant_id, bank_code, bank_name, locale, template_name, source, status,
                priority, layout_json, created_by, version
            ) values (null, ?, ?, ?, ?, 'platform_seed', ?, ?, ?::jsonb, ?, 0)
            returning id, tenant_id, bank_code, bank_name, locale, template_name, source, status,
                      priority, version, layout_json::text as layout_json, created_at, updated_at
            """,
            JdbcPaymentProofTemplateRepository::mapTemplate,
            command.bankCode(), command.bankName(), command.locale(), command.templateName(), command.status(),
            command.priority(), command.layoutJson(), actorId
        ).stream().findFirst().orElseThrow();
    }

    @Override
    public PaymentProofTemplate updatePlatformTemplate(UUID templateId, PaymentProofTemplateCommand command) {
        return jdbc.query(
            """
            update payment_proof_templates
            set bank_code = ?, bank_name = ?, locale = ?, template_name = ?, status = ?, priority = ?,
                layout_json = ?::jsonb, updated_at = now(), version = version + 1
            where id = ? and tenant_id is null and version = ?
            returning id, tenant_id, bank_code, bank_name, locale, template_name, source, status,
                      priority, version, layout_json::text as layout_json, created_at, updated_at
            """,
            JdbcPaymentProofTemplateRepository::mapTemplate,
            command.bankCode(), command.bankName(), command.locale(), command.templateName(), command.status(),
            command.priority(), command.layoutJson(), templateId, command.version()
        ).stream().findFirst().orElseThrow(() -> new PaymentServiceException(PaymentServiceErrorCode.VERSION_CONFLICT));
    }

    @Override
    public PaymentProofTemplateContribution createContribution(
        StoreScope scope,
        PaymentProofTemplateContributionCommand command,
        UUID actorId
    ) {
        return jdbc.query(
            """
            insert into payment_proof_template_contributions (
                tenant_id, store_id, source_template_id, bank_code, bank_name, locale, template_name, layout_json,
                sample_file_name, sample_content_type, sample_file_digest, sample_ocr_reference, sample_ocr_amount,
                sample_raw_text, submitted_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
            returning id, tenant_id, store_id, source_template_id, platform_template_id, bank_code, bank_name, locale,
                      template_name, layout_json::text as layout_json, sample_file_name, sample_content_type,
                      sample_file_digest, sample_raw_text, sample_ocr_reference, sample_ocr_amount, status, review_note,
                      submitted_by, reviewed_by, created_at, updated_at, reviewed_at, version
            """,
            JdbcPaymentProofTemplateRepository::mapContribution,
            scope.tenantId().value(), scope.storeId().value(), command.sourceTemplateId(), command.bankCode(),
            command.bankName(), command.locale(), command.templateName(), command.layoutJson(), command.sampleFileName(),
            command.sampleContentType(), command.sampleFileDigest(), command.sampleOcrReference(), command.sampleOcrAmount(),
            command.sampleRawText(), actorId
        ).stream().findFirst().orElseThrow();
    }

    @Override
    public List<PaymentProofTemplateContribution> findTenantContributions(StoreScope scope) {
        return jdbc.query(
            """
            select id, tenant_id, store_id, source_template_id, platform_template_id, bank_code, bank_name, locale,
                   template_name, layout_json::text as layout_json, sample_file_name, sample_content_type,
                   sample_file_digest, sample_raw_text, sample_ocr_reference, sample_ocr_amount, status, review_note,
                   submitted_by, reviewed_by, created_at, updated_at, reviewed_at, version
            from payment_proof_template_contributions
            where tenant_id = ?
            order by created_at desc
            """,
            JdbcPaymentProofTemplateRepository::mapContribution,
            scope.tenantId().value()
        );
    }

    @Override
    public List<PaymentProofTemplateContribution> findPlatformContributions(String status) {
        String query = """
            select id, tenant_id, store_id, source_template_id, platform_template_id, bank_code, bank_name, locale,
                   template_name, layout_json::text as layout_json, sample_file_name, sample_content_type,
                   sample_file_digest, sample_raw_text, sample_ocr_reference, sample_ocr_amount, status, review_note,
                   submitted_by, reviewed_by, created_at, updated_at, reviewed_at, version
            from payment_proof_template_contributions
            """;
        if (status == null || status.isBlank()) {
            return jdbc.query(query + " order by created_at desc", JdbcPaymentProofTemplateRepository::mapContribution);
        }
        return jdbc.query(
            query + " where status = ? order by created_at desc",
            JdbcPaymentProofTemplateRepository::mapContribution,
            status
        );
    }

    @Override
    public PaymentProofTemplateContribution acceptContribution(
        UUID contributionId,
        UUID platformTemplateId,
        UUID actorId,
        String reviewNote,
        int version
    ) {
        return reviewContribution(contributionId, platformTemplateId, actorId, reviewNote, version, "accepted");
    }

    @Override
    public PaymentProofTemplateContribution rejectContribution(UUID contributionId, UUID actorId, String reviewNote, int version) {
        return reviewContribution(contributionId, null, actorId, reviewNote, version, "rejected");
    }

    private PaymentProofTemplateContribution reviewContribution(
        UUID contributionId,
        UUID platformTemplateId,
        UUID actorId,
        String reviewNote,
        int version,
        String status
    ) {
        return jdbc.query(
            """
            update payment_proof_template_contributions
            set status = ?, platform_template_id = ?, reviewed_by = ?, reviewed_at = now(), review_note = ?,
                updated_at = now(), version = version + 1
            where id = ? and status = 'submitted' and version = ?
            returning id, tenant_id, store_id, source_template_id, platform_template_id, bank_code, bank_name, locale,
                      template_name, layout_json::text as layout_json, sample_file_name, sample_content_type,
                      sample_file_digest, sample_raw_text, sample_ocr_reference, sample_ocr_amount, status, review_note,
                      submitted_by, reviewed_by, created_at, updated_at, reviewed_at, version
            """,
            JdbcPaymentProofTemplateRepository::mapContribution,
            status, platformTemplateId, actorId, reviewNote, contributionId, version
        ).stream().findFirst().orElseThrow(() -> new PaymentServiceException(PaymentServiceErrorCode.VERSION_CONFLICT));
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

    private static PaymentProofTemplateContribution mapContribution(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentProofTemplateContribution(
            rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getObject("store_id", UUID.class),
            rs.getObject("source_template_id", UUID.class), rs.getObject("platform_template_id", UUID.class),
            rs.getString("bank_code"), rs.getString("bank_name"), rs.getString("locale"), rs.getString("template_name"),
            rs.getString("layout_json"), rs.getString("sample_file_name"), rs.getString("sample_content_type"),
            rs.getString("sample_file_digest"), rs.getString("sample_raw_text"), rs.getString("sample_ocr_reference"),
            rs.getBigDecimal("sample_ocr_amount"), rs.getString("status"), rs.getString("review_note"),
            rs.getObject("submitted_by", UUID.class), rs.getObject("reviewed_by", UUID.class),
            rs.getObject("created_at", java.time.OffsetDateTime.class), rs.getObject("updated_at", java.time.OffsetDateTime.class),
            rs.getObject("reviewed_at", java.time.OffsetDateTime.class), rs.getInt("version")
        );
    }
}
