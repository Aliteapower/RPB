package com.rpb.reservation.store.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: resolve Store timezone and Store-local business date. Input
 * placeholder: timezone and UTC instant. Output placeholder: RuleDecision or
 * local date/time later. Failure placeholder: store_timezone_missing. No
 * database, controller, or UI dependency.
 */
public interface StoreTimeZoneRule {

    RuleDecision evaluate(StoreTimeZoneInput input);

    record StoreTimeZoneInput() implements RuleInput {
    }
}
