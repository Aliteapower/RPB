package com.rpb.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.reservation.command.CreateReservationCommand;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationCode;
import com.rpb.reservation.tenant.domain.Tenant;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainSkeletonBoundaryTest {

    @Test
    void reservationSkeletonKeepsReservationBoundarySeparateFromQueue() {
        Reservation reservation = Reservation.skeleton(
            new StoreScope(new TenantId(UUID.randomUUID()), UUID.randomUUID()),
            new ReservationCode("R-1001"),
            new PartySize(4),
            ReservationStatus.DRAFT
        );

        assertThat(reservation.status()).isEqualTo(ReservationStatus.DRAFT);
        assertThat(reservation.confirmIntent()).isEqualTo("reservation.confirm.intent");
    }

    @Test
    void checkInIsRepresentedByBusinessEventNotEntity() {
        BusinessEvent checkInEvent = BusinessEvent.checkInEvent("reservation", UUID.randomUUID());

        assertThat(checkInEvent.eventType()).isEqualTo("reservation_checked_in");
        assertThat(checkInEvent.domainBoundary()).contains("not CheckInEntity");
    }

    @Test
    void commandsAreDomainIntentPlaceholdersNotApiDtos() {
        CreateReservationCommand command = new CreateReservationCommand(
            new StoreScope(new TenantId(UUID.randomUUID()), UUID.randomUUID()),
            new ReservationCode("R-2001"),
            new PartySize(2)
        );

        assertThat(command.intentCode()).isEqualTo("reservation.create.command");
    }

    @Test
    void tenantSkeletonCarriesTenantScopeOnly() {
        Tenant tenant = Tenant.skeleton(new TenantId(UUID.randomUUID()), "tenant-a", "Tenant A", "active");

        assertThat(tenant.scope()).isEqualTo(new TenantScope(tenant.id()));
        assertThat(tenant.domainBoundary()).contains("Tenant is not Store");
    }
}
