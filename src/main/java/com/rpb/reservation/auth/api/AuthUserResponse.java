package com.rpb.reservation.auth.api;

import com.rpb.reservation.auth.application.AuthPrincipal;
import java.util.List;

public record AuthUserResponse(
    String accountId,
    String tenantId,
    String username,
    String displayName,
    String actorType,
    String defaultStoreId,
    List<String> storeIds,
    List<String> roles,
    List<String> permissions
) {
    public static AuthUserResponse from(AuthPrincipal principal) {
        return new AuthUserResponse(
            principal.accountId().toString(),
            principal.tenantId() == null ? null : principal.tenantId().toString(),
            principal.username(),
            principal.displayName(),
            principal.actorType(),
            principal.defaultStoreId() == null ? null : principal.defaultStoreId().toString(),
            principal.storeIds().stream().map(Object::toString).sorted().toList(),
            principal.roles().stream().sorted().toList(),
            principal.permissions().stream().sorted().toList()
        );
    }
}
