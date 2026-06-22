package com.rpb.reservation.common.state;

import java.util.Objects;

/**
 * Placeholder transition decision shared by all centralized state machines.
 */
public record TransitionResult<S extends Enum<S>>(
    boolean accepted,
    S from,
    S to,
    String violationCode,
    boolean auditRequired,
    boolean stateTransitionLogRequired,
    boolean idempotencyRequired
) {

    public TransitionResult {
        Objects.requireNonNull(to, "transition_to_status_required");
        if (!accepted && (violationCode == null || violationCode.isBlank())) {
            throw new IllegalArgumentException("violation_code_required_for_rejected_transition");
        }
    }

    public static <S extends Enum<S>> TransitionResult<S> accepted(S from, S to) {
        return new TransitionResult<>(true, from, to, null, true, true, true);
    }

    public static <S extends Enum<S>> TransitionResult<S> rejected(S from, S to, String violationCode) {
        return new TransitionResult<>(false, from, to, violationCode, true, false, false);
    }
}
