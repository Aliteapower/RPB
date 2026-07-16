package com.rpb.reservation.reservation.api;

public class PlatformReservationShareTemplateSeedApiException extends RuntimeException {
    private final PlatformReservationShareTemplateSeedApiErrorCode code;

    public PlatformReservationShareTemplateSeedApiException(PlatformReservationShareTemplateSeedApiErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public PlatformReservationShareTemplateSeedApiErrorCode code() {
        return code;
    }
}
