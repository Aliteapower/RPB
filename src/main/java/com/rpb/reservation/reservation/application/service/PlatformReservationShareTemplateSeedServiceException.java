package com.rpb.reservation.reservation.application.service;

public class PlatformReservationShareTemplateSeedServiceException extends RuntimeException {
    private final PlatformReservationShareTemplateSeedServiceErrorCode code;

    public PlatformReservationShareTemplateSeedServiceException(PlatformReservationShareTemplateSeedServiceErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public PlatformReservationShareTemplateSeedServiceErrorCode code() {
        return code;
    }
}
