package com.rpb.reservation.customer.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: decide whether Customer identity is sufficient for Reservation,
 * WalkIn, or QueueTicket. Input placeholder: TenantScope, type, code, phone,
 * lookup facts. Output placeholder: RuleDecision. Failure placeholder:
 * customer_identity_insufficient. No database, controller, or UI dependency.
 */
public interface CustomerIdentityRule {

    RuleDecision evaluate(CustomerIdentityInput input);

    record CustomerIdentityInput() implements RuleInput {
    }
}
