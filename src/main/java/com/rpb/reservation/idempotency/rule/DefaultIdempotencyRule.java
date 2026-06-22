package com.rpb.reservation.idempotency.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import org.springframework.stereotype.Component;

@Component
public class DefaultIdempotencyRule implements IdempotencyRule {

    @Override
    public RuleDecision evaluate(IdempotencyInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision evaluate(IdempotencyRecord existing, String requestHash) {
        if (existing == null) {
            return RuleDecision.allow();
        }
        if (!existing.requestHash().equals(requestHash)) {
            return RuleDecision.deny("idempotency_conflict");
        }
        if (existing.status() == IdempotencyStatus.STARTED) {
            return RuleDecision.deny("command_in_progress");
        }
        if (existing.status() == IdempotencyStatus.FAILED) {
            return RuleDecision.deny("failed_idempotency_requires_new_key");
        }
        return RuleDecision.allow();
    }
}
