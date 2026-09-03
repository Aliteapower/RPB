package com.rpb.reservation.payment.application;

import java.util.UUID;

public record PaymentProofTemplateContributionReviewCommand(
    UUID platformTemplateId,
    Integer targetTemplateVersion,
    String reviewNote,
    Integer version
) {
}
