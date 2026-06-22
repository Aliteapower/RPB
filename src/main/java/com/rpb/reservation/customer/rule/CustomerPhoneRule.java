package com.rpb.reservation.customer.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: validate nullable E.164 phone and Tenant-scoped uniqueness facts.
 * Input placeholder: TenantScope, phone value, active phone facts. Output
 * placeholder: RuleDecision. Failure placeholder: invalid_phone_e164 or
 * customer_phone_duplicate. No database, controller, or UI dependency.
 */
public interface CustomerPhoneRule {

    RuleDecision evaluate(CustomerPhoneInput input);

    record CustomerPhoneInput() implements RuleInput {
    }
}
