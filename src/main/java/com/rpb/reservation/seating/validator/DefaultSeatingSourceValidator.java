package com.rpb.reservation.seating.validator;

import com.rpb.reservation.common.rule.RuleDecision;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultSeatingSourceValidator implements SeatingSourceValidator {

    @Override
    public RuleDecision validate(SeatingSourceInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision validate(String sourceType, UUID sourceId) {
        if ("walk_in".equals(sourceType) && sourceId != null) {
            return RuleDecision.allow();
        }
        return RuleDecision.deny("invalid_seating_source");
    }
}
