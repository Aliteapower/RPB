package com.rpb.reservation.payment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

class PaymentProfileControllerTest {

    @Test
    void updateProfileRequiresPaymentSettingsManagePermission() throws NoSuchMethodException {
        Method method = PaymentProfileController.class.getMethod("updateProfile", UUID.class, PaymentProfileRequest.class);

        RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

        assertThat(gate).isNotNull();
        assertThat(gate.appKey()).isEqualTo("payment");
        assertThat(gate.permission()).isEqualTo("payment.settings.manage");
    }

    @Test
    void generateTestQrRequiresPaymentSettingsManagePermission() throws NoSuchMethodException {
        Method method = PaymentProfileController.class.getMethod("generateTestQr", UUID.class);

        PostMapping mapping = method.getAnnotation(PostMapping.class);
        RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/test-qr");
        assertThat(gate).isNotNull();
        assertThat(gate.appKey()).isEqualTo("payment");
        assertThat(gate.permission()).isEqualTo("payment.settings.manage");
    }
}
