package com.rpb.reservation.common.scope;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: decide whether an actor may act in Store operation scope. Input
 * placeholder: actor, role, store, source. Output placeholder: RuleDecision.
 * Failure placeholder: store_access_denied. No database, controller, or UI
 * dependency.
 */
public interface StoreAccessPolicy {

    RuleDecision decide(StoreAccessInput input);

    record StoreAccessInput() implements RuleInput {
    }
}
