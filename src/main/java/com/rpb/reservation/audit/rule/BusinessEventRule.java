package com.rpb.reservation.audit.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: determine required BusinessEvent for domain events such as CheckIn,
 * queue call, skip, rejoin, and table release. Input placeholder: event type,
 * target, actor/source, metadata, scope. Output placeholder: RuleDecision.
 * Failure placeholder: business_event_invalid. No database, controller, or UI
 * dependency.
 */
public interface BusinessEventRule {

    RuleDecision evaluate(BusinessEventInput input);

    record BusinessEventInput() implements RuleInput {
    }
}
