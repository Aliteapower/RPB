package com.rpb.reservation.table.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: validate party size against capacity_min/capacity_max. Input
 * placeholder: party size and resource capacity range. Output placeholder:
 * RuleDecision. Failure placeholder: party_size_outside_capacity. No database,
 * controller, or UI dependency.
 */
public interface TableCapacityRule {

    RuleDecision evaluate(TableCapacityInput input);

    record TableCapacityInput() implements RuleInput {
    }
}
