package com.rpb.reservation.table.api;

import java.util.List;
import java.util.UUID;

public record TableResourceItemResponse(
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

    public TableResourceItemResponse {
        memberTableCodes = memberTableCodes == null ? List.of() : List.copyOf(memberTableCodes);
    }
}
