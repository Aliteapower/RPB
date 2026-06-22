package com.rpb.reservation.cleaning.application.validator;

import com.rpb.reservation.common.rule.RuleDecision;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CleaningResourceValidator {

    public RuleDecision validate(String resourceType, UUID resourceId) {
        if (("dining_table".equals(resourceType) || "table_group".equals(resourceType)) && resourceId != null) {
            return RuleDecision.allow();
        }
        return RuleDecision.deny("resource_target_invalid");
    }
}
