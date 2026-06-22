package com.rpb.reservation.table.state;

import com.rpb.reservation.common.state.StateMachine;
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.table.status.DiningTableStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized DiningTable transition skeleton. Availability and group-member
 * blockers stay in TableAvailabilityRule and TableGroupValidationRule.
 */
public final class DiningTableStateMachine implements StateMachine<DiningTableStatus> {

    private static final Map<DiningTableStatus, Set<DiningTableStatus>> ALLOWED = new EnumMap<>(DiningTableStatus.class);

    static {
        ALLOWED.put(DiningTableStatus.AVAILABLE, EnumSet.of(DiningTableStatus.LOCKED, DiningTableStatus.RESERVED, DiningTableStatus.INACTIVE));
        ALLOWED.put(DiningTableStatus.LOCKED, EnumSet.of(DiningTableStatus.AVAILABLE, DiningTableStatus.RESERVED, DiningTableStatus.OCCUPIED));
        ALLOWED.put(DiningTableStatus.RESERVED, EnumSet.of(DiningTableStatus.AVAILABLE, DiningTableStatus.LOCKED, DiningTableStatus.OCCUPIED, DiningTableStatus.INACTIVE));
        ALLOWED.put(DiningTableStatus.OCCUPIED, EnumSet.of(DiningTableStatus.CLEANING));
        ALLOWED.put(DiningTableStatus.CLEANING, EnumSet.of(DiningTableStatus.AVAILABLE, DiningTableStatus.INACTIVE));
        ALLOWED.put(DiningTableStatus.INACTIVE, EnumSet.of(DiningTableStatus.AVAILABLE));
    }

    @Override
    public boolean canTransition(DiningTableStatus from, DiningTableStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    @Override
    public TransitionResult<DiningTableStatus> validateTransition(DiningTableStatus from, DiningTableStatus to) {
        if (canTransition(from, to)) {
            return TransitionResult.accepted(from, to);
        }
        return TransitionResult.rejected(from, to, "illegal_state_transition");
    }
}
