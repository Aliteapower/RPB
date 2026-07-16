package com.rpb.reservation.walkin.api;

import java.util.HashSet;
import java.util.List;
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
    List<UUID> temporaryTableIds,
    String overrideReasonCode,
    String overrideNote
) {
    private static final Pattern E164_PATTERN = Pattern.compile("^[+][1-9][0-9]{1,14}$");

    public SeatWalkInDirectlyRequest {
        temporaryTableIds = temporaryTableIds == null ? null : List.copyOf(temporaryTableIds);
    }

    public Optional<ApiErrorCode> validateContract() {
        if (partySize == null || partySize <= 0) {
            return Optional.of(ApiErrorCode.INVALID_PARTY_SIZE);
        }
        if (hasText(phoneE164) && !E164_PATTERN.matcher(phoneE164.trim()).matches()) {
            return Optional.of(ApiErrorCode.INVALID_PHONE_E164);
        }
        boolean temporarySelectionProvided = temporaryTableIds != null;
        int selectedTargets = (tableId == null ? 0 : 1)
            + (tableGroupId == null ? 0 : 1)
            + (temporarySelectionProvided ? 1 : 0);
        if (selectedTargets > 1) {
            return Optional.of(ApiErrorCode.RESOURCE_CONFLICT);
        }
        if (temporarySelectionProvided && temporaryTableIds.size() < 2) {
            return Optional.of(ApiErrorCode.TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED);
        }
        if (temporarySelectionProvided && new HashSet<>(temporaryTableIds).size() != temporaryTableIds.size()) {
            return Optional.of(ApiErrorCode.TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE);
        }
        return Optional.empty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
