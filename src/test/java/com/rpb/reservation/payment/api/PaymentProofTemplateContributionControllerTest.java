package com.rpb.reservation.payment.api;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.payment.application.PaymentProofTemplateService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PaymentProofTemplateContributionControllerTest {
    private final UUID storeId = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private final UUID otherStoreId = UUID.fromString("20000000-0000-0000-0000-000000000004");
    private final MutableCurrentActorProvider currentActorProvider = new MutableCurrentActorProvider();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentProofTemplateContributionController(
            mock(PaymentProofTemplateService.class), currentActorProvider
        )).build();
    }

    @Test
    void tenantContributionSubmitRequiresTenantStoreScope() throws Exception {
        currentActorProvider.setCurrentActor(tenantActorWithStore(otherStoreId, "payment.proof_template.manage"));

        mockMvc.perform(post("/api/v1/stores/{storeId}/tenant-admin/payment/proof-template-contributions", storeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validContributionJson()))
            .andExpect(status().isForbidden());
    }

    private static String validContributionJson() {
        return """
            {"bankCode":"ocbc","bankName":"OCBC","locale":"zh-CN","templateName":"OCBC receipt",
             "layoutJson":"{\\"matchKeywords\\":[\\"OCBC\\"]}","sampleFileName":"receipt.jpg",
             "sampleContentType":"image/jpeg","sampleFileDigest":"digest","sampleOcrReference":"QP202608090016MERK",
             "sampleOcrAmount":0.50,"sampleRawText":"OCBC raw text"}
            """;
    }

    private static CurrentActor tenantActorWithStore(UUID storeId, String permission) {
        return new CurrentActor(UUID.randomUUID(), UUID.randomUUID(), "tenant_admin", Set.of("tenant_admin"), Set.of(permission), Set.of(storeId));
    }

    private static final class MutableCurrentActorProvider implements CurrentActorProvider {
        private CurrentActor currentActor;

        void setCurrentActor(CurrentActor actor) {
            this.currentActor = actor;
        }

        @Override
        public Optional<CurrentActor> currentActor() {
            return Optional.ofNullable(currentActor);
        }
    }
}
