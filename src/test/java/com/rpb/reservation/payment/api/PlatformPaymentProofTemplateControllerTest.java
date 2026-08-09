package com.rpb.reservation.payment.api;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.payment.application.PaymentProofTemplateService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformPaymentProofTemplateControllerTest {
    private final MutableCurrentActorProvider currentActorProvider = new MutableCurrentActorProvider();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlatformPaymentProofTemplateController(
            mock(PaymentProofTemplateService.class), currentActorProvider
        )).build();
    }

    @Test
    void platformTemplateListRequiresPlatformAdminPermission() throws Exception {
        currentActorProvider.setCurrentActor(tenantActorWith("payment.proof_template.manage"));

        mockMvc.perform(get("/api/v1/platform/payment/proof-templates"))
            .andExpect(status().isForbidden());
    }

    @Test
    void platformTemplateListAllowsPlatformAdminManager() throws Exception {
        currentActorProvider.setCurrentActor(platformActorWith("platform.payment_proof_template.manage"));

        mockMvc.perform(get("/api/v1/platform/payment/proof-templates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    private static CurrentActor tenantActorWith(String permission) {
        return new CurrentActor(UUID.randomUUID(), UUID.randomUUID(), "tenant_admin", Set.of("tenant_admin"), Set.of(permission), Set.of(UUID.randomUUID()));
    }

    private static CurrentActor platformActorWith(String permission) {
        return new CurrentActor(null, UUID.randomUUID(), "platform_admin", Set.of("platform_admin"), Set.of(permission), Set.of());
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
