package com.rpb.reservation.loyalty.application;
import java.util.UUID;
import java.time.Instant;
public final class PointsViews {
 private PointsViews(){}
 public record Binding(UUID tenantId,UUID issuerId,UUID walletProgramId,UUID storeId,UUID programId){}
 public record Account(@com.fasterxml.jackson.annotation.JsonProperty("accountId") UUID id,UUID programId,UUID walletProgramId,UUID tenantId,UUID issuerId,UUID customerId,int balance,int version){}
 public record Entry(UUID id,UUID accountId,UUID storeId,UUID actorId,String kind,int quantity,int beforeBalance,int afterBalance,int accountVersion,String reason,String note,String externalReference,UUID originalEntryId,String redemptionDescription,Instant createdAt){}
 public record Redemption(UUID id,UUID entryId,String redemptionDescription,String externalReference){}
 public record Result(Account account,Entry entry,Redemption redemption){}
 public record Mutation(Integer quantity,String reason,String note,String externalReference,String redemptionDescription,Integer expectedVersion,String idempotencyKey){}
 public record Reverse(String reason,Integer expectedVersion,String idempotencyKey){}
}
