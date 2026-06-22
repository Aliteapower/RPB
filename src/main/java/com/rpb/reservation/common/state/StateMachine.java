package com.rpb.reservation.common.state;

/**
 * Central state machine contract. Implementations own transition checks so
 * status logic does not spread into controllers, services, or domain records.
 */
public interface StateMachine<S extends Enum<S>> {

    boolean canTransition(S from, S to);

    TransitionResult<S> validateTransition(S from, S to);
}
