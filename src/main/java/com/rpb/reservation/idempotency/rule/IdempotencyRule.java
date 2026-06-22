package com.rpb.reservation.idempotency.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: deduplicate critical commands and define replay decision. Input
 * placeholder: scope, source, action, idempotency key, request hash, existing
 * record. Output placeholder: RuleDecision. Failure placeholder:
 * idempotency_conflict. No database, controller, or UI dependency.
 */
public interface IdempotencyRule {

    RuleDecision evaluate(IdempotencyInput input);

    record IdempotencyInput() implements RuleInput {
    }
}
