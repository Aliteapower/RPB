package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationShareInfo;
import com.rpb.reservation.reservation.application.ReservationShareInfoResult;
import org.springframework.stereotype.Component;

@Component
public class ReservationShareInfoApiMapper {

    public ReservationShareInfoResponse toResponse(ReservationShareInfoResult result) {
        ReservationShareInfo shareInfo = result.shareInfo();
        return new ReservationShareInfoResponse(
            true,
            new ReservationShareInfoResponse.ShareInfoResponse(
                shareInfo.reservationId(),
                shareInfo.reservationNo(),
                shareInfo.channel(),
                shareInfo.shareText(),
                shareInfo.customerMaskedPhone(),
                shareInfo.customerPhoneAvailable(),
                shareInfo.canOpenWhatsAppLink(),
                shareInfo.whatsappLink(),
                shareInfo.shareToken(),
                shareInfo.sharePath(),
                shareInfo.shareTitle(),
                shareInfo.shareSummary()
            )
        );
    }
}
