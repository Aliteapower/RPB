package com.rpb.reservation.idempotency.state;

import com.rpb.reservation.common.state.StateMachine;
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized IdempotencyRecord transition skeleton. Replay behavior remains in
 * IdempotencyRule.
 */
public final class IdempotencyStateMachine implements StateMachine<IdempotencyStatus> {

    private static final Map<IdempotencyStatus, Set<IdempotencyStatus>> ALLOWED = new EnumMap<>(IdempotencyStatus.class);

    static {
        ALLOWED.put(IdempotencyStatus.STARTED, EnumSet.of(IdempotencyStatus.COMPLETED, IdempotencyStatus.FAILED, IdempotencyStatus.EXPIRED));
        ALLOWED.put(IdempotencyStatus.FAILED, EnumSet.of(IdempotencyStatus.EXPIRED));
        ALLOWED.put(IdempotencyStatus.COMPLETED, EnumSet.of(IdempotencyStatus.EXPIRED));
    }

    @Override
    public boolean canTransition(IdempotencyStatus from, IdempotencyStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    @Override
    public TransitionResult<IdempotencyStatus> validateTransition(IdempotencyStatus from, IdempotencyStatus to) {
        if (canTransition(from, to)) {
            return TransitionResult.accepted(from, to);
        }
        return TransitionResult.rejected(from, to, "illegal_state_transition");
    }
}
