package com.rpb.reservation.publicbooking.application;

import com.rpb.reservation.reservation.application.ReservationTimeSlot;
import java.time.LocalDate;
import java.util.List;

public record PublicBookingContext(
    PublicBookingStoreProfile store,
    PublicBookingSettings settings,
    LocalDate businessDate,
    List<ReservationTimeSlot> timeSlots,
    List<PublicBookingAuthProvider> authProviders
) {
}
