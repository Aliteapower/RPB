package com.rpb.reservation.cleaning.application;

import java.util.List;
import java.util.UUID;

public record CleaningApplicationResult(
    boolean success,
    CleaningApplicationError error,
    UUID cleaningId,
    UUID seatingId,
    String resourceType,
    UUID resourceId,
    String previousTableStatus,
    String currentTableStatus,
    String cleaningStatus,
    String idempotencyStatus,
    List<UUID> businessEventIds,
    List<UUID> stateTransitionLogIds,
    UUID auditLogId,
    boolean replayed,
    boolean retryLater
) {

    public CleaningApplicationResult {
        businessEventIds = businessEventIds == null ? List.of() : List.copyOf(businessEventIds);
        stateTransitionLogIds = stateTransitionLogIds == null ? List.of() : List.copyOf(stateTransitionLogIds);
    }

    public static CleaningApplicationResult success(
        UUID cleaningId,
        UUID seatingId,
        String resourceType,
        UUID resourceId,
        String previousTableStatus,
        String currentTableStatus,
        String cleaningStatus,
        String idempotencyStatus,
        List<UUID> businessEventIds,
        List<UUID> stateTransitionLogIds,
        UUID auditLogId
    ) {
        return new CleaningApplicationResult(
            true,
            null,
            cleaningId,
            seatingId,
            resourceType,
            resourceId,
            previousTableStatus,
            currentTableStatus,
            cleaningStatus,
            idempotencyStatus,
            businessEventIds,
            stateTransitionLogIds,
            auditLogId,
            false,
            false
        );
    }

    public static CleaningApplicationResult replay(
        UUID cleaningId,
        UUID seatingId,
        String resourceType,
        UUID resourceId,
        String currentTableStatus,
        String cleaningStatus
    ) {
        return new CleaningApplicationResult(
            true,
            null,
            cleaningId,
            seatingId,
            resourceType,
            resourceId,
            null,
            currentTableStatus,
            cleaningStatus,
            "completed",
            List.of(),
            List.of(),
            null,
            true,
            false
        );
    }

    public static CleaningApplicationResult failure(CleaningApplicationError error) {
        return new CleaningApplicationResult(
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
            List.of(),
            List.of(),
            null,
            false,
            false
        );
    }

    public static CleaningApplicationResult retryLater(CleaningApplicationError error) {
        return new CleaningApplicationResult(
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
            List.of(),
            List.of(),
            null,
            false,
            true
        );
    }
}
