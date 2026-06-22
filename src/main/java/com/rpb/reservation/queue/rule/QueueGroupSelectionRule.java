package com.rpb.reservation.queue.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.queue.domain.QueueGroup;

public class QueueGroupSelectionRule {

    public String defaultGroupCode(PartySize partySize) {
        if (partySize == null) {
            return null;
        }
        int value = partySize.value();
        if (value <= 2) {
            return "1-2";
        }
        if (value <= 4) {
            return "3-4";
        }
        if (value <= 6) {
            return "5-6";
        }
        return "7+";
    }

    public RuleDecision validate(QueueGroup queueGroup, PartySize partySize) {
        if (queueGroup == null) {
            return RuleDecision.deny("queue_group_not_found");
        }
        if (!queueGroup.active()) {
            return RuleDecision.deny("queue_group_not_found");
        }
        if (!queueGroup.covers(partySize)) {
            return RuleDecision.deny("queue_group_party_size_mismatch");
        }
        return RuleDecision.allow();
    }
}
