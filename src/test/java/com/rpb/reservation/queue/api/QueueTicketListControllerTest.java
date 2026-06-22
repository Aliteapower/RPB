package com.rpb.reservation.queue.api;

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
import com.rpb.reservation.queue.application.QueueTicketListError;
import com.rpb.reservation.queue.application.QueueTicketListItem;
import com.rpb.reservation.queue.application.QueueTicketListPage;
import com.rpb.reservation.queue.application.QueueTicketListResult;
import com.rpb.reservation.queue.application.query.QueueTicketListQuery;
import com.rpb.reservation.queue.application.service.QueueTicketListApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QueueTicketListControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000992");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000992");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000999");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000992");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000992");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000992");
    private static final Instant CREATED_AT = Instant.parse("2030-06-20T03:00:00Z");
    private static final Instant CALLED_AT = Instant.parse("2030-06-20T03:10:00Z");
    private static final Instant HOLD_UNTIL_AT = Instant.parse("2030-06-20T03:13:00Z");

    private QueueTicketListApplicationService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(QueueTicketListApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("queue.view"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new QueueTicketListController(
                applicationService,
                actorProvider,
                new QueueTicketListApiMapper(),
                new QueueTicketListApiErrorMapper()
            ))
            .build();
    }

    @Test
    void listsQueueTicketsAndMapsQueryParameters() throws Exception {
        when(applicationService.listQueueTickets(any())).thenReturn(success());

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("status", "called")
                .param("limit", "25")
                .param("offset", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].queueTicketId").value(QUEUE_TICKET_ID.toString()))
            .andExpect(jsonPath("$.items[0].queueTicketNumber").value(12))
            .andExpect(jsonPath("$.items[0].queueTicketStatus").value("called"))
            .andExpect(jsonPath("$.items[0].partySize").value(4))
            .andExpect(jsonPath("$.items[0].partySizeGroup").value("3-4"))
            .andExpect(jsonPath("$.items[0].reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.items[0].reservationCode").value("R-LIST-0992"))
            .andExpect(jsonPath("$.items[0].reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.items[0].customerName").value("Queue Guest"))
            .andExpect(jsonPath("$.items[0].customerPhoneMasked").value("****5432"))
            .andExpect(jsonPath("$.items[0].createdAt").value(CREATED_AT.toString()))
            .andExpect(jsonPath("$.items[0].calledAt").value(CALLED_AT.toString()))
            .andExpect(jsonPath("$.items[0].holdUntilAt").value(HOLD_UNTIL_AT.toString()))
            .andExpect(jsonPath("$.items[0].expiresAt").value(HOLD_UNTIL_AT.toString()))
            .andExpect(jsonPath("$.page.limit").value(25))
            .andExpect(jsonPath("$.page.offset").value(5))
            .andExpect(jsonPath("$.page.total").value(1));

        ArgumentCaptor<QueueTicketListQuery> queryCaptor = ArgumentCaptor.forClass(QueueTicketListQuery.class);
        verify(applicationService).listQueueTickets(queryCaptor.capture());
        QueueTicketListQuery query = queryCaptor.getValue();
        assertThat(query.tenantId()).isEqualTo(TENANT_ID);
        assertThat(query.storeId()).isEqualTo(STORE_ID);
        assertThat(query.actorId()).isEqualTo(ACTOR_ID);
        assertThat(query.actorType()).isEqualTo("staff");
        assertThat(query.status()).isEqualTo("called");
        assertThat(query.limit()).isEqualTo("25");
        assertThat(query.offset()).isEqualTo("5");
    }

    @Test
    void endpointDeclaresAppGateReservationQueueViewPermission() throws Exception {
        Method method = QueueTicketListController.class.getMethod(
            "listQueueTickets",
            UUID.class,
            String.class,
            String.class,
            String.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("queue.view");
    }

    @Test
    void missingPermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("queue.call"), Set.of(STORE_ID));

        mockMvc.perform(get(ENDPOINT, STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.error.messageKey").value("queue.list.forbidden"));

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
    void mapsApplicationErrorsToStableApiErrors() throws Exception {
        assertApplicationError(QueueTicketListError.INVALID_STATUS, 400, "INVALID_STATUS");
        assertApplicationError(QueueTicketListError.INVALID_LIMIT, 400, "INVALID_LIMIT");
        assertApplicationError(QueueTicketListError.INVALID_OFFSET, 400, "INVALID_OFFSET");
        assertApplicationError(QueueTicketListError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND");
        assertApplicationError(QueueTicketListError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH");
        assertApplicationError(QueueTicketListError.STORE_ACCESS_DENIED, 403, "FORBIDDEN");
        assertApplicationError(QueueTicketListError.PERSISTENCE_ERROR, 500, "PERSISTENCE_ERROR");
    }

    private void assertApplicationError(QueueTicketListError applicationError, int expectedStatus, String expectedApiCode) throws Exception {
        when(applicationService.listQueueTickets(any())).thenReturn(QueueTicketListResult.failure(applicationError));

        mockMvc.perform(get(ENDPOINT, STORE_ID))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedApiCode))
            .andExpect(jsonPath("$.error.messageKey").value("queue.list." + expectedApiCode.toLowerCase()))
            .andExpect(jsonPath("$.error.details").isMap());
    }

    private static QueueTicketListResult success() {
        return QueueTicketListResult.success(
            List.of(new QueueTicketListItem(
                QUEUE_TICKET_ID,
                12,
                "called",
                4,
                "3-4",
                RESERVATION_ID,
                "R-LIST-0992",
                "arrived",
                "Queue Guest",
                "****5432",
                CREATED_AT,
                CALLED_AT,
                HOLD_UNTIL_AT,
                HOLD_UNTIL_AT
            )),
            new QueueTicketListPage(25, 5, 1)
        );
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
