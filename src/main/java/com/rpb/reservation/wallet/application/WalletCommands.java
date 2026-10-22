package com.rpb.reservation.wallet.application;

import java.util.*;

/** Command DTOs deliberately omit tenant IDs, customer authorization claims and online payment status. */
public final class WalletCommands {
    private WalletCommands() {}
    public record ProgramSettings(Integer version,List<String> channels,String idempotencyKey) {}
    public record TopupCreate(UUID accountId,String amount,String channel,String idempotencyKey) {}
    public record CollectionConfirm(String channel,String amount,String collectionAccount,String receiptReference,
                                    String terminalCode,String note,boolean verified,String idempotencyKey) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record BillCreate(UUID accountId,String amount,String idempotencyKey,String customerCode) {
        public BillCreate(UUID accountId,String amount,String idempotencyKey) {this(accountId,amount,idempotencyKey,null);}
    }
    public record Pay(UUID quoteId,String idempotencyKey) {}
    public record RefundCreate(String sourceType,UUID sourceId,String amount,String reason,String idempotencyKey) {}
    public record CollectionException(String sourceType,UUID sourceId,CollectionConfirm confirmation,String reason,String idempotencyKey) {}
    public record CollectionSettingsUpdate(Integer version,String status,String paynowType,String paynowMobile,String paynowUen,String merchantName,boolean issuerVerified,String idempotencyKey) {}
}
