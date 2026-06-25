package com.rpb.reservation.staffhome.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.queue.application.port.out.QueueTicketOverviewMetric;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.reservation.application.ReservationTodayViewError;
import com.rpb.reservation.reservation.application.ReservationTodayViewItem;
import com.rpb.reservation.reservation.application.ReservationTodayViewResult;
import com.rpb.reservation.reservation.application.query.ReservationTodayViewQuery;
import com.rpb.reservation.reservation.application.service.ReservationTodayViewApplicationService;
import com.rpb.reservation.staffhome.application.service.StaffHomeOverviewApplicationService;
import com.rpb.reservation.table.application.TableResourceItem;
import com.rpb.reservation.table.application.TableResourceListQuery;
import com.rpb.reservation.table.application.TableResourceListResult;
import com.rpb.reservation.table.application.service.TableResourceListApplicationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StaffHomeOverviewApplicationServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000002301");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000002301");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000002301");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");
    private static final Instant START_AT = Instant.parse("2030-06-20T10:00:00Z");
    private static final Instant END_AT = Instant.parse("2030-06-20T11:30:00Z");
    private static final Instant HOLD_AT = Instant.parse("2030-06-20T10:15:00Z");

    private ReservationTodayViewApplicationService reservationService;
    private TableResourceListApplicationService tableService;
    private QueueTicketRepositoryPort queueTicketRepository;
    private StaffHomeOverviewApplicationService service;

    @BeforeEach
    void setUp() {
        reservationService = mock(ReservationTodayViewApplicationService.class);
        tableService = mock(TableResourceListApplicationService.class);
        queueTicketRepository = mock(QueueTicketRepositoryPort.class);
        service = new StaffHomeOverviewApplicationService(
            reservationService,
            tableService,
            queueTicketRepository
        );
    }

    @Test
    void buildsTodayOperationalOverviewFromExistingReadModels() {
        when(reservationService.getToday(any())).thenReturn(ReservationTodayViewResult.success(
            STORE_ID,
            BUSINESS_DATE,
            "Asia/Singapore",
            "all",
            List.of(
                reservation("confirmed", 4),
                reservation("arrived", 2),
                reservation("seated", 6),
                reservation("cancelled", 3)
            )
        ));
        when(tableService.listResources(any())).thenReturn(TableResourceListResult.success(List.of(
            table("A01", "available", true),
            table("A02", "reserved", false),
            table("B01", "occupied", false),
            table("VIP1", "cleaning", false),
            temporaryGroup("b", "active")
        )));
        when(queueTicketRepository.findOverviewMetrics(any(), any())).thenReturn(List.of(
            new QueueTicketOverviewMetric("waiting", "1-2", 2, 4),
            new QueueTicketOverviewMetric("called", "7+", 1, 8),
            new QueueTicketOverviewMetric("seated", "3-4", 3, 9),
            new QueueTicketOverviewMetric("skipped", "1-2", 1, 2)
        ));

        StaffHomeOverviewResult result = service.getOverview(new StaffHomeOverviewQuery(
            TENANT_ID,
            STORE_ID,
            ACTOR_ID,
            "staff",
            BUSINESS_DATE.toString()
        ));

        assertThat(result.success()).isTrue();
        StaffHomeOverview overview = result.overview();
        assertThat(overview.storeId()).isEqualTo(STORE_ID);
        assertThat(overview.businessDate()).isEqualTo(BUSINESS_DATE);
        assertThat(overview.reservation().totalReservations()).isEqualTo(4);
        assertThat(overview.reservation().totalPartySize()).isEqualTo(15);
        assertThat(overview.reservation().arrivedReservations()).isEqualTo(1);
        assertThat(overview.reservation().arrivedPartySize()).isEqualTo(2);
        assertThat(overview.reservation().seatedReservations()).isEqualTo(1);
        assertThat(overview.reservation().seatedPartySize()).isEqualTo(6);
        assertThat(overview.reservation().cancelledReservations()).isEqualTo(1);
        assertThat(overview.queue().waitingTickets()).isEqualTo(2);
        assertThat(overview.queue().waitingPartySize()).isEqualTo(4);
        assertThat(overview.queue().calledTickets()).isEqualTo(1);
        assertThat(overview.queue().calledPartySize()).isEqualTo(8);
        assertThat(overview.queue().seatedTickets()).isEqualTo(3);
        assertThat(overview.queue().skippedTickets()).isEqualTo(1);
        assertThat(overview.tables().totalTables()).isEqualTo(4);
        assertThat(overview.tables().availableTables()).isEqualTo(1);
        assertThat(overview.tables().reservedTables()).isEqualTo(1);
        assertThat(overview.tables().occupiedTables()).isEqualTo(1);
        assertThat(overview.tables().cleaningTables()).isEqualTo(1);
        assertThat(overview.tables().temporaryGroups()).isEqualTo(1);
        assertThat(overview.partySizeGroups())
            .extracting(StaffHomeOverview.PartySizeGroupMetrics::label)
            .containsExactly("1-2", "3-4", "5-6", "7+");
        assertThat(overview.partySizeGroups().get(0).groups()).isEqualTo(2);
        assertThat(overview.partySizeGroups().get(0).partySize()).isEqualTo(4);
        assertThat(overview.partySizeGroups().get(3).groups()).isEqualTo(1);
        assertThat(overview.partySizeGroups().get(3).partySize()).isEqualTo(8);

        ArgumentCaptor<ReservationTodayViewQuery> reservationQueryCaptor =
            ArgumentCaptor.forClass(ReservationTodayViewQuery.class);
        verify(reservationService).getToday(reservationQueryCaptor.capture());
        assertThat(reservationQueryCaptor.getValue().status()).isEqualTo("all");

        ArgumentCaptor<TableResourceListQuery> tableQueryCaptor =
            ArgumentCaptor.forClass(TableResourceListQuery.class);
        verify(tableService).listResources(tableQueryCaptor.capture());
        assertThat(tableQueryCaptor.getValue().businessDate().value()).isEqualTo(BUSINESS_DATE);
        assertThat(tableQueryCaptor.getValue().includeGroups()).isTrue();

        ArgumentCaptor<StoreScope> scopeCaptor = ArgumentCaptor.forClass(StoreScope.class);
        ArgumentCaptor<BusinessDate> businessDateCaptor = ArgumentCaptor.forClass(BusinessDate.class);
        verify(queueTicketRepository).findOverviewMetrics(scopeCaptor.capture(), businessDateCaptor.capture());
        assertThat(scopeCaptor.getValue().tenantId().value()).isEqualTo(TENANT_ID);
        assertThat(scopeCaptor.getValue().storeId().value()).isEqualTo(STORE_ID);
        assertThat(businessDateCaptor.getValue().value()).isEqualTo(BUSINESS_DATE);
    }

    @Test
    void mapsReservationReadErrorsToOverviewErrors() {
        when(reservationService.getToday(any())).thenReturn(
            ReservationTodayViewResult.failure(ReservationTodayViewError.INVALID_BUSINESS_DATE)
        );

        StaffHomeOverviewResult result = service.getOverview(new StaffHomeOverviewQuery(
            TENANT_ID,
            STORE_ID,
            ACTOR_ID,
            "staff",
            "bad-date"
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(StaffHomeOverviewError.INVALID_BUSINESS_DATE);
    }

    private static ReservationTodayViewItem reservation(String status, int partySize) {
        return new ReservationTodayViewItem(
            UUID.randomUUID(),
            "R-" + status,
            status,
            partySize,
            START_AT,
            END_AT,
            HOLD_AT,
            BUSINESS_DATE,
            "Guest",
            null,
            "****1234",
            null,
            null,
            null,
            null,
            null
        );
    }

    private static TableResourceItem table(String code, String status, boolean selectable) {
        return new TableResourceItem(
            "dining_table",
            UUID.randomUUID(),
            code,
            code,
            "A区",
            2,
            4,
            status,
            selectable,
            selectable ? null : "status_unavailable",
            List.of()
        );
    }

    private static TableResourceItem temporaryGroup(String code, String status) {
        return new TableResourceItem(
            "table_group",
            "temporary",
            UUID.randomUUID(),
            code,
            code,
            null,
            2,
            10,
            status,
            true,
            null,
            List.of("A01", "B01"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
