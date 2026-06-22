package com.rpb.reservation.walkin.api;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record SeatWalkInDirectlyRequest(
    Integer partySize,
    UUID customerId,
    String customerName,
    String customerNickname,
    String phoneE164,
    UUID tableId,
    UUID tableGroupId,
    String overrideReasonCode,
    String overrideNote
) {
    private static final Pattern E164_PATTERN = Pattern.compile("^[+][1-9][0-9]{1,14}$");

    public Optional<ApiErrorCode> validateContract() {
        if (partySize == null || partySize <= 0) {
            return Optional.of(ApiErrorCode.INVALID_PARTY_SIZE);
        }
        if (hasText(phoneE164) && !E164_PATTERN.matcher(phoneE164.trim()).matches()) {
            return Optional.of(ApiErrorCode.INVALID_PHONE_E164);
        }
        if (tableId != null && tableGroupId != null) {
            return Optional.of(ApiErrorCode.RESOURCE_CONFLICT);
        }
        return Optional.empty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
