package com.rpb.reservation.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.persistence.PaymentProofTemplateRepository;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentProofTemplateContributionServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID TENANT_ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID PLATFORM_ACTOR_ID = UUID.fromString("40000000-0000-0000-0000-000000000003");
    private static final UUID TENANT_TEMPLATE_ID = UUID.fromString("50000000-0000-0000-0000-000000000003");

    private final InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
    private final PaymentProofTemplateService service = new PaymentProofTemplateService(repository);

    @Test
    void tenantContributionCanBeAcceptedIntoPlatformTemplate() {
        PaymentProofTemplateContribution submitted = service.submitContribution(
            scope(),
            contributionCommand(),
            tenantActor()
        );

        PaymentProofTemplateContribution accepted = service.acceptContribution(
            submitted.id(),
            new PaymentProofTemplateContributionReviewCommand(null, "accepted", submitted.version()),
            platformActor()
        );

        assertThat(accepted.status()).isEqualTo("accepted");
        assertThat(accepted.platformTemplateId()).isNotNull();
        assertThat(repository.platformTemplates).hasSize(1);
        assertThat(repository.platformTemplates.getFirst().status()).isEqualTo("active");
    }

    @Test
    void tenantActorCannotAcceptContribution() {
        PaymentProofTemplateContribution submitted = service.submitContribution(scope(), contributionCommand(), tenantActor());

        assertThatThrownBy(() -> service.acceptContribution(
            submitted.id(),
            new PaymentProofTemplateContributionReviewCommand(null, "bad actor", submitted.version()),
            tenantActor()
        )).isInstanceOf(PaymentServiceException.class);
    }

    @Test
    void contributionCannotBeAcceptedIntoTenantTemplate() {
        PaymentProofTemplateContribution submitted = service.submitContribution(scope(), contributionCommand(), tenantActor());

        assertThatThrownBy(() -> service.acceptContribution(
            submitted.id(),
            new PaymentProofTemplateContributionReviewCommand(UUID.randomUUID(), "wrong scope", submitted.version()),
            platformActor()
        )).isInstanceOf(PaymentServiceException.class);
    }

    private static StoreScope scope() {
        return new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));
    }

    private static CurrentActor tenantActor() {
        return new CurrentActor(
            TENANT_ID,
            TENANT_ACTOR_ID,
            "tenant_admin",
            Set.of("tenant_admin"),
            Set.of("payment.proof_template.manage"),
            Set.of(STORE_ID)
        );
    }

    private static CurrentActor platformActor() {
        return new CurrentActor(
            null,
            PLATFORM_ACTOR_ID,
            "platform_admin",
            Set.of("platform_admin"),
            Set.of("platform.payment_proof_template.manage"),
            Set.of()
        );
    }

    private static PaymentProofTemplateContributionCommand contributionCommand() {
        return new PaymentProofTemplateContributionCommand(
            TENANT_TEMPLATE_ID,
            "ocbc",
            "OCBC",
            "zh-CN",
            "OCBC new receipt",
            "{\"matchKeywords\":[\"OCBC\"],\"successKeywords\":[\"Payment successful\"]}",
            "receipt.jpg",
            "image/jpeg",
            "digest",
            "QP202608090016MERK",
            new BigDecimal("0.50"),
            "OCBC raw text"
        );
    }

    private static final class InMemoryTemplateRepository implements PaymentProofTemplateRepository {
        private final List<PaymentProofTemplate> platformTemplates = new ArrayList<>();
        private final List<PaymentProofTemplateContribution> contributions = new ArrayList<>();

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
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PaymentProofTemplate> findTemplateForTenant(StoreScope scope, UUID templateId) {
            return Optional.empty();
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
        public List<PaymentProofTemplate> findPlatformTemplates() {
            return List.copyOf(platformTemplates);
        }

        @Override
        public PaymentProofTemplate createPlatformTemplate(PaymentProofTemplateCommand command, UUID actorId) {
            PaymentProofTemplate template = new PaymentProofTemplate(
                UUID.randomUUID(), null, command.bankCode(), command.bankName(), command.locale(), command.templateName(),
                "platform_seed", command.status(), command.priority(), 0, command.layoutJson(), OffsetDateTime.now(), OffsetDateTime.now()
            );
            platformTemplates.add(template);
            return template;
        }

        @Override
        public PaymentProofTemplate updatePlatformTemplate(UUID templateId, PaymentProofTemplateCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentProofTemplateContribution createContribution(
            StoreScope scope,
            PaymentProofTemplateContributionCommand command,
            UUID actorId
        ) {
            PaymentProofTemplateContribution contribution = new PaymentProofTemplateContribution(
                UUID.randomUUID(), scope.tenantId().value(), scope.storeId().value(), command.sourceTemplateId(), null,
                command.bankCode(), command.bankName(), command.locale(), command.templateName(), command.layoutJson(),
                command.sampleFileName(), command.sampleContentType(), command.sampleFileDigest(), command.sampleRawText(),
                command.sampleOcrReference(), command.sampleOcrAmount(), "submitted", null, actorId, null,
                OffsetDateTime.now(), OffsetDateTime.now(), null, 0
            );
            contributions.add(contribution);
            return contribution;
        }

        @Override
        public List<PaymentProofTemplateContribution> findTenantContributions(StoreScope scope) {
            return contributions.stream().filter(value -> value.tenantId().equals(scope.tenantId().value())).toList();
        }

        @Override
        public List<PaymentProofTemplateContribution> findPlatformContributions(String status) {
            return contributions.stream().filter(value -> status == null || status.equals(value.status())).toList();
        }

        @Override
        public PaymentProofTemplateContribution acceptContribution(
            UUID contributionId,
            UUID platformTemplateId,
            UUID actorId,
            String reviewNote,
            int version
        ) {
            PaymentProofTemplateContribution current = contributions.stream()
                .filter(value -> value.id().equals(contributionId) && value.version() == version).findFirst().orElseThrow();
            PaymentProofTemplateContribution accepted = new PaymentProofTemplateContribution(
                current.id(), current.tenantId(), current.storeId(), current.sourceTemplateId(), platformTemplateId,
                current.bankCode(), current.bankName(), current.locale(), current.templateName(), current.layoutJson(),
                current.sampleFileName(), current.sampleContentType(), current.sampleFileDigest(), current.sampleRawText(),
                current.sampleOcrReference(), current.sampleOcrAmount(), "accepted", reviewNote, current.submittedBy(), actorId,
                current.createdAt(), OffsetDateTime.now(), OffsetDateTime.now(), current.version() + 1
            );
            contributions.set(contributions.indexOf(current), accepted);
            return accepted;
        }

        @Override
        public PaymentProofTemplateContribution rejectContribution(UUID contributionId, UUID actorId, String reviewNote, int version) {
            throw new UnsupportedOperationException();
        }
    }
}
