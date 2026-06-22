package com.rpb.reservation.reservation.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: block duplicate active same-customer reservation slots. Input
 * placeholder: scope, customer, time range, active statuses. Output placeholder:
 * RuleDecision. Failure placeholder: duplicate_active_reservation_slot. No
 * database, controller, or UI dependency.
 */
public interface ReservationDuplicateRule {

    RuleDecision evaluate(ReservationDuplicateInput input);

    record ReservationDuplicateInput() implements RuleInput {
    }
}
