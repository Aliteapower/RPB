package com.rpb.reservation.reservation.application;

import java.util.UUID;

public record ReservationShareInfo(
    UUID reservationId,
    String reservationNo,
    String channel,
    String shareText,
    String customerMaskedPhone,
    boolean customerPhoneAvailable,
    String senderLabel,
    boolean canOpenWhatsAppLink,
    String whatsappLink,
    boolean canOpenWechatLink,
    String wechatLink,
    String wechatShareText,
    String shareToken,
    String sharePath,
    String shareTitle,
    String shareSummary
) {
}
