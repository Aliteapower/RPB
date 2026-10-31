package com.rpb.reservation.payment.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.payment.application.PaymentMethodProfile;
import com.rpb.reservation.payment.application.PaymentMethodProfileCommand;
import java.util.Optional;

public interface PaymentMethodProfileRepository {
    PaymentMethodProfile upsertStoreProfile(StoreScope scope, PaymentMethodProfileCommand command);

    Optional<PaymentMethodProfile> findStoreProfile(StoreScope scope, String method);

    Optional<PaymentMethodProfile> findTenantDefaultProfile(TenantScope scope, String method);
}
