package com.rpb.reservation.wallet.proof;
import java.util.*;
public final class WalletProofViews {
 private WalletProofViews(){}
 public record Context(String sourceType,UUID sourceId,String channel,String systemReference,long amountCents,String expectedAccount,String merchantName){}
 public record Evidence(UUID id,Context context,String bankReference,Map<String,Object> extracted,Map<String,String> checks,Map<String,Object> confirmation,String imageUrl){}
 public record Image(byte[] bytes,String contentType){}
}
