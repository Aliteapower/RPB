package com.rpb.reservation.audit.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultBusinessEventRule implements BusinessEventRule {

    @Override
    public RuleDecision evaluate(BusinessEventInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision evaluate(String eventType, String targetType, UUID targetId, String actorType) {
        if (hasText(eventType) && hasText(targetType) && targetId != null && hasText(actorType)) {
            return RuleDecision.allow();
        }
        return RuleDecision.deny("business_event_invalid");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
