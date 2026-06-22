package com.rpb.reservation.reservation.policy;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: determine when confirmed/arrived Reservation can become no_show.
 * Input placeholder: reservation, StorePolicy, current time, actor/source,
 * reason. Output placeholder: RuleDecision. Failure placeholder:
 * reservation_no_show_not_allowed. No database, controller, or UI dependency.
 */
public interface NoShowPolicy {

    RuleDecision decide(NoShowInput input);

    record NoShowInput() implements RuleInput {
    }
}
