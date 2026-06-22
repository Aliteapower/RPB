package com.rpb.reservation.table.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: guard table/group lock acquisition and release. Input placeholder:
 * scope, resource, owner, source, expiry, idempotency key. Output placeholder:
 * RuleDecision. Failure placeholder: table_lock_conflict. No database,
 * controller, or UI dependency.
 */
public interface TableLockRule {

    RuleDecision evaluate(TableLockInput input);

    record TableLockInput() implements RuleInput {
    }
}
