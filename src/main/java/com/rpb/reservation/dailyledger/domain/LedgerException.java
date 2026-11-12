package com.rpb.reservation.dailyledger.domain;

public class LedgerException extends RuntimeException {
    private final String code;
    private final int status;
    public LedgerException(String code, int status, String message) {
        super(message); this.code=code; this.status=status;
    }
    public String code() { return code; }
    public int status() { return status; }
    public static LedgerException invalid(String message) { return new LedgerException("REQUEST_INVALID",400,message); }
    public static LedgerException conflict(String code, String message) { return new LedgerException(code,409,message); }
}
