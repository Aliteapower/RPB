package com.rpb.reservation.table.application;

import java.util.List;
import java.util.UUID;

public record TableResourceItem(
    String resourceType,
    String groupType,
    UUID resourceId,
    String code,
    String displayName,
    String areaName,
    int capacityMin,
    int capacityMax,
    String status,
    boolean selectable,
    String selectionDisabledReason,
    List<String> memberTableCodes,
    UUID currentSeatingId,
    UUID currentCleaningId,
    UUID currentReservationId,
    Integer currentPartySize,
    UUID preassignedReservationId,
    String preassignedReservationCode,
    String preassignedCustomerName,
    String preassignedPhoneMasked,
    String preassignedReservationStatus,
    Integer preassignedPartySize,
    java.time.Instant preassignedStartAt,
    java.time.Instant preassignedEndAt,
    String preassignedResourceCode,
    UUID preassignedQueueTicketId,
    Integer preassignedQueueTicketNumber,
    String preassignedQueueTicketStatus
) {

    public TableResourceItem {
        memberTableCodes = memberTableCodes == null ? List.of() : List.copyOf(memberTableCodes);
    }

    public TableResourceItem(
        String resourceType,
        UUID resourceId,
        String code,
        String displayName,
        String areaName,
        int capacityMin,
        int capacityMax,
        String status,
        boolean selectable,
        String selectionDisabledReason,
        List<String> memberTableCodes
    ) {
        this(
            resourceType,
            null,
            resourceId,
            code,
            displayName,
            areaName,
            capacityMin,
            capacityMax,
            status,
            selectable,
            selectionDisabledReason,
            memberTableCodes,
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
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public TableResourceItem(
        String resourceType,
        UUID resourceId,
        String code,
        String displayName,
        String areaName,
        int capacityMin,
        int capacityMax,
        String status,
        boolean selectable,
        String selectionDisabledReason,
        List<String> memberTableCodes,
        UUID currentSeatingId,
        UUID currentCleaningId
    ) {
        this(
            resourceType,
            null,
            resourceId,
            code,
            displayName,
            areaName,
            capacityMin,
            capacityMax,
            status,
            selectable,
            selectionDisabledReason,
            memberTableCodes,
            currentSeatingId,
            currentCleaningId,
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
            null,
            null,
            null,
            null
        );
    }

    public TableResourceItem(
        String resourceType,
        UUID resourceId,
        String code,
        String displayName,
        String areaName,
        int capacityMin,
        int capacityMax,
        String status,
        boolean selectable,
        String selectionDisabledReason,
        List<String> memberTableCodes,
        UUID currentSeatingId,
        UUID currentCleaningId,
        UUID currentReservationId,
        Integer currentPartySize
    ) {
        this(
            resourceType,
            null,
            resourceId,
            code,
            displayName,
            areaName,
            capacityMin,
            capacityMax,
            status,
            selectable,
            selectionDisabledReason,
            memberTableCodes,
            currentSeatingId,
            currentCleaningId,
            currentReservationId,
            currentPartySize,
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
            null,
            null
        );
    }
}
