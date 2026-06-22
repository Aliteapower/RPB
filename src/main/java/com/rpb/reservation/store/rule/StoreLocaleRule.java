package com.rpb.reservation.store.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: resolve Store locale, date format, time format, and display
 * preferences. Input placeholder: Store and Tenant defaults. Output placeholder:
 * RuleDecision or locale settings later. Failure placeholder:
 * store_locale_missing. No database, controller, or UI dependency.
 */
public interface StoreLocaleRule {

    RuleDecision evaluate(StoreLocaleInput input);

    record StoreLocaleInput() implements RuleInput {
    }
}
