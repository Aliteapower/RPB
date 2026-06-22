package com.rpb.reservation.queue.policy;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: select Store + party-size QueueGroup. Input placeholder: StoreScope,
 * party size, active groups. Output placeholder: RuleDecision or selected group
 * in later implementation. Failure placeholder: queue_group_not_found. No
 * database, controller, or UI dependency.
 */
public interface QueueGroupPolicy {

    RuleDecision decide(QueueGroupInput input);

    record QueueGroupInput() implements RuleInput {
    }
}
