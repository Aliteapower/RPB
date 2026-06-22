package com.rpb.reservation.cleaning.application.port.out;

import com.rpb.reservation.cleaning.domain.Cleaning;
import com.rpb.reservation.cleaning.value.CleaningId;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.seating.value.SeatingId;
import java.util.Optional;
import java.util.UUID;

public interface CleaningRepositoryPort {

    Optional<Cleaning> findById(StoreScope scope, CleaningId cleaningId);

    Optional<Cleaning> findActiveByResource(StoreScope scope, String resourceType, UUID resourceId);

    Optional<Cleaning> findBySeating(StoreScope scope, SeatingId seatingId);

    Cleaning save(StoreScope scope, Cleaning cleaning);
}
