package com.rpb.reservation.queue.policy;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: compute ticket number and queue position; default rejoin goes to
 * same-group tail. Input placeholder: StoreScope, QueueGroup, business date,
 * queue facts. Output placeholder: RuleDecision or ordering value later.
 * Failure placeholder: queue_ordering_conflict. No database, controller, or UI
 * dependency.
 */
public interface QueueOrderingPolicy {

    RuleDecision decide(QueueOrderingInput input);

    record QueueOrderingInput() implements RuleInput {
    }
}
