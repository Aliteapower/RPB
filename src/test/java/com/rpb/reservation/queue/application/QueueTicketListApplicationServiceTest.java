package com.rpb.reservation.queue.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.queue.application.port.out.QueueTicketListRow;
import com.rpb.reservation.queue.application.port.out.QueueTicketListRows;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.application.query.QueueTicketListQuery;
import com.rpb.reservation.queue.application.service.QueueTicketListApplicationService;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.queue.value.QueueTicketId;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueueTicketListApplicationServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000991");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000991");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000991");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000991");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000991");
    private static final Instant CREATED_AT = Instant.parse("2030-06-20T03:00:00Z");
    private static final Instant CALLED_AT = Instant.parse("2030-06-20T03:10:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2030-06-20T03:13:00Z");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2030, 6, 20);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-06-20T02:00:00Z"), ZoneOffset.UTC);

    private FakeStoreRepository storeRepository;
    private FakeQueueTicketRepository queueTicketRepository;
    private QueueTicketListApplicationService service;

    @BeforeEach
    void setUp() {
        storeRepository = new FakeStoreRepository();
        queueTicketRepository = new FakeQueueTicketRepository();
        service = new QueueTicketListApplicationService(storeRepository, queueTicketRepository, CLOCK);
    }

    @Test
    void listsQueueTicketsWithDefaultPaginationReservationSummaryAndMaskedPhone() {
        queueTicketRepository.rows = new QueueTicketListRows(List.of(row("called")), 1);

        QueueTicketListResult result = service.listQueueTickets(query("called", null, null));

        assertThat(result.success()).isTrue();
        assertThat(result.page().limit()).isEqualTo(50);
        assertThat(result.page().offset()).isEqualTo(0);
        assertThat(result.page().total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        QueueTicketListItem item = result.items().get(0);
        assertThat(item.queueTicketId()).isEqualTo(QUEUE_TICKET_ID);
        assertThat(item.queueTicketNumber()).isEqualTo(12);
        assertThat(item.queueTicketDisplayNumber()).isEqualTo("B12");
        assertThat(item.queueTicketStatus()).isEqualTo("called");
        assertThat(item.partySize()).isEqualTo(4);
        assertThat(item.partySizeGroup()).isEqualTo("3-4");
        assertThat(item.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(item.reservationCode()).isEqualTo("R-LIST-0991");
        assertThat(item.reservationStatus()).isEqualTo("arrived");
        assertThat(item.customerName()).isEqualTo("Queue Guest");
        assertThat(item.customerPhoneMasked()).isEqualTo("****5432");
        assertThat(item.createdAt()).isEqualTo(CREATED_AT);
        assertThat(item.calledAt()).isEqualTo(CALLED_AT);
        assertThat(item.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(item.holdUntilAt()).isEqualTo(EXPIRES_AT);
        assertThat(queueTicketRepository.lastStatus).isEqualTo(QueueTicketStatus.CALLED);
        assertThat(queueTicketRepository.lastBusinessDate.value()).isEqualTo(BUSINESS_DATE);
        assertThat(queueTicketRepository.lastLimit).isEqualTo(50);
        assertThat(queueTicketRepository.lastOffset).isEqualTo(0);
    }

    @Test
    void supportsLimitOffsetAndNoStatusFilter() {
        queueTicketRepository.rows = new QueueTicketListRows(List.of(row("waiting")), 12);

        QueueTicketListResult result = service.listQueueTickets(query(null, "25", "5"));

        assertThat(result.success()).isTrue();
        assertThat(result.page().limit()).isEqualTo(25);
        assertThat(result.page().offset()).isEqualTo(5);
        assertThat(result.page().total()).isEqualTo(12);
        assertThat(queueTicketRepository.lastStatus).isNull();
        assertThat(queueTicketRepository.lastBusinessDate.value()).isEqualTo(BUSINESS_DATE);
        assertThat(queueTicketRepository.lastLimit).isEqualTo(25);
        assertThat(queueTicketRepository.lastOffset).isEqualTo(5);
    }

    @Test
    void passesReceptionFiltersToRepositoryForPhonePartySizeAndTableArea() {
        queueTicketRepository.rows = new QueueTicketListRows(List.of(row("waiting")), 1);

        QueueTicketListResult result = service.listQueueTickets(
            query(null, "50", "0", "A区", "4", "9876")
        );

        assertThat(result.success()).isTrue();
        assertThat(queueTicketRepository.lastTableArea).isEqualTo("A区");
        assertThat(queueTicketRepository.lastPartySize).isEqualTo(4);
        assertThat(queueTicketRepository.lastPhoneDigits).isEqualTo("9876");
    }

    @Test
    void rejectsInvalidStatusLimitAndOffset() {
        assertThat(service.listQueueTickets(query("queued", null, null)).error())
            .isEqualTo(QueueTicketListError.INVALID_STATUS);
        assertThat(service.listQueueTickets(query(null, "-1", null)).error())
            .isEqualTo(QueueTicketListError.INVALID_LIMIT);
        assertThat(service.listQueueTickets(query(null, "101", null)).error())
            .isEqualTo(QueueTicketListError.INVALID_LIMIT);
        assertThat(service.listQueueTickets(query(null, null, "-1")).error())
            .isEqualTo(QueueTicketListError.INVALID_OFFSET);
    }

    @Test
    void returnsStoreNotFoundBeforeQueryingTickets() {
        storeRepository.store = Optional.empty();

        QueueTicketListResult result = service.listQueueTickets(query(null, null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(QueueTicketListError.STORE_NOT_FOUND);
        assertThat(queueTicketRepository.called).isFalse();
    }

    @Test
    void mapsRepositoryFailureToApplicationError() {
        queueTicketRepository.throwPersistence = true;

        QueueTicketListResult result = service.listQueueTickets(query(null, null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(QueueTicketListError.PERSISTENCE_ERROR);
    }

    private static QueueTicketListQuery query(String status, String limit, String offset) {
        return query(status, limit, offset, null, null, null);
    }

    private static QueueTicketListQuery query(
        String status,
        String limit,
        String offset,
        String tableArea,
        String partySize,
        String phoneDigits
    ) {
        return new QueueTicketListQuery(
            TENANT_ID,
            STORE_ID,
            ACTOR_ID,
            "staff",
            status,
            limit,
            offset,
            tableArea,
            partySize,
            phoneDigits
        );
    }

    private static QueueTicketListRow row(String status) {
        return new QueueTicketListRow(
            QUEUE_TICKET_ID,
            12,
            status,
            4,
            "3-4",
            RESERVATION_ID,
            "R-LIST-0991",
            "arrived",
            "Queue Guest",
            "+6598765432",
            CREATED_AT,
            CALLED_AT,
            EXPIRES_AT
        );
    }

    private static final class FakeStoreRepository implements StoreRepositoryPort {
        private Optional<Store> store = Optional.of(Store.skeleton(
            new StoreId(STORE_ID),
            new TenantId(TENANT_ID),
            "queue-list-store",
            "Asia/Singapore",
            "en-SG",
            "active"
        ));

        @Override
        public Optional<Store> findById(StoreScope scope) {
            return store;
        }

        @Override
        public Optional<StorePolicy> findCurrentPolicy(StoreScope scope, OffsetDateTime at) {
            return Optional.empty();
        }

        @Override
        public Store save(StoreScope scope, Store store) {
            throw new UnsupportedOperationException("read_only_test");
        }

        @Override
        public StorePolicy savePolicy(StoreScope scope, StorePolicy policy) {
            throw new UnsupportedOperationException("read_only_test");
        }
    }

    private static final class FakeQueueTicketRepository implements QueueTicketRepositoryPort {
        private QueueTicketListRows rows = new QueueTicketListRows(List.of(), 0);
        private QueueTicketStatus lastStatus;
        private BusinessDate lastBusinessDate;
        private int lastLimit;
        private int lastOffset;
        private String lastTableArea;
        private Integer lastPartySize;
        private String lastPhoneDigits;
        private boolean called;
        private boolean throwPersistence;

        @Override
        public QueueTicketListRows findQueueTicketList(
            StoreScope scope,
            QueueTicketStatus status,
            BusinessDate businessDate,
            int limit,
            int offset,
            String tableArea,
            Integer partySize,
            String phoneDigits
        ) {
            called = true;
            lastStatus = status;
            lastBusinessDate = businessDate;
            lastLimit = limit;
            lastOffset = offset;
            lastTableArea = tableArea;
            lastPartySize = partySize;
            lastPhoneDigits = phoneDigits;
            if (throwPersistence) {
                throw new IllegalStateException("db_down");
            }
            return rows;
        }

        @Override
        public Optional<QueueTicket> findById(StoreScope scope, QueueTicketId queueTicketId) {
            return Optional.empty();
        }

        @Override
        public List<QueueTicket> findActiveQueue(StoreScope scope, UUID queueGroupId, BusinessDate businessDate) {
            return List.of();
        }

        @Override
        public Optional<QueueTicket> findNextCallable(StoreScope scope, UUID queueGroupId, BusinessDate businessDate) {
            return Optional.empty();
        }

        @Override
        public Optional<QueueTicket> findActiveByReservationId(StoreScope scope, UUID reservationId) {
            return Optional.empty();
        }

        @Override
        public boolean existsActiveSourceTicket(StoreScope scope, String sourceType, UUID sourceId) {
            return false;
        }

        @Override
        public QueueTicket save(StoreScope scope, QueueTicket queueTicket) {
            throw new UnsupportedOperationException("read_only_test");
        }
    }
}
