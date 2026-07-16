package com.rpb.reservation.table.group.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.status.TableGroupStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DefaultTableGroupValidationRule implements TableGroupValidationRule {

    @Override
    public RuleDecision evaluate(TableGroupValidationInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision evaluate(TableGroup group, List<TableGroupMember> members) {
        if (group == null || !isUsableGroup(group) || members == null || members.isEmpty()) {
            return RuleDecision.deny("invalid_table_group");
        }
        Set<Object> tableIds = new HashSet<>();
        for (TableGroupMember member : members) {
            if (member == null || !group.scope().equals(member.scope()) || !group.id().equals(member.tableGroupId()) || !tableIds.add(member.tableId())) {
                return RuleDecision.deny("invalid_table_group");
            }
        }
        return RuleDecision.allow();
    }

    private static boolean isUsableGroup(TableGroup group) {
        return group.status() == TableGroupStatus.ACTIVE
            || ("temporary".equals(group.groupType()) && group.status() == TableGroupStatus.CREATED);
    }
}
