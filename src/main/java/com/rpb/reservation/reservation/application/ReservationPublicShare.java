package com.rpb.reservation.reservation.application;

public record ReservationPublicShare(
    String reservationNo,
    String storeName,
    String reservationDate,
    String reservationTime,
    int partySize,
    String tableCode,
    boolean tablePending,
    String arrivalNote,
    String storePhone,
    String storeEmail,
    String storeWhatsappPhone,
    String storeAddress,
    String googleMapUrl,
    String shareTitle,
    String shareSummary,
    String shareText
) {
}
