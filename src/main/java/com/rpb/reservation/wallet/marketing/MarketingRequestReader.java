package com.rpb.reservation.wallet.marketing;
import com.fasterxml.jackson.databind.*;
import com.rpb.reservation.wallet.domain.WalletException;
import org.springframework.stereotype.Component;
/** Per-endpoint strict reader, independent of the application's compatibility Jackson defaults. */
@Component
public class MarketingRequestReader {
 private final ObjectMapper mapper;
 public MarketingRequestReader(ObjectMapper mapper){this.mapper=mapper;}
 public <T>T read(JsonNode body,Class<T> type){
  if(body==null||!body.isObject())throw single();
  // Prevent Jackson scalar coercion from weakening the wire contract.
  for(String key:new String[]{"expectedVersion","draftVersion","offerVersion","priority"}){
   JsonNode value=body.get(key);if(value!=null&&(!value.isIntegralNumber()||!value.canConvertToInt()))throw single();
  }
  for(String key:new String[]{"selfTopupEnabled","selfServiceEnabled","campaignsEnabled","pointsEnabled"}){
   JsonNode value=body.get(key);if(value!=null&&!value.isBoolean())throw single();
  }
  for(String key:new String[]{"offers","baseOffers"}){
   JsonNode offers=body.get(key);if(offers!=null&&offers.isArray())for(JsonNode offer:offers){
    JsonNode amount=offer.get("amount"),bonus=offer.get("bonus");
    if(amount!=null&&!amount.isTextual())throw single();
    // Preserve the existing optional bonus contract: absent/null means zero.
    if(bonus!=null&&!bonus.isNull()&&!bonus.isTextual())throw single();
   }
  }
  try{return mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(body);}
  catch(java.io.IOException e){throw single();}
 }
 private WalletException single(){return new WalletException("WALLET_TOPUP_SINGLE_OFFER_REQUIRED",400,"请仅选择一个充值档位，不允许数量、金额、赠送或身份覆盖字段");}
}
