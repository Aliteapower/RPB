package com.rpb.reservation.reservation.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record CreateReservationRequest(
    Integer partySize,
    Instant reservedStartAt,
    Instant reservedEndAt,
    LocalDate businessDate,
    UUID customerId,
    String customerName,
    String customerNickname,
    String phoneE164,
    String note,
    UUID tableId,
    UUID tableGroupId
) {
    private static final Pattern E164_PATTERN = Pattern.compile("^[+][1-9][0-9]{1,14}$");

    public CreateReservationRequest(
        Integer partySize,
        Instant reservedStartAt,
        Instant reservedEndAt,
        UUID customerId,
        String customerName,
        String customerNickname,
        String phoneE164,
        String note,
        UUID tableId,
        UUID tableGroupId
    ) {
        this(
            partySize,
            reservedStartAt,
            reservedEndAt,
            null,
            customerId,
            customerName,
            customerNickname,
            phoneE164,
            note,
            tableId,
            tableGroupId
        );
    }

    public CreateReservationRequest(
        Integer partySize,
        Instant reservedStartAt,
        Instant reservedEndAt,
        UUID customerId,
        String customerName,
        String customerNickname,
        String phoneE164,
        String note
    ) {
        this(partySize, reservedStartAt, reservedEndAt, null, customerId, customerName, customerNickname, phoneE164, note, null, null);
    }

    public Optional<ReservationApiErrorCode> validateContract() {
        if (partySize == null || partySize <= 0) {
            return Optional.of(ReservationApiErrorCode.INVALID_PARTY_SIZE);
        }
        if (reservedStartAt == null) {
            return Optional.of(ReservationApiErrorCode.INVALID_TIME_RANGE);
        }
        if (reservedEndAt != null && !reservedEndAt.isAfter(reservedStartAt)) {
            return Optional.of(ReservationApiErrorCode.INVALID_TIME_RANGE);
        }
        if (hasText(phoneE164) && !E164_PATTERN.matcher(phoneE164.trim()).matches()) {
            return Optional.of(ReservationApiErrorCode.INVALID_PHONE_E164);
        }
        if (tableId != null && tableGroupId != null) {
            return Optional.of(ReservationApiErrorCode.RESOURCE_SELECTION_CONFLICT);
        }
        return Optional.empty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
