package com.rpb.reservation.audit.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultAuditRule implements AuditRule {

    @Override
    public RuleDecision evaluate(AuditInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision evaluate(String operationCode, String targetType, UUID targetId, String actorType) {
        if (hasText(operationCode) && hasText(targetType) && targetId != null && hasText(actorType)) {
            return RuleDecision.allow();
        }
        return RuleDecision.deny("audit_required_missing");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
