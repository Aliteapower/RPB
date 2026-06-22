package com.rpb.reservation.queue.application.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import java.util.UUID;

public class SeatingFromCalledQueueRule {

    public RuleDecision validateQueueTicketSource(UUID queueTicketId) {
        if (queueTicketId == null) {
            return RuleDecision.deny("invalid_seating_source");
        }
        return RuleDecision.allow();
    }
}
