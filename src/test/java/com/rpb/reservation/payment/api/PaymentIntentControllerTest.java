package com.rpb.reservation.payment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

class PaymentIntentControllerTest {

    @Test
    void createQuickPayIntentRequiresPaymentAppGatePermission() throws NoSuchMethodException {
        Method method = PaymentIntentController.class.getMethod(
            "createIntent",
            UUID.class,
            PaymentIntentRequests.CreateIntentRequest.class
        );

        RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

        assertThat(gate).isNotNull();
        assertThat(gate.appKey()).isEqualTo("payment");
        assertThat(gate.permission()).isEqualTo("payment.intent.create");
    }

    @Test
    void getPaymentSessionRequiresPaymentViewPermission() throws NoSuchMethodException {
        Method method = PaymentIntentController.class.getMethod("getSession", UUID.class, String.class);

        GetMapping mapping = method.getAnnotation(GetMapping.class);
        RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/sessions/{sessionNo}");
        assertThat(gate).isNotNull();
        assertThat(gate.appKey()).isEqualTo("payment");
        assertThat(gate.permission()).isEqualTo("payment.intent.view");
    }
}
