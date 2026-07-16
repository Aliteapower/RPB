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
}
