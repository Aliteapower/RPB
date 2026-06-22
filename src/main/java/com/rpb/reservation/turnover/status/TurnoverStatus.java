package com.rpb.reservation.turnover.status;

/**
 * Turnover statuses confirmed by schema and migration constraints.
 */
public enum TurnoverStatus {
    PENDING("pending"),
    RECORDED("recorded"),
    ARCHIVED("archived");

    private final String code;

    TurnoverStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
