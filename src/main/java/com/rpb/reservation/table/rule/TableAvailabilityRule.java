package com.rpb.reservation.table.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: decide whether a DiningTable or TableGroup can be used. Input
 * placeholder: scope, resource, status, locks, seating, cleaning, group facts.
 * Output placeholder: RuleDecision. Failure placeholder:
 * table_resource_unavailable. No database, controller, or UI dependency.
 */
public interface TableAvailabilityRule {

    RuleDecision evaluate(TableAvailabilityInput input);

    record TableAvailabilityInput() implements RuleInput {
    }
}
