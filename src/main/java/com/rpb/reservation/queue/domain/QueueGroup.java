package com.rpb.reservation.queue.domain;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.PartySize;
import java.util.Objects;
import java.util.UUID;

/**
 * QueueGroup domain skeleton. V1 groups queue tickets by Store + party-size
 * band and is not an individual queue ticket.
 */
public record QueueGroup(
    UUID id,
    StoreScope scope,
    String groupCode,
    int minPartySize,
    Integer maxPartySize,
    String displayI18nKey,
    String status
) {

    public QueueGroup {
        Objects.requireNonNull(id, "queue_group_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        if (minPartySize <= 0 || (maxPartySize != null && maxPartySize < minPartySize)) {
            throw new IllegalArgumentException("queue_group_party_size_range_invalid");
        }
        requireText(groupCode, "queue_group_code_required");
        requireText(displayI18nKey, "queue_group_i18n_key_required");
        requireText(status, "queue_group_status_required");
    }

    public String matchPartySizeIntent() {
        return "queue_group.match_party_size.intent";
    }

    public boolean covers(PartySize partySize) {
        Objects.requireNonNull(partySize, "party_size_required");
        return partySize.value() >= minPartySize && (maxPartySize == null || partySize.value() <= maxPartySize);
    }

    public boolean active() {
        return "active".equals(status);
    }

    public String domainBoundary() {
        return "QueueGroup is a grouping policy and not QueueTicket.";
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
