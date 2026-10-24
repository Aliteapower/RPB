package com.rpb.reservation.payment.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.persistence.PaymentMethodProfileRepository;
import com.rpb.reservation.payment.provider.PayNowQrPayloadBuilder;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentMethodProfileServiceTest {
    private final PaymentMethodProfileRepository repository = mock(PaymentMethodProfileRepository.class);
    private final PaymentMethodProfileService service = new PaymentMethodProfileService(repository, new PayNowQrPayloadBuilder());
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

    @Test
    void normalizesMobilePayNowProfileToSingaporeCountryCode() {
        when(repository.upsertStoreProfile(eq(scope), any())).thenAnswer(invocation -> {
            PaymentMethodProfileCommand saved = invocation.getArgument(1);
            return new PaymentMethodProfile(
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                scope.tenantId().value(),
                scope.storeId().value(),
                saved.method(),
                saved.status(),
                saved.paynowType(),
                saved.paynowMobile(),
                saved.paynowUen(),
                saved.merchantName(),
                saved.currency(),
                saved.configJson(),
                1,
                java.time.OffsetDateTime.parse("2026-08-06T04:00:00Z"),
                java.time.OffsetDateTime.parse("2026-08-06T04:00:00Z")
            );
        });

        PaymentMethodProfile result = service.updateProfile(scope, new PaymentMethodProfileCommand(
            "paynow",
            "active",
            "mobile",
            "8735 7878",
            null,
            "RPB Demo Restaurant",
            "SGD",
            "{}",
            0
        ));

        org.assertj.core.api.Assertions.assertThat(result.paynowMobile()).isEqualTo("+6587357878");
    }

    @Test
    void buildsSettingsTestQrForTenCentWithoutCreatingIntent() {
        when(repository.findStoreProfile(scope, "paynow")).thenReturn(java.util.Optional.of(new PaymentMethodProfile(
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            scope.tenantId().value(),
            scope.storeId().value(),
            "paynow",
            "active",
            "mobile",
            "+6587357878",
            null,
            "RPB Demo Restaurant",
            "SGD",
            "{\"quickPay\":{\"referencePrefix\":\"AB\"}}",
            1,
            java.time.OffsetDateTime.parse("2026-08-06T04:00:00Z"),
            java.time.OffsetDateTime.parse("2026-08-06T04:00:00Z")
        )));

        PaymentProfileTestQr result = service.generateSettingsTestQr(scope);

        org.assertj.core.api.Assertions.assertThat(result.amount()).isEqualByComparingTo("0.10");
        org.assertj.core.api.Assertions.assertThat(result.currency()).isEqualTo("SGD");
        org.assertj.core.api.Assertions.assertThat(result.paymentReference()).isEqualTo("AB-TEST-010");
        org.assertj.core.api.Assertions.assertThat(result.qrPayload()).contains("+6587357878").contains("0.10");
    }
}
