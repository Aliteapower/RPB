package com.rpb.reservation.table.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.status.TableGroupStatus;
import org.springframework.stereotype.Component;

@Component
public class DefaultTableAvailabilityRule implements TableAvailabilityRule {

    @Override
    public RuleDecision evaluate(TableAvailabilityInput input) {
        return RuleDecision.allow();
    }

    public RuleDecision evaluate(DiningTable table) {
        if (table == null || table.status() != DiningTableStatus.AVAILABLE) {
            return RuleDecision.deny("table_resource_unavailable");
        }
        return RuleDecision.allow();
    }

    public RuleDecision evaluate(TableGroup group) {
        if (group == null || group.status() != TableGroupStatus.ACTIVE) {
            return RuleDecision.deny("invalid_table_group");
        }
        return RuleDecision.allow();
    }
}
