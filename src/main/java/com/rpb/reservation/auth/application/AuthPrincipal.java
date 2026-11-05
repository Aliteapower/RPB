package com.rpb.reservation.auth.application;

import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
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
    Set<String> permissions,
    Map<UUID, Set<String>> storePermissions
) {
    public AuthPrincipal {
        storeIds = storeIds == null ? Set.of() : Set.copyOf(storeIds);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        Set<UUID> allowedStores = storeIds;
        storePermissions = storePermissions == null ? Map.of() : storePermissions.entrySet().stream()
            .filter(entry -> allowedStores.contains(entry.getKey()))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    /** Compatibility for service callers and the legacy account-level grant model. */
    public AuthPrincipal(UUID accountId, UUID tenantId, String username, String displayName, String actorType,
                         UUID defaultStoreId, Set<UUID> storeIds, Set<String> roles, Set<String> permissions) {
        this(accountId, tenantId, username, displayName, actorType, defaultStoreId, storeIds, roles, permissions, Map.of());
    }

    public CurrentActor toCurrentActor() {
        return CurrentActor.storeStaff(tenantId, accountId, actorType, roles, permissions, storeIds);
    }

    public CurrentActor toCurrentActor(UUID storeId) {
        return CurrentActor.storeStaff(tenantId, accountId, actorType, roles,
            storeId == null ? permissions : storePermissions.getOrDefault(storeId, permissions), storeIds);
    }
}
