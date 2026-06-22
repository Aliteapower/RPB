package com.rpb.reservation.reservation.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: decide Store time-slot capacity for a party size. Input placeholder:
 * scope, date/time, party size, policy, active reservation facts. Output
 * placeholder: RuleDecision. Failure placeholder: reservation_capacity_unavailable.
 * No database, controller, or UI dependency.
 */
public interface ReservationAvailabilityRule {

    RuleDecision evaluate(ReservationAvailabilityInput input);

    record ReservationAvailabilityInput() implements RuleInput {
    }
}
