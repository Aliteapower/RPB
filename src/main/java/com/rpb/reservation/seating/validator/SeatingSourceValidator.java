package com.rpb.reservation.seating.validator;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: enforce exactly one Seating source among Reservation, QueueTicket,
 * and WalkIn. Input placeholder: source ids, source statuses, scope. Output
 * placeholder: RuleDecision. Failure placeholder: invalid_seating_source. No
 * database, controller, or UI dependency.
 */
public interface SeatingSourceValidator {

    RuleDecision validate(SeatingSourceInput input);

    record SeatingSourceInput() implements RuleInput {
    }
}
