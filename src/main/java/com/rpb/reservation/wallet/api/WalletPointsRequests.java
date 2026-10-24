package com.rpb.reservation.wallet.api;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rpb.reservation.loyalty.domain.PointsException;
import com.rpb.reservation.loyalty.application.PointsViews.*;
import java.io.IOException;
public final class WalletPointsRequests {
 private WalletPointsRequests(){}
 public static class Quantity extends JsonDeserializer<Integer>{
  @Override public Integer deserialize(JsonParser p,DeserializationContext c)throws IOException{
   if(p.currentToken()!=JsonToken.VALUE_NUMBER_INT)throw PointsException.invalid();
   var value=p.getBigIntegerValue();if(value.signum()<=0)throw PointsException.invalid();
   if(value.compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE))>0)throw PointsException.conflict("POINTS_LIMIT_EXCEEDED","积分数量超过上限");
   return value.intValueExact();
  }
 }
 public static class Version extends JsonDeserializer<Integer>{
  @Override public Integer deserialize(JsonParser p,DeserializationContext c)throws IOException{
   if(p.currentToken()!=JsonToken.VALUE_NUMBER_INT)throw PointsException.invalid();
   var value=p.getBigIntegerValue();if(value.signum()<0||value.bitLength()>31)throw PointsException.invalid();return value.intValueExact();
  }
 }
 public record Grant(@JsonDeserialize(using=Quantity.class) Integer quantity,String reason,String note,String externalReference,String idempotencyKey){
  @JsonAnySetter public void reject(String key,Object value){throw PointsException.invalid();}
  public Mutation command(){return new Mutation(quantity,reason,note,externalReference,null,null,idempotencyKey);}
 }
 public record Deduction(@JsonDeserialize(using=Quantity.class) Integer quantity,String reason,String note,@JsonDeserialize(using=Version.class) Integer expectedVersion,String idempotencyKey){
  @JsonAnySetter public void reject(String key,Object value){throw PointsException.invalid();}
  public Mutation command(){return new Mutation(quantity,reason,note,null,null,expectedVersion,idempotencyKey);}
 }
 public record Redeem(@JsonDeserialize(using=Quantity.class) Integer quantity,String redemptionDescription,String note,String externalReference,@JsonDeserialize(using=Version.class) Integer expectedVersion,String idempotencyKey){
  @JsonAnySetter public void reject(String key,Object value){throw PointsException.invalid();}
  public Mutation command(){return new Mutation(quantity,"兑换",note,externalReference,redemptionDescription,expectedVersion,idempotencyKey);}
 }
 public record Reversal(String reason,@JsonDeserialize(using=Version.class) Integer expectedVersion,String idempotencyKey){
  @JsonAnySetter public void reject(String key,Object value){throw PointsException.invalid();}
  public Reverse command(){return new Reverse(reason,expectedVersion,idempotencyKey);}
 }
}
