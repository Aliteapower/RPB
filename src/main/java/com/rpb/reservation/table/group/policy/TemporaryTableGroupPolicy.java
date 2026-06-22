package com.rpb.reservation.table.group.policy;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: govern temporary TableGroup creation, use, release, and end. Input
 * placeholder: candidate tables, seating, lock facts, cleaning facts. Output
 * placeholder: RuleDecision. Failure placeholder: temporary_group_not_usable.
 * No database, controller, or UI dependency.
 */
public interface TemporaryTableGroupPolicy {

    RuleDecision decide(TemporaryTableGroupInput input);

    record TemporaryTableGroupInput() implements RuleInput {
    }
}
