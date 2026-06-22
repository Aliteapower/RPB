package com.rpb.reservation.queue.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: validate skipped ticket rejoin while preserving original number.
 * Input placeholder: ticket, StorePolicy, queue facts. Output placeholder:
 * RuleDecision. Failure placeholder: queue_ticket_not_rejoinable. No database,
 * controller, or UI dependency.
 */
public interface QueueRejoinRule {

    RuleDecision evaluate(QueueRejoinInput input);

    record QueueRejoinInput() implements RuleInput {
    }
}
