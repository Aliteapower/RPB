package com.rpb.reservation.publicbooking.application;

public record PublicBookingEntryResult(
    boolean success,
    PublicBookingStoreProfile store,
    PublicBookingEntryError error
) {
    public static PublicBookingEntryResult success(PublicBookingStoreProfile store) {
        return new PublicBookingEntryResult(true, store, null);
    }

    public static PublicBookingEntryResult failure(PublicBookingEntryError error) {
        return new PublicBookingEntryResult(false, null, error);
    }
}
