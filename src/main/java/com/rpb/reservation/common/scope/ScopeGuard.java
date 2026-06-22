package com.rpb.reservation.common.scope;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: validate that referenced objects stay inside the same Tenant/Store
 * scope. Input placeholder: source and target scope facts. Output placeholder:
 * RuleDecision. Failure placeholder: cross_tenant_reference or
 * cross_store_reference. No database, controller, or UI dependency.
 */
public interface ScopeGuard {

    RuleDecision validate(ScopeGuardInput input);

    record ScopeGuardInput() implements RuleInput {
    }
}
