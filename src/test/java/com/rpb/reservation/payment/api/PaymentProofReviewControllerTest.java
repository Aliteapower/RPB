package com.rpb.reservation.payment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

class PaymentProofReviewControllerTest {

    @Test
    void candidatesEndpointRequiresPaymentProofReviewPermission() throws NoSuchMethodException {
        Method method = PaymentProofReviewController.class.getMethod(
            "getCandidates",
            UUID.class,
            LocalDate.class,
            String.class,
            int.class
        );

        GetMapping mapping = method.getAnnotation(GetMapping.class);
        RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/candidates");
        assertThat(gate).isNotNull();
        assertThat(gate.appKey()).isEqualTo("payment");
        assertThat(gate.permission()).isEqualTo("payment.proof.review");
    }

    @Test
    void scanEndpointRequiresPaymentProofReviewPermissionAndMultipartImage() throws NoSuchMethodException {
        Method method = PaymentProofReviewController.class.getMethod(
            "scanProof",
            UUID.class,
            MultipartFile.class,
            String.class,
            LocalDate.class,
            String.class
        );

        PostMapping mapping = method.getAnnotation(PostMapping.class);
        RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/scan");
        assertThat(mapping.consumes()).contains("multipart/form-data");
        assertThat(gate).isNotNull();
        assertThat(gate.appKey()).isEqualTo("payment");
        assertThat(gate.permission()).isEqualTo("payment.proof.review");
    }

    @Test
    void idempotencyConflictIsHttpConflict() {
        assertThat(PaymentApiErrorCode.IDEMPOTENCY_CONFLICT.httpStatus().value()).isEqualTo(409);
    }
}
