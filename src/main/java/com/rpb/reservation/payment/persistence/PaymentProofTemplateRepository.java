package com.rpb.reservation.payment.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.PaymentProofTemplate;
import com.rpb.reservation.payment.application.PaymentProofTemplateCommand;
import com.rpb.reservation.payment.application.PaymentProofTemplateContribution;
import com.rpb.reservation.payment.application.PaymentProofTemplateContributionCommand;
import com.rpb.reservation.payment.application.PaymentProofTemplateSample;
import com.rpb.reservation.payment.application.PaymentProofTemplateSampleCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentProofTemplateRepository {
    List<PaymentProofTemplate> findEffectiveTemplates(StoreScope scope);

    List<PaymentProofTemplate> findManageableTemplates(StoreScope scope);

    PaymentProofTemplate createTenantTemplate(StoreScope scope, PaymentProofTemplateCommand command, UUID actorId);

    Optional<PaymentProofTemplate> findTemplateForTenant(StoreScope scope, UUID templateId);

    PaymentProofTemplate updateTenantTemplate(StoreScope scope, UUID templateId, PaymentProofTemplateCommand command);

    PaymentProofTemplateSample createSample(
        StoreScope scope,
        UUID templateId,
        PaymentProofTemplateSampleCommand command,
        UUID actorId
    );

    List<PaymentProofTemplate> findPlatformTemplates();

    PaymentProofTemplate createPlatformTemplate(PaymentProofTemplateCommand command, UUID actorId);

    PaymentProofTemplate updatePlatformTemplate(UUID templateId, PaymentProofTemplateCommand command);

    PaymentProofTemplateContribution createContribution(
        StoreScope scope,
        PaymentProofTemplateContributionCommand command,
        UUID actorId
    );

    List<PaymentProofTemplateContribution> findTenantContributions(StoreScope scope);

    List<PaymentProofTemplateContribution> findPlatformContributions(String status);

    PaymentProofTemplateContribution acceptContribution(
        UUID contributionId,
        UUID platformTemplateId,
        UUID actorId,
        String reviewNote,
        int version
    );

    PaymentProofTemplateContribution rejectContribution(UUID contributionId, UUID actorId, String reviewNote, int version);
}
