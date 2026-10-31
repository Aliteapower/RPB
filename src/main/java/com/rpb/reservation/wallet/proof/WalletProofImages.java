package com.rpb.reservation.wallet.proof;

import com.rpb.reservation.common.media.SafeImageValidation;
import com.rpb.reservation.wallet.domain.WalletException;

final class WalletProofImages {
 private WalletProofImages(){}
 static void validate(String type,byte[] bytes){
  try{SafeImageValidation.validate(type,bytes);}catch(IllegalArgumentException e){throw WalletException.invalid();}
 }
}
