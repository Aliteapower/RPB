package com.rpb.reservation.common.time;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: enforce UTC instants, ISO8601 exchange, and Store-local business
 * date derivation. Input placeholder: instant/date, Store timezone, source.
 * Output placeholder: RuleDecision or normalized values later. Failure
 * placeholder: invalid_datetime. No database, controller, or UI dependency.
 */
public interface DateTimePolicy {

    RuleDecision decide(DateTimeInput input);

    record DateTimeInput() implements RuleInput {
    }
}
