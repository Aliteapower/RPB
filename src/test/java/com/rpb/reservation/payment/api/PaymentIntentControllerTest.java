package com.rpb.reservation.payment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
}
