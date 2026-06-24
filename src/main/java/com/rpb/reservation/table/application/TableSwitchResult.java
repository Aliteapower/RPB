package com.rpb.reservation.table.application;

import java.util.List;
import java.util.UUID;

public record TableSwitchResult(
    boolean success,
    TableSwitchError error,
    UUID seatingId,
    String fromResourceType,
    UUID fromResourceId,
    String fromResourceStatus,
    String toResourceType,
    UUID toResourceId,
    String toResourceStatus,
    UUID cleaningId,
    String seatingStatus,
    String idempotencyStatus,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean replayed,
    boolean retryLater
) {

    public TableSwitchResult {
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static TableSwitchResult success(
        UUID seatingId,
        String fromResourceType,
        UUID fromResourceId,
        String fromResourceStatus,
        String toResourceType,
        UUID toResourceId,
        String toResourceStatus,
        UUID cleaningId,
        String seatingStatus,
        String idempotencyStatus,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new TableSwitchResult(
            true,
            null,
            seatingId,
            fromResourceType,
            fromResourceId,
            fromResourceStatus,
            toResourceType,
            toResourceId,
            toResourceStatus,
            cleaningId,
            seatingStatus,
            idempotencyStatus,
            businessEventIds,
            stateTransitionLogIds,
            auditLogId,
            false,
            false
        );
    }

    public static TableSwitchResult replay(
        UUID seatingId,
        String fromResourceType,
        UUID fromResourceId,
        String fromResourceStatus,
        String toResourceType,
        UUID toResourceId,
        String toResourceStatus,
        UUID cleaningId,
        String seatingStatus
    ) {
        return new TableSwitchResult(
            true,
            null,
            seatingId,
            fromResourceType,
            fromResourceId,
            fromResourceStatus,
            toResourceType,
            toResourceId,
            toResourceStatus,
            cleaningId,
            seatingStatus,
            "completed",
            List.of(),
            List.of(),
            null,
            true,
            false
        );
    }

    public static TableSwitchResult failure(TableSwitchError error) {
        return new TableSwitchResult(
            false,
            error,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            false,
            false
        );
    }

    public static TableSwitchResult retryLater(TableSwitchError error) {
        return new TableSwitchResult(
            false,
            error,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            false,
            true
        );
    }
}
