package com.rpb.reservation.payment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class PaymentProofTemplateControllerTest {

    @Test
    void tenantTemplateListRequiresPayNowSettingsPermission() throws NoSuchMethodException {
        Method method = PaymentProofTemplateController.class.getMethod("listTemplates", UUID.class);

        GetMapping mapping = method.getAnnotation(GetMapping.class);
        RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

        assertThat(mapping).isNotNull();
        assertThat(gate).isNotNull();
        assertThat(gate.appKey()).isEqualTo("payment");
        assertThat(gate.permission()).isEqualTo("payment.proof_template.manage");
    }

    @Test
    void tenantTemplateTestScanUsesMultipartImageWithoutMutatingPayments() throws NoSuchMethodException {
        Method method = PaymentProofTemplateController.class.getMethod(
            "testScan",
            UUID.class,
            org.springframework.web.multipart.MultipartFile.class
        );

        PostMapping mapping = method.getAnnotation(PostMapping.class);
        RequireAppGate gate = method.getAnnotation(RequireAppGate.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/test-scan");
        assertThat(mapping.consumes()).contains("multipart/form-data");
        assertThat(gate.permission()).isEqualTo("payment.proof_template.manage");
    }
}
