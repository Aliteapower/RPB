package com.rpb.reservation.table.state;

import com.rpb.reservation.common.state.StateMachine;
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.table.status.TableLockStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized TableLock transition skeleton. Lock acquisition conflicts remain
 * in TableLockRule.
 */
public final class TableLockStateMachine implements StateMachine<TableLockStatus> {

    private static final Map<TableLockStatus, Set<TableLockStatus>> ALLOWED = new EnumMap<>(TableLockStatus.class);

    static {
        ALLOWED.put(TableLockStatus.ACTIVE, EnumSet.of(TableLockStatus.RELEASED, TableLockStatus.EXPIRED, TableLockStatus.CANCELLED));
    }

    @Override
    public boolean canTransition(TableLockStatus from, TableLockStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    @Override
    public TransitionResult<TableLockStatus> validateTransition(TableLockStatus from, TableLockStatus to) {
        if (canTransition(from, to)) {
            return TransitionResult.accepted(from, to);
        }
        return TransitionResult.rejected(from, to, "illegal_state_transition");
    }
}
