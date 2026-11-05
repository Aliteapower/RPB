package com.rpb.reservation.wallet.domain;

public class WalletException extends RuntimeException {
    private final String code;
    private final int status;
    public WalletException(String code, int status, String message) {
        super(message); this.code = code; this.status = status;
    }
    public String code() { return code; }
    public int status() { return status; }
    public static WalletException invalid() { return new WalletException("WALLET_REQUEST_INVALID",400,"金额或请求资料不正确"); }
    public static WalletException missing() { return new WalletException("WALLET_NOT_FOUND",404,"记录不存在或无权访问"); }
    public static WalletException conflict(String message) { return new WalletException("WALLET_CONFLICT",409,message); }
    public static WalletException forbidden() { return new WalletException("WALLET_FORBIDDEN",403,"无权执行此操作"); }
}
