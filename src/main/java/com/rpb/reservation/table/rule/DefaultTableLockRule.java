package com.rpb.reservation.table.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import org.springframework.stereotype.Component;

@Component
public class DefaultTableLockRule implements TableLockRule {

    @Override
    public RuleDecision evaluate(TableLockInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision evaluate(boolean hasActiveConflict) {
        if (hasActiveConflict) {
            return RuleDecision.deny("table_lock_conflict");
        }
        return RuleDecision.allow();
    }
}
