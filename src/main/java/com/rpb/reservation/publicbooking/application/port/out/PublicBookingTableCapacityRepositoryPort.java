package com.rpb.reservation.publicbooking.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import java.util.List;

public interface PublicBookingTableCapacityRepositoryPort {

    List<Integer> findActiveTableCapacityMaxValues(StoreScope scope);
}
