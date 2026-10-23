package com.rpb.reservation.wallet.application;

import java.time.Instant;
import java.util.*;

/** Explicit immutable application DTOs, never persistence/JPA entities. Amounts are integer SGD cents. */
public final class WalletViews {
    private WalletViews() {}
    public record Program(UUID id, UUID tenantId, UUID operatingEntityId, List<String> channels, String status, int version) {}
    public record Account(UUID id, UUID tenantId, UUID programId, UUID customerId, long balanceCents,
                          long reservedCents, String status, int version,Long principalBalanceCents,Long bonusBalanceCents,Long principalReservedCents,Long bonusReservedCents) {
        public Account {if(principalBalanceCents==null)principalBalanceCents=balanceCents;if(bonusBalanceCents==null)bonusBalanceCents=0L;if(principalReservedCents==null)principalReservedCents=reservedCents;if(bonusReservedCents==null)bonusReservedCents=0L;}
        public Account(UUID id,UUID tenant,UUID program,UUID customer,long balance,long reserved,String status,int version){this(id,tenant,program,customer,balance,reserved,status,version,balance,0L,reserved,0L);}
        public long availableCents() { return balanceCents-reservedCents; }
    }
    public record Entry(UUID id, UUID accountId, UUID storeId, String kind, UUID sourceId, long amountCents,
                        long beforeCents, long afterCents, int accountVersion, Instant createdAt,Long principalCents,Long bonusCents,List<FundingPart> allocations) {
        public Entry {if(principalCents==null)principalCents=amountCents;if(bonusCents==null)bonusCents=0L;if(allocations==null)allocations=List.of();}
        public Entry(UUID id,UUID account,UUID store,String kind,UUID source,long amount,long before,long after,int version,Instant at,Long principal,Long bonus){this(id,account,store,kind,source,amount,before,after,version,at,principal,bonus,List.of());}
        public Entry(UUID id,UUID account,UUID store,String kind,UUID source,long amount,long before,long after,int version,Instant at){this(id,account,store,kind,source,amount,before,after,version,at,amount,0L);}
    }
    public record FundingPart(UUID lotId,long principalCents,long bonusCents) {}
    public record Topup(UUID id, UUID programId, UUID accountId, UUID storeId, long amountCents, String channel,
                        String status, long refundedCents, int version, Instant createdAt,long bonusCents,String deductionPolicy,String origin,Instant customerDeclaredPaidAt,Instant creditedAt,Map<String,Object> offerSnapshot,Map<String,Object> policySnapshot) {
        public Topup {if(deductionPolicy==null)deductionPolicy="proportional";if(origin==null)origin="staff";if(offerSnapshot==null)offerSnapshot=Map.of();if(policySnapshot==null)policySnapshot=Map.of();}
        public long principalCents(){return amountCents;}public long creditTotalCents(){return amountCents+bonusCents;}
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public long getPrincipalCents(){return amountCents;}
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public long getCreditTotalCents(){return amountCents+bonusCents;}
        public Topup(UUID id,UUID program,UUID account,UUID store,long amount,String channel,String status,long refunded,int version,Instant at){this(id,program,account,store,amount,channel,status,refunded,version,at,0,"proportional","staff",null,null,Map.of(),Map.of());}
    }
    public record Bill(UUID id, UUID programId, UUID accountId, UUID storeId, long amountCents, long walletPaidCents,
                       long externalPaidCents, String status, boolean walletApplied, boolean closed, String refundStatus, int version, Instant createdAt,Long principalPaidCents,Long bonusPaidCents) {
        public Bill {if(principalPaidCents==null)principalPaidCents=walletPaidCents;if(bonusPaidCents==null)bonusPaidCents=0L;}
        public Bill(UUID id,UUID program,UUID account,UUID store,long amount,long wallet,long external,String status,boolean applied,boolean closed,String refundStatus,int version,Instant at){this(id,program,account,store,amount,wallet,external,status,applied,closed,refundStatus,version,at,wallet,0L);}
        public long remainingCents() { return amountCents-walletPaidCents-externalPaidCents; }
    }
    public record Collection(UUID id, UUID topupId, UUID billId, String channel, long amountCents,
                             long refundedCents, String receiptReference, String collectionAccount, String terminalCode,
                             boolean exception, String exceptionReason, Instant createdAt) {}
    public record Quote(UUID id, UUID billId, UUID accountId, long amountCents, long remainingCents,
                        int accountVersion, int billVersion, Instant expiresAt,Long principalDebitCents,Long bonusDebitCents) {
        public Quote {if(principalDebitCents==null)principalDebitCents=amountCents;if(bonusDebitCents==null)bonusDebitCents=0L;}
        public Quote(UUID id,UUID bill,UUID account,long amount,long remaining,int av,int bv,Instant expires){this(id,bill,account,amount,remaining,av,bv,expires,amount,0L);}
    }
    public record PaymentCode(String type, String code, Instant expiresAt) {}
    public record PaymentCodeStatus(Bill bill) {}
    public record Refund(UUID id, UUID accountId, UUID storeId, UUID topupId, UUID billId, long amountCents,
                         long walletCents, String status, String reason, int version, Instant createdAt, UUID exceptionCollectionId,Long principalCents,Long bonusCents) {
        public Refund {if(principalCents==null)principalCents=walletCents;if(bonusCents==null)bonusCents=0L;}
        public Refund(UUID id,UUID account,UUID store,UUID topup,UUID bill,long amount,long wallet,String status,String reason,int version,Instant at,UUID exception){this(id,account,store,topup,bill,amount,wallet,status,reason,version,at,exception,wallet,0L);}
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public long getCashPrincipalRefundCents(){return topupId!=null?amountCents:0;}
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public long getPrincipalReturnCents(){return billId!=null&&exceptionCollectionId==null?principalCents:0;}
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public long getBonusReturnCents(){return billId!=null&&exceptionCollectionId==null?bonusCents:0;}
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY) public long getBonusRevokeCents(){return topupId!=null&&exceptionCollectionId==null?bonusCents:0;}
    }
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
                                 long creditedTopupCents,long pendingRefundCents,long ledgerMismatchAccounts,List<Collection> collections,
                                 long creditedPrincipalCents,long creditedBonusCents,long spentPrincipalCents,long spentBonusCents,
                                 long returnedPrincipalCents,long returnedBonusCents,long cashPrincipalRefundCents,long revokedBonusCents,
                                 long programPrincipalBalanceCents,long programBonusBalanceCents,long programPrincipalReservedCents,long programBonusReservedCents,
                                 long componentMismatchAccounts,long fundingMismatchAccounts) {
        public Reconciliation(long cash,long paynow,long card,long spent,long external,long refunds,long pending,long due,long balance,long reserved,long exceptions,long credited,long pendingRefund,long mismatches,List<Collection> collections){
            this(cash,paynow,card,spent,external,refunds,pending,due,balance,reserved,exceptions,credited,pendingRefund,mismatches,collections,
                 credited,0,spent,0,0,0,0,0,balance,0,reserved,0,0,0);
        }
    }
}
