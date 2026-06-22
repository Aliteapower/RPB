package com.rpb.reservation.common.scope;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: decide whether an actor may act in Tenant scope. Input placeholder:
 * actor, role, tenant, source. Output placeholder: RuleDecision. Failure
 * placeholder: tenant_access_denied. No database, controller, or UI dependency.
 */
public interface TenantAccessPolicy {

    RuleDecision decide(TenantAccessInput input);

    record TenantAccessInput() implements RuleInput {
    }
}
