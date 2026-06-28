package com.rpb.reservation.reservation.application;

import java.util.UUID;

public record ReservationShareInfo(
    UUID reservationId,
    String reservationNo,
    String channel,
    String shareText,
    String customerMaskedPhone,
    boolean customerPhoneAvailable,
    boolean canOpenWhatsAppLink,
    String whatsappLink,
    String shareToken,
    String sharePath,
    String shareTitle,
    String shareSummary
) {
}
