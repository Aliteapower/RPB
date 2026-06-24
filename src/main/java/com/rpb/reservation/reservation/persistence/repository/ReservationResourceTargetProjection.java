package com.rpb.reservation.reservation.persistence.repository;

import java.util.UUID;

public interface ReservationResourceTargetProjection {
    String getResourceType();

    UUID getResourceId();
}
