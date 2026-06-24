package com.rpb.reservation.table.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import java.time.LocalDate;
import com.rpb.reservation.table.application.TableResourceItem;
import com.rpb.reservation.table.application.TableResourceListQuery;
import com.rpb.reservation.table.application.TableResourceListResult;
import com.rpb.reservation.table.application.service.TableResourceListApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TableResourceListControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/tables";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000001202");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000001202");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000001299");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000001202");
    private static final UUID TABLE_ID = UUID.fromString("70000000-0000-0000-0000-000000001202");
    private static final UUID GROUP_ID = UUID.fromString("71000000-0000-0000-0000-000000001202");
    private static final UUID SEATING_ID = UUID.fromString("80000000-0000-0000-0000-000000001202");
    private static final UUID CLEANING_ID = UUID.fromString("81000000-0000-0000-0000-000000001202");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000001202");

    private TableResourceListApplicationService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(TableResourceListApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("table.view"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new TableResourceListController(applicationService, actorProvider, new TableResourceListApiErrorMapper()))
            .build();
    }

    @Test
    void listsConfiguredTableNumbersAndGroups() throws Exception {
        when(applicationService.listResources(any())).thenReturn(TableResourceListResult.success(List.of(
            new TableResourceItem(
                "dining_table",
                TABLE_ID,
                "A01",
                "A01 靠窗",
                "A区",
                1,
                4,
                "available",
                true,
                null,
                List.of(),
                SEATING_ID,
                null,
                RESERVATION_ID,
                3
            ),
            new TableResourceItem(
                "table_group",
                GROUP_ID,
                "VIP-1",
                "VIP-1",
                null,
                8,
                12,
                "active",
                true,
                null,
                List.of("V01", "V02"),
                null,
                CLEANING_ID
            )
        )));

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("status", "available")
                .param("partySize", "4")
                .param("includeGroups", "true")
                .param("businessDate", "2030-06-20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resources[0].resourceType").value("dining_table"))
            .andExpect(jsonPath("$.resources[0].resourceId").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.resources[0].code").value("A01"))
            .andExpect(jsonPath("$.resources[0].displayName").value("A01 靠窗"))
            .andExpect(jsonPath("$.resources[0].areaName").value("A区"))
            .andExpect(jsonPath("$.resources[0].selectable").value(true))
            .andExpect(jsonPath("$.resources[0].currentSeatingId").value(SEATING_ID.toString()))
            .andExpect(jsonPath("$.resources[0].currentReservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.resources[0].currentPartySize").value(3))
            .andExpect(jsonPath("$.resources[1].resourceType").value("table_group"))
            .andExpect(jsonPath("$.resources[1].resourceId").value(GROUP_ID.toString()))
            .andExpect(jsonPath("$.resources[1].memberTableCodes[0]").value("V01"))
            .andExpect(jsonPath("$.resources[1].memberTableCodes[1]").value("V02"))
            .andExpect(jsonPath("$.resources[1].currentCleaningId").value(CLEANING_ID.toString()));

        ArgumentCaptor<TableResourceListQuery> queryCaptor = ArgumentCaptor.forClass(TableResourceListQuery.class);
        verify(applicationService).listResources(queryCaptor.capture());
        TableResourceListQuery query = queryCaptor.getValue();
        assertThat(query.scope().tenantId().value()).isEqualTo(TENANT_ID);
        assertThat(query.scope().storeId().value()).isEqualTo(STORE_ID);
        assertThat(query.status()).isEqualTo("available");
        assertThat(query.partySize()).isEqualTo(4);
        assertThat(query.includeGroups()).isTrue();
        assertThat(query.businessDate().value()).isEqualTo(LocalDate.parse("2030-06-20"));
    }

    @Test
    void endpointDeclaresAppGateReservationQueueTableViewPermission() throws Exception {
        Method method = TableResourceListController.class.getMethod(
            "listTableResources",
            UUID.class,
            String.class,
            String.class,
            String.class,
            String.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("table.view");
    }

    @Test
    void missingPermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("queue.view"), Set.of(STORE_ID));

        mockMvc.perform(get(ENDPOINT, STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.error.messageKey").value("table.resources.forbidden"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        mockMvc.perform(get(ENDPOINT, OTHER_STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void invalidPartySizeReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(get(ENDPOINT, STORE_ID).param("partySize", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PARTY_SIZE"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void invalidBusinessDateReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(get(ENDPOINT, STORE_ID).param("businessDate", "20-06-2030"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_BUSINESS_DATE"));

        verifyNoInteractions(applicationService);
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions, Set<UUID> storeIds) {
        return CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            roles.contains("customer") ? "customer" : "staff",
            roles,
            permissions,
            storeIds
        );
    }

    private static final class MutableCurrentActorProvider implements CurrentActorProvider {
        private CurrentActor actor;

        private MutableCurrentActorProvider(CurrentActor actor) {
            this.actor = actor;
        }

        @Override
        public Optional<CurrentActor> currentActor() {
            return Optional.ofNullable(actor);
        }
    }
}
