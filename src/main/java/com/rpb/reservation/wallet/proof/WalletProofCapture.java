package com.rpb.reservation.wallet.proof;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.*;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.domain.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.math.BigDecimal;

import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.proof.WalletProofViews.*;

/** Shared capture only. Callers own authentication, source locking and transactions. */
final class WalletProofCapture {
 private final WalletCollectionProofRepository repository;private final PaymentProofImageInspector inspector;
 WalletProofCapture(WalletCollectionProofRepository repository,PaymentProofImageInspector inspector){this.repository=repository;this.inspector=inspector;}
 Evidence capture(StoreScope s,Context current,long cents,String key,String filename,String contentType,byte[] bytes,UUID actor,UUID customer){
  String type=current.sourceType();UUID id=current.sourceId();String channel=current.channel();
  WalletProofImages.validate(contentType,bytes);
  var snapshot=new Context(type,id,channel,current.systemReference(),cents,current.expectedAccount(),current.merchantName());
  String imageDigest=digest(bytes),requestDigest=digest((type+"|"+id+"|"+channel+"|"+cents+"|"+snapshot.expectedAccount()+"|"+contentType+"|"+imageDigest).getBytes(StandardCharsets.UTF_8));
  var replay=repository.byKey(s,key);if(replay.isPresent()){if(!Objects.equals(customer,replay.get().customerId()))throw WalletException.missing();return replay(replay.get(),requestDigest);}
  PaymentProofOcrFields fields;boolean ocrUnavailable=false;
  try{fields=inspector.inspect(s,bytes,filename);}catch(PaymentServiceException e){ocrUnavailable=true;fields=new PaymentProofOcrFields(null,null,null,null,false,BigDecimal.ZERO,"", "{}");}
  var identifiers=PaymentReceiptIdentifiers.parse(fields.rawText());
  var checks=new LinkedHashMap<String,String>();
  String extractedReference=identifiers.systemReference()!=null?identifiers.systemReference():fields.extractedReference();
  checks.put("reference",check(extractedReference,snapshot.systemReference()));
  checks.put("amount",fields.extractedAmount()==null?"missing":fields.extractedAmount().compareTo(BigDecimal.valueOf(cents,2))==0?"match":"mismatch");
  checks.put("account",check(identifiers.recipient()==null?null:WalletCollectionProofService.normalizeAccount(identifiers.recipient()),snapshot.expectedAccount()));
  checks.put("status",identifiers.failedOrPending()?"mismatch":fields.successDetected()?"match":"missing");
  Map<String,Object> extracted=new LinkedHashMap<>();extracted.put("ocrStatus",ocrUnavailable?"unavailable":"completed");extracted.put("systemReference",extractedReference);extracted.put("amount",fields.extractedAmount());extracted.put("recipient",identifiers.recipient());extracted.put("bankCode",fields.bankCode());extracted.put("paidAt",fields.extractedPaidAt());extracted.put("rawText",fields.rawText());extracted.put("confidence",fields.confidence());
  UUID proofId=UUID.randomUUID();var evidence=new Evidence(proofId,snapshot,identifiers.bankReference(),extracted,checks,Map.of(),"/api/v1/stores/"+s.storeId().value()+"/wallet/collection-proofs/images/"+proofId);
  return replay(repository.insert(s,evidence,key,requestDigest,imageDigest,contentType,bytes,actor,customer),requestDigest);
 }
 static Evidence replay(WalletCollectionProofRepository.Stored stored,String digest){if(!stored.requestDigest().equals(digest))throw WalletCollectionProofService.conflict("上传重试内容已改变，请重新选择凭证");return stored.evidence();}
 private static String check(String actual,String expected){return actual==null||actual.isBlank()?"missing":actual.equalsIgnoreCase(expected)?"match":"mismatch";}
 static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
