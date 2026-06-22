package com.rpb.reservation.table.group.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.rule.RuleInput;

/**
 * Purpose: validate adding/removing member tables. Input placeholder:
 * TableGroup, DiningTable, existing members, StoreScope. Output placeholder:
 * RuleDecision. Failure placeholder: invalid_table_group_member. No database,
 * controller, or UI dependency.
 */
public interface TableGroupMemberRule {

    RuleDecision evaluate(TableGroupMemberInput input);

    record TableGroupMemberInput() implements RuleInput {
    }
}
