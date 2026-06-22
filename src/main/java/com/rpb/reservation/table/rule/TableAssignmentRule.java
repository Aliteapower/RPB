package com.rpb.reservation.table.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: select valid table/group assignment candidates. Input placeholder:
 * party size, StorePolicy, active resources, availability facts. Output
 * placeholder: RuleDecision or candidate later. Failure placeholder:
 * no_assignable_table. No database, controller, or UI dependency.
 */
public interface TableAssignmentRule {

    RuleDecision evaluate(TableAssignmentInput input);

    record TableAssignmentInput() implements RuleInput {
    }
}
