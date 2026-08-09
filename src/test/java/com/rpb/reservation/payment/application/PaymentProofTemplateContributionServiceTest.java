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
    private static final UUID OTHER_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
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
            new PaymentProofTemplateContributionReviewCommand(null, null, "accepted", submitted.version()),
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
            new PaymentProofTemplateContributionReviewCommand(null, null, "bad actor", submitted.version()),
            tenantActor()
        )).isInstanceOf(PaymentServiceException.class);
    }

    @Test
    void contributionCannotBeAcceptedIntoTenantTemplate() {
        PaymentProofTemplateContribution submitted = service.submitContribution(scope(), contributionCommand(), tenantActor());

        assertThatThrownBy(() -> service.acceptContribution(
            submitted.id(),
            new PaymentProofTemplateContributionReviewCommand(UUID.randomUUID(), 0, "wrong scope", submitted.version()),
            platformActor()
        )).isInstanceOf(PaymentServiceException.class);
    }

    @Test
    void acceptingIntoExistingPlatformTemplateUpdatesItsContributionFields() {
        PaymentProofTemplate existing = repository.addPlatformTemplate("Previous template", "inactive", "{\"matchKeywords\":[\"OLD\"]}");
        PaymentProofTemplateContribution submitted = service.submitContribution(scope(), contributionCommand(), tenantActor());

        service.acceptContribution(
            submitted.id(),
            new PaymentProofTemplateContributionReviewCommand(existing.id(), existing.version(), "apply contribution", submitted.version()),
            platformActor()
        );

        PaymentProofTemplate updated = repository.findPlatformTemplates().getFirst();
        assertThat(updated.templateName()).isEqualTo("OCBC new receipt");
        assertThat(updated.layoutJson()).contains("OCBC");
        assertThat(updated.status()).isEqualTo("active");
        assertThat(updated.version()).isEqualTo(existing.version() + 1);
    }

    @Test
    void acceptingIntoExistingPlatformTemplateRequiresTargetVersion() {
        PaymentProofTemplate existing = repository.addPlatformTemplate("Previous template", "inactive", "{\"matchKeywords\":[\"OLD\"]}");
        PaymentProofTemplateContribution submitted = service.submitContribution(scope(), contributionCommand(), tenantActor());

        assertThatThrownBy(() -> service.acceptContribution(
            submitted.id(),
            new PaymentProofTemplateContributionReviewCommand(existing.id(), null, "apply contribution", submitted.version()),
            platformActor()
        )).isInstanceOf(PaymentServiceException.class)
            .extracting(error -> ((PaymentServiceException) error).code())
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void staleTargetTemplateVersionReturnsVersionConflict() {
        PaymentProofTemplate existing = repository.addPlatformTemplate("Previous template", "inactive", "{\"matchKeywords\":[\"OLD\"]}");
        PaymentProofTemplateContribution submitted = service.submitContribution(scope(), contributionCommand(), tenantActor());

        assertThatThrownBy(() -> service.acceptContribution(
            submitted.id(),
            new PaymentProofTemplateContributionReviewCommand(existing.id(), existing.version() - 1, "apply contribution", submitted.version()),
            platformActor()
        )).isInstanceOf(PaymentServiceException.class)
            .extracting(error -> ((PaymentServiceException) error).code())
            .isEqualTo(PaymentServiceErrorCode.VERSION_CONFLICT);

        assertThat(repository.findPlatformTemplates().getFirst()).isEqualTo(existing);
        assertThat(repository.findPlatformContributions(null).getFirst().status()).isEqualTo("submitted");
    }

    @Test
    void contributionSubmissionRejectsMalformedRegexRules() {
        PaymentProofTemplateContributionCommand command = contributionCommandWithLayout(
            "{\"matchKeywords\":[\"OCBC\"],\"referencePatterns\":[\"[\"]}"
        );

        assertThatThrownBy(() -> service.submitContribution(scope(), command, tenantActor()))
            .isInstanceOf(PaymentServiceException.class)
            .extracting(error -> ((PaymentServiceException) error).code())
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void platformTemplateCreateRejectsMalformedRegexRules() {
        PaymentProofTemplateCommand command = new PaymentProofTemplateCommand(
            "ocbc", "OCBC", "zh-CN", "Broken rule", "active", 20,
            "{\"matchKeywords\":[\"OCBC\"],\"amountPatterns\":[\"[\"]}", 0
        );

        assertThatThrownBy(() -> service.createPlatformTemplate(command, platformActor()))
            .isInstanceOf(PaymentServiceException.class)
            .extracting(error -> ((PaymentServiceException) error).code())
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void contributionAcceptanceRevalidatesPersistedRulePatterns() {
        PaymentProofTemplateContribution submitted = service.submitContribution(scope(), contributionCommand(), tenantActor());
        repository.replaceContributionLayout(submitted.id(), "{\"matchKeywords\":[\"OCBC\"],\"amountPatterns\":[\"[\"]}");

        assertThatThrownBy(() -> service.acceptContribution(
            submitted.id(),
            new PaymentProofTemplateContributionReviewCommand(null, null, "accepted", submitted.version()),
            platformActor()
        )).isInstanceOf(PaymentServiceException.class)
            .extracting(error -> ((PaymentServiceException) error).code())
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);

        assertThat(repository.platformTemplates).isEmpty();
    }

    @Test
    void rejectContributionRequiresNonBlankReviewNote() {
        PaymentProofTemplateContribution submitted = service.submitContribution(scope(), contributionCommand(), tenantActor());

        assertThatThrownBy(() -> service.rejectContribution(
            submitted.id(),
            new PaymentProofTemplateContributionReviewCommand(null, null, "  ", submitted.version()),
            platformActor()
        )).isInstanceOf(PaymentServiceException.class)
            .extracting(error -> ((PaymentServiceException) error).code())
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void platformTemplateCannotBeSubmittedAsContributionSource() {
        repository.sourceTemplate = template(TENANT_TEMPLATE_ID, null, "platform_seed");

        assertThatThrownBy(() -> service.submitContribution(scope(), contributionCommand(), tenantActor()))
            .isInstanceOf(PaymentServiceException.class)
            .extracting(error -> ((PaymentServiceException) error).code())
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void foreignTemplateCannotBeSubmittedAsContributionSource() {
        repository.sourceTemplate = template(TENANT_TEMPLATE_ID, OTHER_TENANT_ID, "tenant_custom");

        assertThatThrownBy(() -> service.submitContribution(scope(), contributionCommand(), tenantActor()))
            .isInstanceOf(PaymentServiceException.class)
            .extracting(error -> ((PaymentServiceException) error).code())
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void acceptedContributionCannotBeRejected() {
        PaymentProofTemplateContribution submitted = service.submitContribution(scope(), contributionCommand(), tenantActor());
        PaymentProofTemplateContribution accepted = service.acceptContribution(
            submitted.id(), new PaymentProofTemplateContributionReviewCommand(null, null, "accepted", submitted.version()), platformActor()
        );

        assertThatThrownBy(() -> service.rejectContribution(
            accepted.id(), new PaymentProofTemplateContributionReviewCommand(null, null, "too late", accepted.version()), platformActor()
        )).isInstanceOf(PaymentServiceException.class)
            .extracting(error -> ((PaymentServiceException) error).code())
            .isEqualTo(PaymentServiceErrorCode.REQUEST_INVALID);
    }

    @Test
    void staleSubmittedContributionReviewReturnsVersionConflict() {
        PaymentProofTemplateContribution submitted = service.submitContribution(scope(), contributionCommand(), tenantActor());

        assertThatThrownBy(() -> service.rejectContribution(
            submitted.id(), new PaymentProofTemplateContributionReviewCommand(null, null, "stale", submitted.version() + 1), platformActor()
        )).isInstanceOf(PaymentServiceException.class)
            .extracting(error -> ((PaymentServiceException) error).code())
            .isEqualTo(PaymentServiceErrorCode.VERSION_CONFLICT);
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
        return contributionCommandWithLayout(
            "{\"matchKeywords\":[\"OCBC\"],\"successKeywords\":[\"Payment successful\"]}"
        );
    }

    private static PaymentProofTemplateContributionCommand contributionCommandWithLayout(String layoutJson) {
        return new PaymentProofTemplateContributionCommand(
            TENANT_TEMPLATE_ID,
            "ocbc",
            "OCBC",
            "zh-CN",
            "OCBC new receipt",
            layoutJson,
            "receipt.jpg",
            "image/jpeg",
            "digest",
            "QP202608090016MERK",
            new BigDecimal("0.50"),
            "OCBC raw text"
        );
    }

    private static PaymentProofTemplate template(UUID id, UUID tenantId, String source) {
        return new PaymentProofTemplate(
            id, tenantId, "ocbc", "OCBC", "zh-CN", "Tenant source", source, "active", 100, 0,
            "{\"matchKeywords\":[\"OCBC\"]}", OffsetDateTime.now(), OffsetDateTime.now()
        );
    }

    private static final class InMemoryTemplateRepository implements PaymentProofTemplateRepository {
        private final List<PaymentProofTemplate> platformTemplates = new ArrayList<>();
        private final List<PaymentProofTemplateContribution> contributions = new ArrayList<>();
        private PaymentProofTemplate sourceTemplate = template(TENANT_TEMPLATE_ID, TENANT_ID, "tenant_custom");

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
            if (!sourceTemplate.id().equals(templateId)) {
                return Optional.empty();
            }
            return Optional.of(sourceTemplate);
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
            PaymentProofTemplate current = platformTemplates.stream()
                .filter(template -> template.id().equals(templateId) && template.version() == command.version())
                .findFirst()
                .orElseThrow(() -> new PaymentServiceException(PaymentServiceErrorCode.VERSION_CONFLICT));
            PaymentProofTemplate updated = new PaymentProofTemplate(
                current.id(), null, command.bankCode(), command.bankName(), command.locale(), command.templateName(),
                "platform_seed", command.status(), command.priority(), current.version() + 1, command.layoutJson(),
                current.createdAt(), OffsetDateTime.now()
            );
            platformTemplates.set(platformTemplates.indexOf(current), updated);
            return updated;
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
            PaymentProofTemplateContribution current = contributions.stream()
                .filter(value -> value.id().equals(contributionId) && value.version() == version).findFirst().orElseThrow();
            PaymentProofTemplateContribution rejected = new PaymentProofTemplateContribution(
                current.id(), current.tenantId(), current.storeId(), current.sourceTemplateId(), null,
                current.bankCode(), current.bankName(), current.locale(), current.templateName(), current.layoutJson(),
                current.sampleFileName(), current.sampleContentType(), current.sampleFileDigest(), current.sampleRawText(),
                current.sampleOcrReference(), current.sampleOcrAmount(), "rejected", reviewNote, current.submittedBy(), actorId,
                current.createdAt(), OffsetDateTime.now(), OffsetDateTime.now(), current.version() + 1
            );
            contributions.set(contributions.indexOf(current), rejected);
            return rejected;
        }

        private PaymentProofTemplate addPlatformTemplate(String templateName, String status, String layoutJson) {
            PaymentProofTemplate template = new PaymentProofTemplate(
                UUID.randomUUID(), null, "old", "Old Bank", "en-SG", templateName, "platform_seed", status,
                75, 4, layoutJson, OffsetDateTime.now(), OffsetDateTime.now()
            );
            platformTemplates.add(template);
            return template;
        }

        private void replaceContributionLayout(UUID contributionId, String layoutJson) {
            PaymentProofTemplateContribution contribution = contributions.stream()
                .filter(value -> value.id().equals(contributionId))
                .findFirst()
                .orElseThrow();
            PaymentProofTemplateContribution replacement = new PaymentProofTemplateContribution(
                contribution.id(), contribution.tenantId(), contribution.storeId(), contribution.sourceTemplateId(),
                contribution.platformTemplateId(), contribution.bankCode(), contribution.bankName(), contribution.locale(),
                contribution.templateName(), layoutJson, contribution.sampleFileName(), contribution.sampleContentType(),
                contribution.sampleFileDigest(), contribution.sampleRawText(), contribution.sampleOcrReference(),
                contribution.sampleOcrAmount(), contribution.status(), contribution.reviewNote(), contribution.submittedBy(),
                contribution.reviewedBy(), contribution.createdAt(), contribution.updatedAt(), contribution.reviewedAt(),
                contribution.version()
            );
            contributions.set(contributions.indexOf(contribution), replacement);
        }
    }
}
