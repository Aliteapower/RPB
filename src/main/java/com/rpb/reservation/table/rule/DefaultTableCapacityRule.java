package com.rpb.reservation.table.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.common.value.PartySize;
import org.springframework.stereotype.Component;

@Component
public class DefaultTableCapacityRule implements TableCapacityRule {

    @Override
    public RuleDecision evaluate(TableCapacityInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision evaluate(PartySize partySize, CapacityRange capacity) {
        if (partySize == null || capacity == null || !capacity.includes(partySize)) {
            return RuleDecision.deny("party_size_outside_capacity");
        }
        return RuleDecision.allow();
    }
}
