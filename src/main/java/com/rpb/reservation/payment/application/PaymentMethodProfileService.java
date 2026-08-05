package com.rpb.reservation.payment.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.persistence.PaymentMethodProfileRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PaymentMethodProfileService {
    private static final String METHOD_PAYNOW = "paynow";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_DISABLED = "disabled";
    private static final String PAYNOW_TYPE_MOBILE = "mobile";
    private static final String PAYNOW_TYPE_UEN = "uen";
    private static final String CURRENCY_SGD = "SGD";

    private final PaymentMethodProfileRepository repository;

    public PaymentMethodProfileService(PaymentMethodProfileRepository repository) {
        this.repository = Objects.requireNonNull(repository, "payment_profile_repository_required");
    }

    public PaymentMethodProfile updateProfile(StoreScope scope, PaymentMethodProfileCommand command) {
        Objects.requireNonNull(scope, "payment_scope_required");
        validate(command);
        return repository.upsertStoreProfile(scope, normalized(command));
    }

    public Optional<PaymentMethodProfile> findEffectiveProfile(StoreScope scope) {
        Objects.requireNonNull(scope, "payment_scope_required");
        return repository.findStoreProfile(scope, METHOD_PAYNOW)
            .or(() -> repository.findTenantDefaultProfile(scope.tenantScope(), METHOD_PAYNOW));
    }

    private static PaymentMethodProfileCommand normalized(PaymentMethodProfileCommand command) {
        return new PaymentMethodProfileCommand(
            trim(command.method()),
            trim(command.status()),
            trim(command.paynowType()),
            trim(command.paynowMobile()),
            trim(command.paynowUen()),
            trim(command.merchantName()),
            trim(command.currency()),
            blankToDefaultJson(command.configJson()),
            command.version()
        );
    }

    private static void validate(PaymentMethodProfileCommand command) {
        if (command == null) {
            throw invalid();
        }
        PaymentMethodProfileCommand normalized = normalized(command);
        if (!METHOD_PAYNOW.equals(normalized.method())) {
            throw invalid();
        }
        if (!STATUS_ACTIVE.equals(normalized.status()) && !STATUS_DISABLED.equals(normalized.status())) {
            throw invalid();
        }
        if (!PAYNOW_TYPE_MOBILE.equals(normalized.paynowType()) && !PAYNOW_TYPE_UEN.equals(normalized.paynowType())) {
            throw invalid();
        }
        if (!CURRENCY_SGD.equals(normalized.currency())) {
            throw invalid();
        }
        if (STATUS_ACTIVE.equals(normalized.status())) {
            validateActivePayNow(normalized);
        }
    }

    private static void validateActivePayNow(PaymentMethodProfileCommand command) {
        if (isBlank(command.merchantName())) {
            throw invalid();
        }
        if (PAYNOW_TYPE_MOBILE.equals(command.paynowType()) && isBlank(command.paynowMobile())) {
            throw invalid();
        }
        if (PAYNOW_TYPE_UEN.equals(command.paynowType()) && isBlank(command.paynowUen())) {
            throw invalid();
        }
    }

    private static PaymentServiceException invalid() {
        return new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_PROFILE_INVALID);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToDefaultJson(String value) {
        return isBlank(value) ? "{}" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
