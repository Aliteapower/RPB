package com.rpb.reservation.queue.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: validate calling a waiting QueueTicket and call-hold behavior. Input
 * placeholder: ticket, StorePolicy, current time, actor/source. Output
 * placeholder: RuleDecision. Failure placeholder: queue_ticket_not_callable. No
 * database, controller, or UI dependency.
 */
public interface QueueCallingRule {

    RuleDecision evaluate(QueueCallingInput input);

    record QueueCallingInput() implements RuleInput {
    }
}
