package com.rpb.reservation.reservation.policy;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: compute hold_until_at from Store policy. Input placeholder:
 * StorePolicy, reservation time, source. Output placeholder: RuleDecision or
 * computed value in later implementation. Failure placeholder:
 * reservation_hold_policy_missing. No database, controller, or UI dependency.
 */
public interface ReservationHoldPolicy {

    RuleDecision decide(ReservationHoldInput input);

    record ReservationHoldInput() implements RuleInput {
    }
}
