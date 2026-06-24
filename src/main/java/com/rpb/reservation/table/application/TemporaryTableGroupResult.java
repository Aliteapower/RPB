package com.rpb.reservation.table.application;

import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import java.util.List;

public record TemporaryTableGroupResult(
    boolean success,
    TemporaryTableGroupError error,
    TableGroup group,
    List<DiningTable> memberTables,
    List<TableGroupMember> members
) {

    public TemporaryTableGroupResult {
        memberTables = memberTables == null ? List.of() : List.copyOf(memberTables);
        members = members == null ? List.of() : List.copyOf(members);
    }

    public static TemporaryTableGroupResult success(
        TableGroup group,
        List<DiningTable> memberTables,
        List<TableGroupMember> members
    ) {
        return new TemporaryTableGroupResult(true, null, group, memberTables, members);
    }

    public static TemporaryTableGroupResult failure(TemporaryTableGroupError error) {
        return new TemporaryTableGroupResult(false, error, null, List.of(), List.of());
    }
}
