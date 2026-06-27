package com.rpb.reservation.reservation.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationShareInfoResponse(
    boolean success,
    ShareInfoResponse shareInfo
) {
    public record ShareInfoResponse(
        UUID reservationId,
        String reservationNo,
        String channel,
        String shareText,
        String customerMaskedPhone,
        boolean customerPhoneAvailable,
        boolean canOpenWhatsAppLink,
        String whatsappLink
    ) {
    }
}
