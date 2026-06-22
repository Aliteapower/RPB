package com.rpb.reservation.customer.policy;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: define allowed anonymous/temporary Customer usage. Input
 * placeholder: scenario, StoreScope, customer type, lookup note. Output
 * placeholder: RuleDecision. Failure placeholder: anonymous_customer_not_allowed.
 * No database, controller, or UI dependency.
 */
public interface AnonymousCustomerPolicy {

    RuleDecision decide(AnonymousCustomerInput input);

    record AnonymousCustomerInput() implements RuleInput {
    }
}
