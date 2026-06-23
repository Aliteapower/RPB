package com.rpb.reservation.customer.domain;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.value.CustomerId;
import java.util.Objects;

/**
 * Customer domain skeleton. Customer is Tenant-scoped identity and not Member.
 */
public record Customer(
    CustomerId id,
    TenantScope scope,
    String customerCode,
    String customerType,
    E164Phone phone,
    String status,
    String displayName,
    String nickname
) {

    public Customer {
        Objects.requireNonNull(id, "customer_id_required");
        Objects.requireNonNull(scope, "tenant_scope_required");
        Objects.requireNonNull(phone, "phone_value_required");
        requireText(customerCode, "customer_code_required");
        requireText(customerType, "customer_type_required");
        requireText(status, "customer_status_required");
        displayName = blankToNull(displayName);
        nickname = blankToNull(nickname);
    }

    public Customer(CustomerId id, TenantScope scope, String customerCode, String customerType, E164Phone phone, String status) {
        this(id, scope, customerCode, customerType, phone, status, null, null);
    }

    public String mergeIntent() {
        return "customer.merge.intent";
    }

    public String domainBoundary() {
        return "Customer is not Member, loyalty, marketing, payment, or POS.";
    }

    public Customer refreshProfile(E164Phone updatedPhone, String updatedDisplayName, String updatedNickname) {
        E164Phone nextPhone = updatedPhone != null && updatedPhone.isPresent() ? updatedPhone : phone;
        return new Customer(
            id,
            scope,
            customerCode,
            customerType,
            nextPhone,
            status,
            coalesceProfileValue(updatedDisplayName, displayName),
            coalesceProfileValue(updatedNickname, nickname)
        );
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String coalesceProfileValue(String candidate, String fallback) {
        String normalized = blankToNull(candidate);
        return normalized == null ? fallback : normalized;
    }
}
