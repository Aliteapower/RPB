package com.rpb.reservation.table.group.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: validate group member consistency, status, active use, and no nested
 * groups in V1. Input placeholder: group, members, table statuses, locks and
 * occupancy facts. Output placeholder: RuleDecision. Failure placeholder:
 * invalid_table_group. No database, controller, or UI dependency.
 */
public interface TableGroupValidationRule {

    RuleDecision evaluate(TableGroupValidationInput input);

    record TableGroupValidationInput() implements RuleInput {
    }
}
