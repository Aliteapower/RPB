package com.rpb.reservation.table.group.policy;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: govern fixed TableGroup lifecycle and recommendation eligibility.
 * Input placeholder: group type/status, members, StoreScope. Output placeholder:
 * RuleDecision. Failure placeholder: fixed_group_not_usable. No database,
 * controller, or UI dependency.
 */
public interface FixedTableGroupPolicy {

    RuleDecision decide(FixedTableGroupInput input);

    record FixedTableGroupInput() implements RuleInput {
    }
}
