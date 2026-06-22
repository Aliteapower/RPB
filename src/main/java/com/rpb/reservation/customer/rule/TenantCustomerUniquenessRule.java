package com.rpb.reservation.customer.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: enforce Customer uniqueness inside Tenant, not Store or Platform.
 * Input placeholder: TenantScope, customer code, phone, merge target. Output
 * placeholder: RuleDecision. Failure placeholder: customer_duplicate_in_tenant.
 * No database, controller, or UI dependency.
 */
public interface TenantCustomerUniquenessRule {

    RuleDecision evaluate(TenantCustomerUniquenessInput input);

    record TenantCustomerUniquenessInput() implements RuleInput {
    }
}
