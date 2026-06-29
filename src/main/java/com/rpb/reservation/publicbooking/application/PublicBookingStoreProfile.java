package com.rpb.reservation.publicbooking.application;

import com.rpb.reservation.common.scope.StoreScope;

public record PublicBookingStoreProfile(
    StoreScope scope,
    String storeName,
    String timezone,
    String shareAddress,
    String googleMapUrl,
    String shareContactPhone
) {
}
