package com.rpb.reservation.audit.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultStateTransitionRule implements StateTransitionRule {

    @Override
    public RuleDecision evaluate(StateTransitionInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision evaluate(String targetType, UUID targetId, String toStatus, String transitionCode, String actorType) {
        if (hasText(targetType) && targetId != null && hasText(toStatus) && hasText(transitionCode) && hasText(actorType)) {
            return RuleDecision.allow();
        }
        return RuleDecision.deny("illegal_state_transition");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
