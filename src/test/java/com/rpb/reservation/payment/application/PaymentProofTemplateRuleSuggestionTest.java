package com.rpb.reservation.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.persistence.PaymentProofTemplateRepository;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentProofTemplateRuleSuggestionTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private final NoWriteTemplateRepository repository = new NoWriteTemplateRepository();

    @Test
    void suggestRuleBuildsJsonFromOcrTextWithoutSavingTemplate() {
        PaymentProofTemplateService service = new PaymentProofTemplateService(repository, adapterReturning(fields()));

        PaymentProofTemplateRuleSuggestion suggestion = service.suggestRule(
            scope(),
            "ocbc.jpg",
            "image/jpeg",
            "receipt".getBytes(StandardCharsets.UTF_8),
            "ocbc",
            "OCBC",
            "zh-CN",
            tenantActor()
        );

        assertThat(suggestion.bankCode()).isEqualTo("ocbc");
        assertThat(suggestion.bankName()).isEqualTo("OCBC");
        assertThat(suggestion.locale()).isEqualTo("zh-CN");
        assertThat(suggestion.templateName()).isEqualTo("OCBC PayNow");
        assertThat(suggestion.suggestedLayoutJson())
            .contains("matchKeywords")
            .contains("referencePatterns")
            .contains("amountPatterns");
        assertThat(suggestion.ocr().extractedReference()).isEqualTo("QP202608090016MERK");
        assertThat(repository.writeAttempted).isFalse();
    }

    @Test
    void suggestRuleRejectsUnsupportedImageTypes() {
        PaymentProofTemplateService service = new PaymentProofTemplateService(repository, adapterReturning(fields()));

        assertThatThrownBy(() -> service.suggestRule(
            scope(),
            "receipt.gif",
            "image/gif",
            "receipt".getBytes(StandardCharsets.UTF_8),
            "ocbc",
            "OCBC",
            "zh-CN",
            tenantActor()
        )).isInstanceOf(PaymentServiceException.class);
    }

    @Test
    void suggestPlatformRuleBuildsTheSameDraftWithoutRepositoryWrites() {
        PaymentProofTemplateService service = new PaymentProofTemplateService(repository, adapterReturning(fields()));

        PaymentProofTemplateRuleSuggestion suggestion = service.suggestPlatformRule(
            "ocbc.webp",
            "image/webp",
            "receipt".getBytes(StandardCharsets.UTF_8),
            "ocbc",
            "OCBC",
            "zh-CN"
        );

        assertThat(suggestion.suggestedLayoutJson()).contains("OCBC").contains("您已支付");
        assertThat(suggestion.ocr()).isEqualTo(fields());
        assertThat(repository.writeAttempted).isFalse();
    }

    private static PaymentProofOcrAdapter adapterReturning(PaymentProofOcrFields fields) {
        return (Path file, PaymentProofOcrExpected expected) -> fields;
    }

    private static PaymentProofOcrFields fields() {
        return new PaymentProofOcrFields(
            "QP202608090016MERK",
            new BigDecimal("0.50"),
            null,
            "ocbc",
            true,
            new BigDecimal("0.7300"),
            "OCBC\n您已支付 0.50 SGD\n讯息\nQP202608090016MERK\n转账日期\n9 Aug 2026",
            "{}"
        );
    }

    private static StoreScope scope() {
        return new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));
    }

    private static CurrentActor tenantActor() {
        return new CurrentActor(TENANT_ID, UUID.fromString("30000000-0000-0000-0000-000000000001"), "user", Set.of(), Set.of(), Set.of(STORE_ID));
    }

    private static final class NoWriteTemplateRepository implements PaymentProofTemplateRepository {
        private boolean writeAttempted;

        @Override
        public List<PaymentProofTemplate> findEffectiveTemplates(StoreScope scope) {
            return List.of();
        }

        @Override
        public List<PaymentProofTemplate> findManageableTemplates(StoreScope scope) {
            return List.of();
        }

        @Override
        public PaymentProofTemplate createTenantTemplate(StoreScope scope, PaymentProofTemplateCommand command, UUID actorId) {
            return write();
        }

        @Override
        public Optional<PaymentProofTemplate> findTemplateForTenant(StoreScope scope, UUID templateId) {
            return Optional.empty();
        }

        @Override
        public PaymentProofTemplate updateTenantTemplate(StoreScope scope, UUID templateId, PaymentProofTemplateCommand command) {
            return write();
        }

        @Override
        public PaymentProofTemplateSample createSample(
            StoreScope scope,
            UUID templateId,
            PaymentProofTemplateSampleCommand command,
            UUID actorId
        ) {
            writeAttempted = true;
            throw new AssertionError("rule suggestion must not persist samples");
        }

        private PaymentProofTemplate write() {
            writeAttempted = true;
            throw new AssertionError("rule suggestion must not persist templates");
        }
    }
}
