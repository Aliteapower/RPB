package com.rpb.reservation.seating.validator;

import com.rpb.reservation.common.rule.RuleDecision;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultSeatingResourceValidator implements SeatingResourceValidator {

    @Override
    public RuleDecision validate(SeatingResourceInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision validate(String resourceType, UUID resourceId) {
        if (("dining_table".equals(resourceType) || "table_group".equals(resourceType)) && resourceId != null) {
            return RuleDecision.allow();
        }
        return RuleDecision.deny("invalid_seating_resource");
    }
}
