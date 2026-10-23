package com.rpb.reservation.wallet.application;

import java.util.*;

/** Command DTOs deliberately omit tenant IDs, customer authorization claims and online payment status. */
public final class WalletCommands {
    private WalletCommands() {}
    public record ProgramSettings(Integer version,List<String> channels,String idempotencyKey) {}
    public record TopupCreate(UUID accountId,String amount,String channel,String idempotencyKey,UUID quoteId) {
        public TopupCreate(UUID account,String amount,String channel,String key){this(account,amount,channel,key,null);}
    }
    public record CollectionConfirm(String channel,String amount,String collectionAccount,String receiptReference,
                                    String terminalCode,String note,boolean verified,String idempotencyKey) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record BillCreate(UUID accountId,String amount,String idempotencyKey,String customerCode) {
        public BillCreate(UUID accountId,String amount,String idempotencyKey) {this(accountId,amount,idempotencyKey,null);}
    }
    public record Pay(UUID quoteId,String idempotencyKey) {}
    public record ScanCharge(UUID accountId,String amount,String customerCode,Integer expectedAccountVersion,
                             UUID billId,Integer expectedBillVersion,String idempotencyKey) {}
    public record RefundCreate(String sourceType,UUID sourceId,String amount,String reason,String idempotencyKey,String mode,Integer expectedAccountVersion) {
        public RefundCreate(String type,UUID source,String amount,String reason,String key){this(type,source,amount,reason,key,null,null);}
        public RefundCreate(String type,UUID source,String amount,String reason,String key,String mode){this(type,source,amount,reason,key,mode,null);}
    }
    public record CollectionException(String sourceType,UUID sourceId,CollectionConfirm confirmation,String reason,String idempotencyKey) {}
    public record CollectionSettingsUpdate(Integer version,String status,String paynowType,String paynowMobile,String paynowUen,String merchantName,boolean issuerVerified,String idempotencyKey) {}
}
