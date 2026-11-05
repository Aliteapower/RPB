package com.rpb.reservation.wallet.redemption;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rpb.reservation.wallet.api.WalletPointsRequests;
import com.rpb.reservation.wallet.domain.WalletException;
import java.util.UUID;
import java.time.Instant;
public final class RedemptionDtos {
 private RedemptionDtos(){}
 public record Create(UUID customerId,@JsonDeserialize(using=WalletPointsRequests.Quantity.class) Integer quantity,String description,String idempotencyKey){@JsonAnySetter public void reject(String k,Object v){throw WalletException.invalid();}}
 public record Confirm(@JsonDeserialize(using=WalletPointsRequests.Version.class) Integer expectedVersion,String idempotencyKey){@JsonAnySetter public void reject(String k,Object v){throw WalletException.invalid();}}
 public record CodeInput(String code){@JsonAnySetter public void reject(String k,Object v){throw WalletException.invalid();}}
 public record Cancel(String idempotencyKey){@JsonAnySetter public void reject(String k,Object v){throw WalletException.invalid();}}
 public record View(UUID id,UUID storeId,UUID customerId,int quantity,String description,String status,Instant expiresAt,UUID entryId,int balance,int expectedVersion){}
 public record Code(String code,Instant expiresAt){}
}
