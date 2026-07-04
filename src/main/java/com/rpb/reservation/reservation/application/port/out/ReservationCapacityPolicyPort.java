package com.rpb.reservation.reservation.application.port.out;

import com.rpb.reservation.reservation.application.ReservationCapacityDecision;
import com.rpb.reservation.reservation.application.ReservationCapacityQuery;

public interface ReservationCapacityPolicyPort {

    ReservationCapacityDecision evaluate(ReservationCapacityQuery query);

    static ReservationCapacityPolicyPort fixed(int capacityLimit) {
        return query -> {
            int currentUsage = query.currentUsage();
            int partySize = query.partySize().value();
            if (capacityLimit > 0 && currentUsage + partySize <= capacityLimit) {
                return ReservationCapacityDecision.accept(capacityLimit, currentUsage);
            }
            return ReservationCapacityDecision.reject(capacityLimit, currentUsage, "reservation_capacity_insufficient");
        };
    }
}
