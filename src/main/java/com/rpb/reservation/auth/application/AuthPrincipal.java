package com.rpb.reservation.auth.application;

import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.Set;
import java.util.UUID;

public record AuthPrincipal(
    UUID accountId,
    UUID tenantId,
    String username,
    String displayName,
    String actorType,
    UUID defaultStoreId,
    Set<UUID> storeIds,
    Set<String> roles,
    Set<String> permissions
) {
    public AuthPrincipal {
        storeIds = storeIds == null ? Set.of() : Set.copyOf(storeIds);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public CurrentActor toCurrentActor() {
        return CurrentActor.storeStaff(tenantId, accountId, actorType, roles, permissions, storeIds);
    }
}
