package com.rpb.reservation.audit.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: determine whether an operation requires AuditLog and snapshots.
 * Input placeholder: operation, actor, scope, target, before/after state,
 * reason, failure. Output placeholder: RuleDecision. Failure placeholder:
 * audit_required_missing. No database, controller, or UI dependency.
 */
public interface AuditRule {

    RuleDecision evaluate(AuditInput input);

    record AuditInput() implements RuleInput {
    }
}
