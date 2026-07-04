package com.rpb.reservation.reservation.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationPublicShareResponse(
    boolean success,
    ShareResponse share
) {
    public record ShareResponse(
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
}
