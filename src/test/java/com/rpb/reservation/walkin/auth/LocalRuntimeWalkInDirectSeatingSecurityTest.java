package com.rpb.reservation.walkin.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.walkin.api.WalkInDirectSeatingApiErrorMapper;
import com.rpb.reservation.walkin.api.WalkInDirectSeatingApiMapper;
import com.rpb.reservation.walkin.api.WalkInDirectSeatingController;
import com.rpb.reservation.walkin.application.WalkInDirectSeatingResult;
import com.rpb.reservation.walkin.application.service.WalkInDirectSeatingApplicationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WalkInDirectSeatingController.class)
@Import({
    AppGateApiErrorMapper.class,
    WalkInDirectSeatingApiMapper.class,
    WalkInDirectSeatingApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000101",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000101",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=walkin.direct_seating.create",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000101"
})
class LocalRuntimeWalkInDirectSeatingSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/walk-ins/direct-seating";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000101");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000101");
    private static final UUID TABLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000101");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalkInDirectSeatingApplicationService applicationService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileRequestWithoutJwtLoginWhenConfiguredActorIsPresent() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "walkin.direct_seating.create"
        ));
        when(applicationService.seatWalkInDirectly(any())).thenReturn(WalkInDirectSeatingResult.success(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "dining_table",
            TABLE_ID,
            2,
            "seated",
            "occupied",
            "active",
            "completed",
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "local-runtime-idem")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 2,
                      "customerName": "Guest"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resource.type").value("TABLE"));
    }
}
