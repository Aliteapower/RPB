package com.rpb.reservation.seating.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.value.SeatingId;
import java.util.Optional;
import java.util.UUID;

public interface SeatingRepositoryPort {

    Optional<Seating> findById(StoreScope scope, SeatingId seatingId);

    Optional<Seating> findActiveBySource(StoreScope scope, String sourceType, UUID sourceId);

    boolean existsActiveResourceOccupancy(StoreScope scope, String resourceType, UUID resourceId);

    Optional<Seating> findActiveOccupancy(StoreScope scope, String resourceType, UUID resourceId);

    Optional<SeatingResource> findActiveResourceBySeating(StoreScope scope, SeatingId seatingId);

    Seating save(StoreScope scope, Seating seating);

    SeatingResource saveResource(StoreScope scope, SeatingResource resource);
}
