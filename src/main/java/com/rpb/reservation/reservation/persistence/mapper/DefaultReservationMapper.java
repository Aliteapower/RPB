package com.rpb.reservation.reservation.persistence.mapper;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.persistence.entity.ReservationEntity;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationCode;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class DefaultReservationMapper implements ReservationMapper {

    @Override
    public Reservation toDomain(ReservationEntity entity) {
        return new Reservation(
            new ReservationId(entity.getId()),
            new StoreScope(new TenantId(entity.getTenantId()), new StoreId(entity.getStoreId())),
            entity.getCustomerId() == null ? null : new CustomerId(entity.getCustomerId()),
            new ReservationCode(entity.getReservationCode()),
            new PartySize(entity.getPartySize()),
            new BusinessDate(entity.getBusinessDate()),
            toInstant(entity.getReservedStartAt()),
            toInstant(entity.getReservedEndAt()),
            toInstant(entity.getHoldUntilAt()),
            statusFromCode(entity.getStatus()),
            entity.getSourceChannel(),
            entity.getCancellationReasonCode(),
            entity.getNoShowReasonCode(),
            entity.getNote(),
            toInstant(entity.getCreatedAt()),
            toInstant(entity.getUpdatedAt()),
            toInstant(entity.getDeletedAt())
        );
    }

    @Override
    public ReservationEntity toEntity(Reservation domain) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return ReservationEntity.of(
            domain.id().value(),
            domain.scope().tenantId().value(),
            domain.scope().storeId().value(),
            domain.customerId() == null ? null : domain.customerId().value(),
            domain.reservationCode().value(),
            domain.partySize().value(),
            Objects.requireNonNull(domain.businessDate(), "reservation_business_date_required").value(),
            toUtc(Objects.requireNonNull(domain.reservedStartAt(), "reservation_reserved_start_at_required")),
            toUtc(Objects.requireNonNull(domain.reservedEndAt(), "reservation_reserved_end_at_required")),
            toUtc(Objects.requireNonNull(domain.holdUntilAt(), "reservation_hold_until_at_required")),
            domain.status().code(),
            domain.sourceChannel(),
            domain.cancellationReasonCode(),
            domain.noShowReasonCode(),
            domain.note(),
            domain.createdAt() == null ? now : toUtc(domain.createdAt()),
            domain.updatedAt() == null ? now : toUtc(domain.updatedAt()),
            toUtc(domain.deletedAt()),
            0
        );
    }

    private static ReservationStatus statusFromCode(String code) {
        for (ReservationStatus status : ReservationStatus.values()) {
            if (status.code().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown_reservation_status");
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toUtc(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
