package com.rpb.reservation.reservation.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.persistence.adapter.ReservationPersistenceAdapter;
import com.rpb.reservation.reservation.persistence.entity.ReservationEntity;
import com.rpb.reservation.reservation.persistence.mapper.DefaultReservationMapper;
import com.rpb.reservation.reservation.persistence.repository.ReservationJpaRepository;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationCode;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.store.application.port.out.StorePolicyRepositoryPort;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.persistence.adapter.StorePersistenceAdapter;
import com.rpb.reservation.store.persistence.entity.StorePolicyEntity;
import com.rpb.reservation.store.persistence.mapper.DefaultStorePolicyMapper;
import com.rpb.reservation.store.persistence.mapper.StoreMapper;
import com.rpb.reservation.store.persistence.repository.StoreJpaRepository;
import com.rpb.reservation.store.persistence.repository.StorePolicyJpaRepository;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationCreatePersistenceTest {

    private final TenantId tenantId = new TenantId(UUID.randomUUID());
    private final StoreId storeId = new StoreId(UUID.randomUUID());
    private final StoreScope scope = new StoreScope(tenantId, storeId);

    @Test
    void reservationMapperMapsConfirmedReservationAndPreservesNullableCustomerAndUtcTime() {
        Instant start = Instant.parse("2026-06-20T11:00:00Z");
        Instant end = Instant.parse("2026-06-20T12:30:00Z");
        Instant holdUntil = Instant.parse("2026-06-20T11:15:00Z");
        LocalDate businessDate = LocalDate.of(2026, 6, 20);

        Reservation reservation = new Reservation(
            new ReservationId(UUID.randomUUID()),
            scope,
            null,
            new ReservationCode("R-1001"),
            new PartySize(4),
            new BusinessDate(businessDate),
            start,
            end,
            holdUntil,
            ReservationStatus.CONFIRMED,
            "staff",
            null,
            null,
            "window seat preferred",
            start.minusSeconds(120),
            start.minusSeconds(60),
            null
        );

        DefaultReservationMapper mapper = new DefaultReservationMapper();
        ReservationEntity entity = mapper.toEntity(reservation);

        assertThat(entity.getTenantId()).isEqualTo(tenantId.value());
        assertThat(entity.getStoreId()).isEqualTo(storeId.value());
        assertThat(entity.getCustomerId()).isNull();
        assertThat(entity.getReservationCode()).isEqualTo("R-1001");
        assertThat(entity.getPartySize()).isEqualTo(4);
        assertThat(entity.getBusinessDate()).isEqualTo(businessDate);
        assertThat(entity.getReservedStartAt().toInstant()).isEqualTo(start);
        assertThat(entity.getReservedEndAt().toInstant()).isEqualTo(end);
        assertThat(entity.getHoldUntilAt().toInstant()).isEqualTo(holdUntil);
        assertThat(entity.getStatus()).isEqualTo("confirmed");
        assertThat(entity.getSourceChannel()).isEqualTo("staff");

        Reservation roundTrip = mapper.toDomain(entity);
        assertThat(roundTrip.customerId()).isNull();
        assertThat(roundTrip.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(roundTrip.reservedStartAt()).isEqualTo(start);
        assertThat(roundTrip.reservedEndAt()).isEqualTo(end);
        assertThat(roundTrip.holdUntilAt()).isEqualTo(holdUntil);
        assertThat(roundTrip.businessDate().value()).isEqualTo(businessDate);

        assertThat(Arrays.stream(ReservationEntity.class.getDeclaredFields()).map(Field::getName))
            .doesNotContain("queueTicketId", "seatingId", "tableId", "tableGroupId");
    }

    @Test
    void reservationAdapterFindsByIdWithinStoreScope() {
        ReservationJpaRepository jpaRepository = mock(ReservationJpaRepository.class);
        ReservationId reservationId = new ReservationId(UUID.randomUUID());
        ReservationEntity entity = reservationEntity(reservationId.value(), UUID.randomUUID(), "R-2001", "confirmed", null);
        when(jpaRepository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            reservationId.value(),
            tenantId.value(),
            storeId.value()
        )).thenReturn(Optional.of(entity));

        ReservationRepositoryPort adapter = new ReservationPersistenceAdapter(
            jpaRepository,
            new DefaultReservationMapper(),
            mock(EntityManager.class)
        );

        Optional<Reservation> found = adapter.findById(scope, reservationId);

        assertThat(found).isPresent();
        assertThat(found.get().scope()).isEqualTo(scope);
        assertThat(found.get().reservationCode().value()).isEqualTo("R-2001");
        verify(jpaRepository).findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            reservationId.value(),
            tenantId.value(),
            storeId.value()
        );
    }

    @Test
    void reservationCodeLookupIsStoreScoped() {
        ReservationJpaRepository jpaRepository = mock(ReservationJpaRepository.class);
        when(jpaRepository.existsByTenantIdAndStoreIdAndReservationCodeAndDeletedAtIsNull(
            tenantId.value(),
            storeId.value(),
            "R-3001"
        )).thenReturn(true);

        ReservationRepositoryPort adapter = new ReservationPersistenceAdapter(
            jpaRepository,
            new DefaultReservationMapper(),
            mock(EntityManager.class)
        );

        assertThat(adapter.existsByReservationCode(scope, new ReservationCode("R-3001"))).isTrue();
        verify(jpaRepository).existsByTenantIdAndStoreIdAndReservationCodeAndDeletedAtIsNull(
            tenantId.value(),
            storeId.value(),
            "R-3001"
        );
    }

    @Test
    void saveWrapsPersistenceConflictWithoutExposingRawDatabaseException() {
        ReservationJpaRepository jpaRepository = mock(ReservationJpaRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        doThrow(new PersistenceException("duplicate key")).when(entityManager).persist(any(ReservationEntity.class));

        ReservationRepositoryPort adapter = new ReservationPersistenceAdapter(
            jpaRepository,
            new DefaultReservationMapper(),
            entityManager
        );

        Reservation reservation = new Reservation(
            new ReservationId(UUID.randomUUID()),
            scope,
            null,
            new ReservationCode("R-3002"),
            new PartySize(2),
            new BusinessDate(LocalDate.of(2026, 6, 20)),
            Instant.parse("2026-06-20T11:00:00Z"),
            Instant.parse("2026-06-20T12:30:00Z"),
            Instant.parse("2026-06-20T11:15:00Z"),
            ReservationStatus.CONFIRMED,
            "staff",
            null,
            null,
            null,
            Instant.parse("2026-06-19T10:00:00Z"),
            Instant.parse("2026-06-19T10:00:00Z"),
            null
        );

        assertThatThrownBy(() -> adapter.save(scope, reservation))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("reservation_persistence_write_failed")
            .hasCauseInstanceOf(PersistenceException.class);
    }

    @Test
    void activeDuplicateDetectsOverlappingCustomerReservationAndIgnoresAnonymousCustomer() {
        ReservationJpaRepository jpaRepository = mock(ReservationJpaRepository.class);
        CustomerId customerId = new CustomerId(UUID.randomUUID());
        TimeRange range = new TimeRange(
            Instant.parse("2026-06-20T11:00:00Z"),
            Instant.parse("2026-06-20T12:30:00Z")
        );
        when(jpaRepository.existsActiveDuplicate(
            eq(tenantId.value()),
            eq(storeId.value()),
            eq(customerId.value()),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)
        )).thenReturn(true);

        ReservationRepositoryPort adapter = new ReservationPersistenceAdapter(
            jpaRepository,
            new DefaultReservationMapper(),
            mock(EntityManager.class)
        );

        assertThat(adapter.existsActiveDuplicate(scope, customerId, range)).isTrue();
        assertThat(adapter.existsActiveDuplicate(scope, null, range)).isFalse();
        verify(jpaRepository, never()).existsActiveDuplicate(
            eq(tenantId.value()),
            eq(storeId.value()),
            eq((UUID) null),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void capacityUsageSumsOnlyActiveOverlappingPartySizeForBusinessDate() {
        ReservationJpaRepository jpaRepository = mock(ReservationJpaRepository.class);
        BusinessDate businessDate = new BusinessDate(LocalDate.of(2026, 6, 20));
        TimeRange range = new TimeRange(
            Instant.parse("2026-06-20T11:00:00Z"),
            Instant.parse("2026-06-20T12:30:00Z")
        );
        when(jpaRepository.sumActiveOverlappingPartySize(
            eq(tenantId.value()),
            eq(storeId.value()),
            eq(businessDate.value()),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)
        )).thenReturn(9L);

        ReservationRepositoryPort adapter = new ReservationPersistenceAdapter(
            jpaRepository,
            new DefaultReservationMapper(),
            mock(EntityManager.class)
        );

        assertThat(adapter.findActiveCapacityUsage(scope, businessDate, range)).isEqualTo(9);
        verify(jpaRepository).sumActiveOverlappingPartySize(
            eq(tenantId.value()),
            eq(storeId.value()),
            eq(businessDate.value()),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void storePolicyPortFindsCurrentPolicyByStoreScopeAndMissingPolicyIsEmpty() {
        StoreJpaRepository storeRepository = mock(StoreJpaRepository.class);
        StorePolicyJpaRepository policyRepository = mock(StorePolicyJpaRepository.class);
        StorePolicyEntity policyEntity = StorePolicyEntity.of(
            UUID.randomUUID(),
            tenantId.value(),
            storeId.value(),
            15,
            3,
            90,
            "same_group_tail",
            "default_capacity_time_area_group",
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
            null,
            OffsetDateTime.now(ZoneOffset.UTC),
            OffsetDateTime.now(ZoneOffset.UTC),
            null,
            0
        );
        when(policyRepository.findCurrentPolicy(eq(tenantId.value()), eq(storeId.value()), any(OffsetDateTime.class)))
            .thenReturn(Optional.of(policyEntity), Optional.empty());

        StorePolicyRepositoryPort adapter = new StorePersistenceAdapter(
            storeRepository,
            policyRepository,
            mock(StoreMapper.class),
            new DefaultStorePolicyMapper()
        );

        Optional<StorePolicy> found = adapter.findByStoreScope(scope);
        Optional<StorePolicy> missing = adapter.findByStoreScope(scope);

        assertThat(found).isPresent();
        assertThat(found.get().reservationHoldMinutes()).isEqualTo(15);
        assertThat(found.get().expectedDiningMinutes()).isEqualTo(90);
        assertThat(missing).isEmpty();
    }

    private ReservationEntity reservationEntity(
        UUID reservationId,
        UUID customerId,
        String reservationCode,
        String status,
        OffsetDateTime deletedAt
    ) {
        OffsetDateTime start = OffsetDateTime.ofInstant(Instant.parse("2026-06-20T11:00:00Z"), ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.ofInstant(Instant.parse("2026-06-20T12:30:00Z"), ZoneOffset.UTC);
        OffsetDateTime holdUntil = OffsetDateTime.ofInstant(Instant.parse("2026-06-20T11:15:00Z"), ZoneOffset.UTC);
        return ReservationEntity.of(
            reservationId,
            tenantId.value(),
            storeId.value(),
            customerId,
            reservationCode,
            4,
            LocalDate.of(2026, 6, 20),
            start,
            end,
            holdUntil,
            status,
            "staff",
            null,
            null,
            null,
            start.minusMinutes(5),
            start.minusMinutes(4),
            deletedAt,
            0
        );
    }
}
