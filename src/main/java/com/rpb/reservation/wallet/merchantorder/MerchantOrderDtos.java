package com.rpb.reservation.wallet.merchantorder;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rpb.reservation.wallet.api.WalletPointsRequests;
import com.rpb.reservation.wallet.domain.WalletException;
import java.time.Instant;
import java.util.UUID;
public final class MerchantOrderDtos {
 private MerchantOrderDtos(){}
 public record Create(String kind,String amount,@JsonDeserialize(using=WalletPointsRequests.Quantity.class) Integer quantity,String description,String idempotencyKey){@JsonAnySetter public void reject(String k,Object v){throw WalletException.invalid();}}
 public record Confirm(@JsonDeserialize(using=WalletPointsRequests.Version.class) Integer expectedVersion,String idempotencyKey){@JsonAnySetter public void reject(String k,Object v){throw WalletException.invalid();}}
 public record CodeInput(String code){@JsonAnySetter public void reject(String k,Object v){throw WalletException.invalid();}}
 public record Cancel(String idempotencyKey){@JsonAnySetter public void reject(String k,Object v){throw WalletException.invalid();}}
 public record Code(String code,Instant expiresAt){}
 public record View(UUID id,String kind,Long amountCents,Integer quantity,String description,String status,Instant expiresAt,UUID customerId,String sourceType,UUID sourceId,Long balance,Integer expectedVersion){}
}
