package com.rpb.reservation.queue.policy;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: determine QueueTicket expiry. Input placeholder: ticket status,
 * timestamps, StorePolicy, current time. Output placeholder: RuleDecision.
 * Failure placeholder: queue_expiry_policy_missing. No database, controller,
 * or UI dependency.
 */
public interface QueueExpiryPolicy {

    RuleDecision decide(QueueExpiryInput input);

    record QueueExpiryInput() implements RuleInput {
    }
}
