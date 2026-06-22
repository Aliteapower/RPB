package com.rpb.reservation.seating.validator;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: enforce exactly one SeatingResource target and Store scope. Input
 * placeholder: seating, resource type/id, resource status. Output placeholder:
 * RuleDecision. Failure placeholder: invalid_seating_resource. No database,
 * controller, or UI dependency.
 */
public interface SeatingResourceValidator {

    RuleDecision validate(SeatingResourceInput input);

    record SeatingResourceInput() implements RuleInput {
    }
}
