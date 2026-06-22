package com.rpb.reservation.customer.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.value.E164Phone;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultCustomerIdentityRule implements CustomerIdentityRule {

    @Override
    public RuleDecision evaluate(CustomerIdentityInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision evaluate(UUID customerId, String customerName, String customerNickname, E164Phone phone) {
        if (customerId != null || hasText(customerName) || hasText(customerNickname) || (phone != null && phone.isPresent())) {
            return RuleDecision.allow();
        }
        return RuleDecision.deny("invalid_customer_identity");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
