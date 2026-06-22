package com.rpb.reservation.store.domain;

import com.rpb.reservation.common.scope.StoreScope;
import java.util.Objects;
import java.util.UUID;

/**
 * StorePolicy domain skeleton. It carries configurable defaults and does not
 * mutate Reservation, QueueTicket, Seating, or Cleaning.
 */
public record StorePolicy(
    UUID id,
    StoreScope scope,
    int reservationHoldMinutes,
    int queueCallHoldMinutes,
    int expectedDiningMinutes,
    String queueRejoinPolicyCode,
    String tableAssignmentPolicyCode
) {

    public StorePolicy {
        Objects.requireNonNull(id, "store_policy_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        if (reservationHoldMinutes <= 0 || queueCallHoldMinutes <= 0 || expectedDiningMinutes <= 0) {
            throw new IllegalArgumentException("store_policy_minutes_must_be_positive");
        }
    }

    public String status() {
        return "effective_policy_skeleton";
    }

    public String resolvePolicyIntent() {
        return "store_policy.resolve.intent";
    }

    public String domainBoundary() {
        return "StorePolicy is configuration only, not operation history.";
    }
}
