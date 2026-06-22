package com.rpb.reservation.i18n.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: resolve stable i18n key by scope and locale. Input placeholder:
 * scope, i18n key, locale. Output placeholder: RuleDecision or message later.
 * Failure placeholder: i18n_message_missing. No database, controller, or UI
 * dependency.
 */
public interface I18nMessageRule {

    RuleDecision evaluate(I18nMessageInput input);

    record I18nMessageInput() implements RuleInput {
    }
}
