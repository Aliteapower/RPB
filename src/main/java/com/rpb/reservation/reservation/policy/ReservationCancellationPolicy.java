package com.rpb.reservation.reservation.policy;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: determine whether Reservation cancellation is allowed. Input
 * placeholder: status, actor/source, current time, reason. Output placeholder:
 * RuleDecision. Failure placeholder: reservation_cancellation_not_allowed. No
 * database, controller, or UI dependency.
 */
public interface ReservationCancellationPolicy {

    RuleDecision decide(ReservationCancellationInput input);

    record ReservationCancellationInput() implements RuleInput {
    }
}
