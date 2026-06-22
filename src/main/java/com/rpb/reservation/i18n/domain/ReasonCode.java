package com.rpb.reservation.i18n.domain;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.I18nKey;
import com.rpb.reservation.common.value.ReasonCodeValue;
import java.util.Objects;
import java.util.UUID;

/**
 * ReasonCode domain skeleton. It stores stable reason codes and i18n keys, not
 * free-form transition execution.
 */
public record ReasonCode(UUID id, TenantScope scope, String reasonType, ReasonCodeValue code, I18nKey i18nKey, String status) {

    public ReasonCode {
        Objects.requireNonNull(id, "reason_code_id_required");
        Objects.requireNonNull(scope, "tenant_scope_required");
        Objects.requireNonNull(code, "reason_code_required");
        Objects.requireNonNull(i18nKey, "reason_i18n_key_required");
        requireText(reasonType, "reason_type_required");
        requireText(status, "reason_code_status_required");
    }

    public String activateIntent() {
        return "reason_code.activate.intent";
    }

    public String domainBoundary() {
        return "ReasonCode is configuration and not transition execution.";
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
