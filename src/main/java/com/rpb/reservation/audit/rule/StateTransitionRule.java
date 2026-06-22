package com.rpb.reservation.audit.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: validate legal state movement and required transition logging.
 * Input placeholder: target type, from/to status, trigger, actor/source,
 * preconditions. Output placeholder: RuleDecision. Failure placeholder:
 * illegal_state_transition. No database, controller, or UI dependency.
 */
public interface StateTransitionRule {

    RuleDecision evaluate(StateTransitionInput input);

    record StateTransitionInput() implements RuleInput {
    }
}
