package com.rpb.reservation.wallet.application;

import java.time.Instant;
import java.util.*;

/** Explicit immutable application DTOs, never persistence/JPA entities. Amounts are integer SGD cents. */
public final class WalletViews {
    private WalletViews() {}
    public record Program(UUID id, UUID tenantId, UUID operatingEntityId, List<String> channels, String status, int version) {}
    public record Account(UUID id, UUID tenantId, UUID programId, UUID customerId, long balanceCents,
                          long reservedCents, String status, int version) {
        public long availableCents() { return balanceCents-reservedCents; }
    }
    public record Entry(UUID id, UUID accountId, UUID storeId, String kind, UUID sourceId, long amountCents,
                        long beforeCents, long afterCents, int accountVersion, Instant createdAt) {}
    public record Topup(UUID id, UUID programId, UUID accountId, UUID storeId, long amountCents, String channel,
                        String status, long refundedCents, int version, Instant createdAt) {}
    public record Bill(UUID id, UUID programId, UUID accountId, UUID storeId, long amountCents, long walletPaidCents,
                       long externalPaidCents, String status, boolean walletApplied, boolean closed, String refundStatus, int version, Instant createdAt) {
        public long remainingCents() { return amountCents-walletPaidCents-externalPaidCents; }
    }
    public record Collection(UUID id, UUID topupId, UUID billId, String channel, long amountCents,
                             long refundedCents, String receiptReference, String collectionAccount, String terminalCode,
                             boolean exception, String exceptionReason, Instant createdAt) {}
    public record Quote(UUID id, UUID billId, UUID accountId, long amountCents, long remainingCents,
                        int accountVersion, int billVersion, Instant expiresAt) {}
    public record PaymentCode(String type, String code, Instant expiresAt) {}
    public record PaymentCodeStatus(Bill bill) {}
    public record Refund(UUID id, UUID accountId, UUID storeId, UUID topupId, UUID billId, long amountCents,
                         long walletCents, String status, String reason, int version, Instant createdAt, UUID exceptionCollectionId) {}
    public record RefundPart(UUID id, UUID collectionId, String channel, long amountCents,
                             boolean completed, String receiptReference) {}
    public record RefundDetail(Refund refund, List<RefundPart> parts) {}
    public record Paid(Bill bill, Account account) {}
    public record TopupResult(Topup topup, Account account) {}
    public record BillReceipt(Bill bill,List<Collection> collections) {}
    public record TopupReceipt(Topup topup,List<Collection> collections) {}
    public record StoreIdentity(UUID tenantId, UUID storeId, UUID operatingEntityId, String operatingEntityName,
                                String storeName, String tenantCode, String timezone, String locale, UUID logoMediaAssetId,
                                String contactPhone,String address) {
        public StoreIdentity(UUID tenantId,UUID storeId,UUID operatingEntityId,String operatingEntityName,String storeName,String tenantCode,String timezone,String locale,UUID logoMediaAssetId) {
            this(tenantId,storeId,operatingEntityId,operatingEntityName,storeName,tenantCode,timezone,locale,logoMediaAssetId,null,null);
        }
    }
    public record Customer(UUID id, String customerCode, String displayName, String email, String phone) {}
    public record Context(StoreIdentity store, Program program, boolean transactionsEnabled, String denialReason,
                          boolean paynowConfigured) {}
    public record CollectionSettings(Integer version,boolean inherited,String status,String paynowType,String paynowMobile,String paynowUen,String merchantName) {}
    public record CustomerHome(Context context, List<Account> accounts, List<Bill> pendingBills) {}
    public record Reconciliation(long topupCashCents,long topupPaynowCents,long topupCardCents,long walletSpentCents,
                                 long externalBillCents,long refundsCents,long pendingTopupCents,long outstandingBillCents,
                                 long programBalanceCents,long programReservedCents,long exceptionReceivedCents,
                                 long creditedTopupCents,long pendingRefundCents,long ledgerMismatchAccounts,List<Collection> collections) {}
}
