package com.rpb.reservation.payment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

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

    @Test
    void getTerminalConfigRequiresPaymentCreatePermission() throws NoSuchMethodException {
        Method method = PaymentIntentController.class.getMethod("getTerminalConfig", UUID.class);

        GetMapping mapping = method.getAnnotation(GetMapping.class);
        RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/terminal-config");
        assertThat(gate).isNotNull();
        assertThat(gate.appKey()).isEqualTo("payment");
        assertThat(gate.permission()).isEqualTo("payment.intent.create");
    }

    @Test
    void getQuickPayRecordsRequiresPaymentViewPermission() throws NoSuchMethodException {
        Method method = PaymentIntentController.class.getMethod(
            "getQuickPayRecords",
            UUID.class,
            java.time.LocalDate.class,
            String.class,
            String.class,
            String.class,
            int.class
        );

        GetMapping mapping = method.getAnnotation(GetMapping.class);
        RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/quick-pay-records");
        assertThat(gate).isNotNull();
        assertThat(gate.appKey()).isEqualTo("payment");
        assertThat(gate.permission()).isEqualTo("payment.intent.view");
    }

    @Test
    void paymentBusinessDayEndpointsRequirePaymentCreatePermission() throws NoSuchMethodException {
        Method getMethod = PaymentBusinessDayController.class.getMethod("getBusinessDay", UUID.class);
        Method postMethod = PaymentBusinessDayController.class.getMethod(
            "openBusinessDay",
            UUID.class,
            PaymentBusinessDayRequest.class
        );
        Method endDayMethod = PaymentBusinessDayController.class.getMethod("endBusinessDay", UUID.class);

        GetMapping getMapping = getMethod.getAnnotation(GetMapping.class);
        PostMapping postMapping = postMethod.getAnnotation(PostMapping.class);
        PostMapping endDayMapping = endDayMethod.getAnnotation(PostMapping.class);
        RequireAppGate getGate = getMethod.getAnnotation(RequireAppGate.class);
        RequireAppGate postGate = postMethod.getAnnotation(RequireAppGate.class);
        RequireAppGate endDayGate = endDayMethod.getAnnotation(RequireAppGate.class);

        assertThat(getMapping).isNotNull();
        assertThat(postMapping).isNotNull();
        assertThat(endDayMapping).isNotNull();
        assertThat(endDayMapping.value()).containsExactly("/end-day");
        assertThat(getGate).isNotNull();
        assertThat(postGate).isNotNull();
        assertThat(endDayGate).isNotNull();
        assertThat(getGate.permission()).isEqualTo("payment.intent.create");
        assertThat(postGate.permission()).isEqualTo("payment.intent.create");
        assertThat(endDayGate.permission()).isEqualTo("payment.intent.create");
    }
}
