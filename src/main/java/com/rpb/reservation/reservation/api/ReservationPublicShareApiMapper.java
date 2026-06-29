package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationPublicShare;
import com.rpb.reservation.reservation.application.ReservationPublicShareError;
import com.rpb.reservation.reservation.application.ReservationPublicShareResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ReservationPublicShareApiMapper {

    public ReservationPublicShareResponse toResponse(ReservationPublicShareResult result) {
        ReservationPublicShare share = result.share();
        return new ReservationPublicShareResponse(
            true,
            new ReservationPublicShareResponse.ShareResponse(
                share.reservationNo(),
                share.storeName(),
                share.reservationDate(),
                share.reservationTime(),
                share.partySize(),
                share.tableCode(),
                share.tablePending(),
                share.arrivalNote(),
                share.storePhone(),
                share.storeAddress(),
                share.googleMapUrl(),
                share.shareTitle(),
                share.shareSummary(),
                share.shareText()
            )
        );
    }

    public ResponseEntity<ReservationPublicShareApiErrorResponse> toErrorResponse(ReservationPublicShareResult result) {
        ReservationPublicShareApiErrorCode code = toApiErrorCode(result.error());
        return ResponseEntity.status(code.httpStatus()).body(ReservationPublicShareApiErrorResponse.of(code));
    }

    private static ReservationPublicShareApiErrorCode toApiErrorCode(ReservationPublicShareError error) {
        return switch (error) {
            case INVALID_TOKEN -> ReservationPublicShareApiErrorCode.INVALID_TOKEN;
            case TOKEN_NOT_FOUND -> ReservationPublicShareApiErrorCode.TOKEN_NOT_FOUND;
            case TOKEN_REVOKED -> ReservationPublicShareApiErrorCode.TOKEN_REVOKED;
            case TOKEN_EXPIRED -> ReservationPublicShareApiErrorCode.TOKEN_EXPIRED;
            case RESERVATION_NOT_FOUND -> ReservationPublicShareApiErrorCode.RESERVATION_NOT_FOUND;
            case PERSISTENCE_ERROR -> ReservationPublicShareApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
