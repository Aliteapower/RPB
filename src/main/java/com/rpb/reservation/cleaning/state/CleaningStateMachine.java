package com.rpb.reservation.cleaning.state;

import com.rpb.reservation.cleaning.status.CleaningStatus;
import com.rpb.reservation.common.state.StateMachine;
import com.rpb.reservation.common.state.TransitionResult;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized Cleaning transition skeleton. Resource release remains a later
 * audited workflow.
 */
public final class CleaningStateMachine implements StateMachine<CleaningStatus> {

    private static final Map<CleaningStatus, Set<CleaningStatus>> ALLOWED = new EnumMap<>(CleaningStatus.class);

    static {
        ALLOWED.put(CleaningStatus.PENDING, EnumSet.of(CleaningStatus.CLEANING, CleaningStatus.CANCELLED));
        ALLOWED.put(CleaningStatus.CLEANING, EnumSet.of(CleaningStatus.COMPLETED, CleaningStatus.CANCELLED));
        ALLOWED.put(CleaningStatus.COMPLETED, EnumSet.of(CleaningStatus.RELEASED));
    }

    @Override
    public boolean canTransition(CleaningStatus from, CleaningStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    @Override
    public TransitionResult<CleaningStatus> validateTransition(CleaningStatus from, CleaningStatus to) {
        if (canTransition(from, to)) {
            return TransitionResult.accepted(from, to);
        }
        return TransitionResult.rejected(from, to, "illegal_state_transition");
    }
}
