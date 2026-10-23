package com.rpb.reservation.wallet.transactions;

import java.time.*;
import java.util.*;

/** Public read models, independent of mutable command/ledger representations. */
public final class TransactionViews {
    private TransactionViews() {}
    public record Item(UUID id,String sourceType,String type,String status,Instant occurredAt,Instant createdAt,
        Instant postedAt,UUID storeId,String storeName,String timeZone,String currency,long amountCents,
        long quantity,String direction,long walletDeltaCents,long principalCents,long bonusCents,long creditTotalCents,
        long walletPaidCents,long externalPaidCents,String refundStatus,UUID customerId,String customerName,
        String customerPhone,String summary,String actorName,String originalSourceType,UUID originalId) {}
    public record Store(UUID id,String name,String timeZone) {}
    public record Period(String mode,LocalDate startDate,LocalDate endDate,String timeZone) {}
    public record Capabilities(boolean money,boolean points,boolean refunds,boolean reversals) {}
    public record Summary(long consumptionCents,long walletPaymentCents,long externalPaymentCents,
        long topupPrincipalCents,long topupBonusCents,long topupCreditCents,long refundCents,long moneyReversalCents,
        long pointsGranted,long pointsDeducted,long pointsRedeemed,long pointsReversed,long pointsReversalIn,long pointsReversalOut) {}
    public record Page(List<Item> items,String nextCursor,long totalCount,Summary summary,List<Store> stores,
        Period period,Capabilities capabilities,String category) {}
    public record Event(String type,Instant occurredAt,String label) {}
    public record Detail(Item transaction,List<Item> related,List<Event> events) {}
}
