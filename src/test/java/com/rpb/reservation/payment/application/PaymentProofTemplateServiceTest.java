package com.rpb.reservation.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.persistence.PaymentProofTemplateRepository;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentProofTemplateServiceTest {
    private final StoreScope scope = new StoreScope(
        new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001")),
        new StoreId(UUID.fromString("20000000-0000-0000-0000-000000000001"))
    );
    private final InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
    private final PaymentProofTemplateService service = new PaymentProofTemplateService(repository);

    @Test
    void tenantCustomTemplateIsSelectedBeforePlatformSeed() {
        repository.templates.add(template(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            null,
            "platform_seed",
            "ocbc",
            10,
            """
            {"matchKeywords":["OCBC"],"successKeywords":["您已支付"],"referencePatterns":["PLAT\\\\d+"],"amountPatterns":["您已支付\\\\s*([0-9.]+)\\\\s*SGD"]}
            """
        ));
        repository.templates.add(template(
            UUID.fromString("30000000-0000-0000-0000-000000000002"),
            scope.tenantId().value(),
            "tenant_custom",
            "ocbc",
            1,
            """
            {"matchKeywords":["OCBC"],"successKeywords":["您已支付"],"referencePatterns":["QP\\\\d{12}[A-Z0-9]{4}"],"amountPatterns":["您已支付\\\\s*([0-9.]+)\\\\s*SGD"]}
            """
        ));

        PaymentProofOcrFields enhanced = service.enhance(
            scope,
            new PaymentProofOcrFields(
                null,
                null,
                null,
                null,
                true,
                new BigDecimal("0.3000"),
                """
                OCBC
                您已支付 1.00 SGD
                讯息
                QP202608090017GQVQ
                """,
                "{}"
            )
        );

        assertThat(enhanced.extractedReference()).isEqualTo("QP202608090017GQVQ");
        assertThat(enhanced.extractedAmount()).isEqualByComparingTo("1.00");
        assertThat(enhanced.bankCode()).isEqualTo("ocbc");
        assertThat(enhanced.rawJson()).contains("templateId").contains("tenant_custom");
    }

    @Test
    void enhancementKeepsOriginalFieldsWhenNoTemplateMatches() {
        PaymentProofOcrFields original = new PaymentProofOcrFields(
            "QP202608090016MERK",
            null,
            null,
            null,
            false,
            new BigDecimal("0.4000"),
            "random receipt text",
            "{}"
        );

        assertThat(service.enhance(scope, original)).isSameAs(original);
    }

    @Test
    void enhancementSkipsMalformedPersistedPatternsAndContinuesWithValidPatterns() {
        repository.templates.add(template(
            UUID.fromString("30000000-0000-0000-0000-000000000003"),
            null,
            "platform_seed",
            "ocbc",
            10,
            """
            {"matchKeywords":["OCBC"],"referencePatterns":["[","(PIT-\\\\d{6}-\\\\d{4})"],"amountPatterns":["[","SGD\\\\s*([0-9.]+)"]}
            """
        ));

        PaymentProofOcrFields enhanced = service.enhance(
            scope,
            new PaymentProofOcrFields(
                null,
                null,
                null,
                null,
                true,
                new BigDecimal("0.3000"),
                "OCBC\nRef PIT-202608-0021\nPaid SGD 1.00",
                "{}"
            )
        );

        assertThat(enhanced.extractedReference()).isEqualTo("PIT-202608-0021");
        assertThat(enhanced.extractedAmount()).isEqualByComparingTo("1.00");
    }

    private static PaymentProofTemplate template(
        UUID id,
        UUID tenantId,
        String source,
        String bankCode,
        int priority,
        String configJson
    ) {
        return new PaymentProofTemplate(
            id,
            tenantId,
            bankCode,
            bankCode.toUpperCase(),
            "zh-CN",
            bankCode.toUpperCase() + " PayNow",
            source,
            "active",
            priority,
            1,
            configJson,
            OffsetDateTime.parse("2026-08-09T00:00:00Z"),
            OffsetDateTime.parse("2026-08-09T00:00:00Z")
        );
    }

    private static final class InMemoryTemplateRepository implements PaymentProofTemplateRepository {
        private final List<PaymentProofTemplate> templates = new ArrayList<>();

        @Override
        public List<PaymentProofTemplate> findEffectiveTemplates(StoreScope scope) {
            return templates.stream()
                .filter(template -> template.tenantId() == null || template.tenantId().equals(scope.tenantId().value()))
                .toList();
        }

        @Override
        public List<PaymentProofTemplate> findManageableTemplates(StoreScope scope) {
            return findEffectiveTemplates(scope);
        }

        @Override
        public PaymentProofTemplate createTenantTemplate(StoreScope scope, PaymentProofTemplateCommand command, UUID actorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PaymentProofTemplate> findTemplateForTenant(StoreScope scope, UUID templateId) {
            return templates.stream().filter(template -> template.id().equals(templateId)).findFirst();
        }

        @Override
        public PaymentProofTemplate updateTenantTemplate(StoreScope scope, UUID templateId, PaymentProofTemplateCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentProofTemplateSample createSample(
            StoreScope scope,
            UUID templateId,
            PaymentProofTemplateSampleCommand command,
            UUID actorId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PaymentProofTemplate> findPlatformTemplates() { return List.of(); }

        @Override
        public PaymentProofTemplate createPlatformTemplate(PaymentProofTemplateCommand command, UUID actorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentProofTemplate updatePlatformTemplate(UUID templateId, PaymentProofTemplateCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentProofTemplateContribution createContribution(StoreScope scope, PaymentProofTemplateContributionCommand command, UUID actorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PaymentProofTemplateContribution> findTenantContributions(StoreScope scope) { return List.of(); }

        @Override
        public List<PaymentProofTemplateContribution> findPlatformContributions(String status) { return List.of(); }

        @Override
        public PaymentProofTemplateContribution acceptContribution(UUID contributionId, UUID platformTemplateId, UUID actorId, String reviewNote, int version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentProofTemplateContribution rejectContribution(UUID contributionId, UUID actorId, String reviewNote, int version) {
            throw new UnsupportedOperationException();
        }
    }
}
