package com.rpb.reservation.payment.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.persistence.PaymentMethodProfileRepository;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentMethodProfileServiceTest {
    private final PaymentMethodProfileRepository repository = mock(PaymentMethodProfileRepository.class);
    private final PaymentMethodProfileService service = new PaymentMethodProfileService(repository);
    private final StoreScope scope = new StoreScope(
        new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001")),
        new StoreId(UUID.fromString("20000000-0000-0000-0000-000000000001"))
    );

    @Test
    void rejectsActivePayNowUenProfileWithoutMerchantName() {
        PaymentMethodProfileCommand command = new PaymentMethodProfileCommand(
            "paynow",
            "active",
            "uen",
            null,
            "202012345A",
            "",
            "SGD",
            "{}",
            0
        );

        assertThatThrownBy(() -> service.updateProfile(scope, command))
            .isInstanceOf(PaymentServiceException.class)
            .hasMessageContaining(PaymentServiceErrorCode.PAYMENT_PROFILE_INVALID.name());
    }
}
