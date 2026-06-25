package com.rpb.reservation.walkin.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.api.TemporaryTableGroupApiErrorMapper;
import com.rpb.reservation.table.api.TemporaryTableGroupApiMapper;
import com.rpb.reservation.table.api.TemporaryTableGroupController;
import com.rpb.reservation.table.application.TemporaryTableGroupResult;
import com.rpb.reservation.table.application.service.TemporaryTableGroupApplicationService;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
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

@WebMvcTest(TemporaryTableGroupController.class)
@Import({
    AppGateApiErrorMapper.class,
    TemporaryTableGroupApiMapper.class,
    TemporaryTableGroupApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000981",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000981",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=table.switch",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000981"
})
class LocalRuntimeTemporaryTableGroupSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/tables/temporary-groups";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000981");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000981");
    private static final UUID GROUP_ID = UUID.fromString("71000000-0000-0000-0000-000000000981");
    private static final UUID TABLE_A_ID = UUID.fromString("70000000-0000-0000-0000-000000000981");
    private static final UUID TABLE_B_ID = UUID.fromString("70000000-0000-0000-0000-000000000982");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TemporaryTableGroupApplicationService applicationService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileTemporaryGroupSaveWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "table.switch"
        ));
        when(applicationService.saveForManagement(any())).thenReturn(success());

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupName":"A区临组1","businessDate":"2026-06-25","tableIds":["%s","%s"]}
                    """.formatted(TABLE_A_ID, TABLE_B_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.groupName").value("A区临组1"));

        verify(applicationService).saveForManagement(any());
    }

    private static TemporaryTableGroupResult success() {
        StoreScope scope = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));
        TableGroup group = new TableGroup(
            new TableGroupId(GROUP_ID),
            scope,
            "A区临组1",
            "temporary",
            new CapacityRange(4, 8),
            TableGroupStatus.CREATED
        );
        return TemporaryTableGroupResult.success(
            group,
            List.of(),
            List.of(
                new TableGroupMember(UUID.randomUUID(), scope, group.id(), new TableId(TABLE_A_ID), "temporary_member"),
                new TableGroupMember(UUID.randomUUID(), scope, group.id(), new TableId(TABLE_B_ID), "temporary_member")
            )
        );
    }
}
