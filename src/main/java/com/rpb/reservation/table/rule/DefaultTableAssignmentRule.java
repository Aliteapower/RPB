package com.rpb.reservation.table.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import java.util.Collection;
import org.springframework.stereotype.Component;

@Component
public class DefaultTableAssignmentRule implements TableAssignmentRule {

    @Override
    public RuleDecision evaluate(TableAssignmentInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision evaluate(Collection<?> tableCandidates, Collection<?> groupCandidates) {
        boolean hasTable = tableCandidates != null && !tableCandidates.isEmpty();
        boolean hasGroup = groupCandidates != null && !groupCandidates.isEmpty();
        if (hasTable || hasGroup) {
            return RuleDecision.allow();
        }
        return RuleDecision.deny("no_assignable_table");
    }

    public RuleDecision evaluateManualOverride(boolean selectedRecommendedResource, String reasonCode, String note) {
        if (selectedRecommendedResource || hasText(reasonCode) || hasText(note)) {
            return RuleDecision.allow();
        }
        return RuleDecision.deny("manual_override_required");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
