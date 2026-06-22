package com.rpb.reservation.walkin.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rpb.local-auth")
public class LocalAuthProperties {
    private boolean enabled;
    private UUID tenantId;
    private UUID actorId;
    private String actorType = "staff";
    private List<String> roles = new ArrayList<>(List.of("store_staff"));
    private List<String> permissions = new ArrayList<>(List.of("walkin.direct_seating.create"));
    private List<UUID> storeIds = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions == null ? new ArrayList<>() : new ArrayList<>(permissions);
    }

    public List<UUID> getStoreIds() {
        return storeIds;
    }

    public void setStoreIds(List<UUID> storeIds) {
        this.storeIds = storeIds == null ? new ArrayList<>() : new ArrayList<>(storeIds);
    }
}
