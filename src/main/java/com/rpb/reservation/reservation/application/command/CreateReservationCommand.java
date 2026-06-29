package com.rpb.reservation.reservation.application.command;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreateReservationCommand(
    UUID tenantId,
    UUID storeId,
    Integer partySize,
    Instant reservedStartAt,
    Instant reservedEndAt,
    LocalDate businessDate,
    UUID customerId,
    String customerName,
    String customerNickname,
    String phoneE164,
    String note,
    String idempotencyKey,
    UUID actorId,
    String actorType,
    String reservationCode,
    String source,
    String reasonCode,
    UUID tableId,
    UUID tableGroupId
) {
    public CreateReservationCommand(
        UUID tenantId,
        UUID storeId,
        Integer partySize,
        Instant reservedStartAt,
        Instant reservedEndAt,
        UUID customerId,
        String customerName,
        String customerNickname,
        String phoneE164,
        String note,
        String idempotencyKey,
        UUID actorId,
        String actorType,
        String reservationCode,
        String source,
        String reasonCode,
        UUID tableId,
        UUID tableGroupId
    ) {
        this(
            tenantId,
            storeId,
            partySize,
            reservedStartAt,
            reservedEndAt,
            null,
            customerId,
            customerName,
            customerNickname,
            phoneE164,
            note,
            idempotencyKey,
            actorId,
            actorType,
            reservationCode,
            source,
            reasonCode,
            tableId,
            tableGroupId
        );
    }

    public CreateReservationCommand(
        UUID tenantId,
        UUID storeId,
        Integer partySize,
        Instant reservedStartAt,
        Instant reservedEndAt,
        UUID customerId,
        String customerName,
        String customerNickname,
        String phoneE164,
        String note,
        String idempotencyKey,
        UUID actorId,
        String actorType,
        String reservationCode,
        String source,
        String reasonCode
    ) {
        this(
            tenantId,
            storeId,
            partySize,
            reservedStartAt,
            reservedEndAt,
            null,
            customerId,
            customerName,
            customerNickname,
            phoneE164,
            note,
            idempotencyKey,
            actorId,
            actorType,
            reservationCode,
            source,
            reasonCode,
            null,
            null
        );
    }
}
