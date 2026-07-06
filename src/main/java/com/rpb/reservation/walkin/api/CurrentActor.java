package com.rpb.reservation.walkin.api;

import java.util.Set;
import java.util.UUID;

public record CurrentActor(
    UUID tenantId,
    UUID actorId,
    String actorType,
    Set<String> roles,
    Set<String> permissions,
    Set<UUID> storeIds
) {
    private static final String TENANT_ADMIN = "tenant_admin";
    private static final String STORE_MANAGER = "store_manager";
    private static final String STORE_STAFF = "store_staff";

    public CurrentActor {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        storeIds = storeIds == null ? Set.of() : Set.copyOf(storeIds);
    }

    public static CurrentActor storeStaff(
        UUID tenantId,
        UUID actorId,
        String actorType,
        Set<String> roles,
        Set<String> permissions,
        Set<UUID> storeIds
    ) {
        return new CurrentActor(tenantId, actorId, actorType, roles, permissions, storeIds);
    }

    public boolean hasAllowedWalkInDirectSeatingRole() {
        return roles.contains(TENANT_ADMIN) || roles.contains(STORE_MANAGER) || roles.contains(STORE_STAFF);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean canAccessStore(UUID storeId) {
        return storeId != null && storeIds.contains(storeId);
    }
}
