package com.rpb.reservation.store.policy;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: resolve Store currency for display/reporting boundaries. Input
 * placeholder: Store currency and Tenant defaults. Output placeholder:
 * RuleDecision or currency code later. Failure placeholder:
 * store_currency_missing. No database, controller, or UI dependency.
 */
public interface CurrencyPolicy {

    RuleDecision decide(CurrencyInput input);

    record CurrencyInput() implements RuleInput {
    }
}
