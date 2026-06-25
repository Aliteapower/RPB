package com.rpb.reservation.table.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.application.TemporaryTableGroupResult;
import com.rpb.reservation.table.application.service.DissolveTemporaryTableGroupCommand;
import com.rpb.reservation.table.application.service.SaveTemporaryTableGroupCommand;
import com.rpb.reservation.table.application.service.TemporaryTableGroupApplicationService;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TemporaryTableGroupControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/tables/temporary-groups";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000001260");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000001260");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000001299");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000001260");
    private static final UUID TABLE_A_ID = UUID.fromString("70000000-0000-0000-0000-000000001260");
    private static final UUID TABLE_B_ID = UUID.fromString("70000000-0000-0000-0000-000000001261");
    private static final UUID GROUP_ID = UUID.fromString("71000000-0000-0000-0000-000000001260");
    private static final UUID AREA_ID = UUID.fromString("60000000-0000-0000-0000-000000001260");
    private static final StoreScope SCOPE = new StoreScope(new TenantId(TENANT_ID), new StoreId(STORE_ID));

    private TemporaryTableGroupApplicationService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(TemporaryTableGroupApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("table.switch"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new TemporaryTableGroupController(
                applicationService,
                actorProvider,
                new TemporaryTableGroupApiMapper(),
                new TemporaryTableGroupApiErrorMapper()
            ))
            .build();
    }

    @Test
    void savesTemporaryGroupWithNameAndSelectedTables() throws Exception {
        when(applicationService.saveForManagement(any())).thenReturn(success());

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupName":"A区临组1","businessDate":"2026-06-25","tableIds":["%s","%s"]}
                    """.formatted(TABLE_A_ID, TABLE_B_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tableGroupId").value(GROUP_ID.toString()))
            .andExpect(jsonPath("$.groupName").value("A区临组1"))
            .andExpect(jsonPath("$.groupType").value("temporary"))
            .andExpect(jsonPath("$.status").value("created"))
            .andExpect(jsonPath("$.tableIds[0]").value(TABLE_A_ID.toString()));

        ArgumentCaptor<SaveTemporaryTableGroupCommand> commandCaptor = ArgumentCaptor.forClass(SaveTemporaryTableGroupCommand.class);
        verify(applicationService).saveForManagement(commandCaptor.capture());
        SaveTemporaryTableGroupCommand command = commandCaptor.getValue();
        assertThat(command.scope().tenantId().value()).isEqualTo(TENANT_ID);
        assertThat(command.scope().storeId().value()).isEqualTo(STORE_ID);
        assertThat(command.groupName()).isEqualTo("A区临组1");
        assertThat(command.businessDate().value()).isEqualTo(LocalDate.parse("2026-06-25"));
        assertThat(command.tableIds()).containsExactly(TABLE_A_ID, TABLE_B_ID);
    }

    @Test
    void dissolvesTemporaryGroup() throws Exception {
        when(applicationService.dissolveForManagement(any())).thenReturn(success());

        mockMvc.perform(delete(ENDPOINT + "/{tableGroupId}", STORE_ID, GROUP_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tableGroupId").value(GROUP_ID.toString()));

        ArgumentCaptor<DissolveTemporaryTableGroupCommand> commandCaptor = ArgumentCaptor.forClass(DissolveTemporaryTableGroupCommand.class);
        verify(applicationService).dissolveForManagement(commandCaptor.capture());
        assertThat(commandCaptor.getValue().tableGroupId()).isEqualTo(GROUP_ID);
    }

    @Test
    void endpointDeclaresTableSwitchPermission() throws Exception {
        Method save = TemporaryTableGroupController.class.getMethod(
            "saveTemporaryGroup",
            UUID.class,
            SaveTemporaryTableGroupRequest.class
        );
        Method dissolve = TemporaryTableGroupController.class.getMethod(
            "dissolveTemporaryGroup",
            UUID.class,
            UUID.class
        );

        assertThat(save.getAnnotation(RequireAppGate.class).permission()).isEqualTo("table.switch");
        assertThat(dissolve.getAnnotation(RequireAppGate.class).permission()).isEqualTo("table.switch");
    }

    @Test
    void missingPermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("table.view"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        mockMvc.perform(delete(ENDPOINT + "/{tableGroupId}", OTHER_STORE_ID, GROUP_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(applicationService);
    }

    private static TemporaryTableGroupResult success() {
        TableGroup group = new TableGroup(
            new TableGroupId(GROUP_ID),
            SCOPE,
            "A区临组1",
            "temporary",
            new CapacityRange(4, 8),
            TableGroupStatus.CREATED
        );
        List<DiningTable> tables = List.of(
            table(TABLE_A_ID, "A01"),
            table(TABLE_B_ID, "A02")
        );
        List<TableGroupMember> members = List.of(
            new TableGroupMember(UUID.randomUUID(), SCOPE, group.id(), new TableId(TABLE_A_ID), "temporary_member"),
            new TableGroupMember(UUID.randomUUID(), SCOPE, group.id(), new TableId(TABLE_B_ID), "temporary_member")
        );
        return TemporaryTableGroupResult.success(group, tables, members);
    }

    private static DiningTable table(UUID tableId, String code) {
        return new DiningTable(
            new TableId(tableId),
            SCOPE,
            AREA_ID,
            code,
            new CapacityRange(2, 4),
            DiningTableStatus.AVAILABLE,
            true
        );
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions, Set<UUID> storeIds) {
        return CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
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
