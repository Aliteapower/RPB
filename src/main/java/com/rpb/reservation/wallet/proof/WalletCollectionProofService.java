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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.proof.WalletProofViews.*;

@Service
@Transactional(readOnly=true)
public class WalletCollectionProofService {
 private final WalletCollectionProofRepository repository;private final WalletAccess access;
 private final WalletTopupRepository topups;private final WalletBillRepository bills;private final WalletCollectionPort port;
 private final PaymentProofImageInspector inspector;
 public WalletCollectionProofService(WalletCollectionProofRepository repository,WalletAccess access,WalletTopupRepository topups,WalletBillRepository bills,WalletCollectionPort port,PaymentProofImageInspector inspector,ObjectMapper json){this.repository=repository;this.access=access;this.topups=topups;this.bills=bills;this.port=port;this.inspector=inspector;}
 public Context context(StoreScope s,CurrentActor actor,String type,UUID id,String channel){
  access.staff(s,actor,"wallet.collection.confirm",false);
  if(!"paynow".equals(WalletMoney.channelType(channel))||!port.paynowConfigured(s))throw WalletException.invalid();
  long amount;
  if("topup".equals(type)){var t=topups.get(s,id,false);if(!t.channel().equals(channel)||!Set.of("pending_collection","verified_pending_credit").contains(t.status()))throw conflict("充值单不再待收款");amount=t.amountCents();}
  else if("bill".equals(type)){var b=bills.get(s,id,false);if(b.closed()||b.remainingCents()<=0)throw conflict("账单已关闭或结清");if(!access.commercial(s).allowed()&&b.walletPaidCents()+b.externalPaidCents()==0)throw WalletException.forbidden();amount=b.remainingCents();}
  else throw WalletException.invalid();
  var profile=port.settings(s);String account="mobile".equals(profile.paynowType())?profile.paynowMobile():profile.paynowUen();
  if(account==null||account.isBlank())throw WalletException.invalid();
  return new Context(type,id,channel,WalletPayNowReference.forSource(id),amount,normalizeAccount(account),profile.merchantName());
 }
 @Transactional
 public Evidence scan(StoreScope s,CurrentActor actor,String type,UUID id,String channel,String amount,String key,String filename,String contentType,byte[] bytes){
  var current=context(s,actor,type,id,channel);long cents=WalletMoney.cents(amount);
  if(cents>current.amountCents()||("topup".equals(type)&&cents!=current.amountCents())||key==null||key.isBlank()||key.length()>160)throw WalletException.invalid();
  return new WalletProofCapture(repository,inspector).capture(s,current,cents,key,filename,contentType,bytes,actor.actorId(),null);
 }

 public List<Evidence> list(StoreScope s,CurrentActor actor,String type,UUID id){access.staff(s,actor,"wallet.view",false);sourceExists(s,type,id);return repository.list(s,type,id);}
 public List<Evidence> submitted(StoreScope s,CurrentActor actor,String type,UUID id){access.staff(s,actor,"wallet.collection.confirm",false);sourceExists(s,type,id);return repository.customerProofs(s,type,id,null,true).stream().map(WalletCollectionProofRepository.Stored::evidence).toList();}
 public Image image(StoreScope s,CurrentActor actor,UUID id){access.staff(s,actor,actor!=null&&actor.hasPermission("wallet.view")?"wallet.view":"wallet.collection.confirm",false);var stored=repository.get(s,id,false);return new Image(stored.image(),stored.contentType());}
 /** Called within the existing collection/credit transaction and under its source/account locks. */
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public void validate(StoreScope s,UUID topup,UUID bill,long amount,WalletCommands.CollectionConfirm command,String account){
  if(command.proofId()==null)return;
  var stored=repository.get(s,command.proofId(),true);var e=stored.evidence();var c=e.context();
  if(stored.collectionId()!=null||!"paynow".equals(WalletMoney.channelType(command.channel()))||!c.sourceType().equals(topup!=null?"topup":"bill")||!c.sourceId().equals(topup!=null?topup:bill)||!c.channel().equals(command.channel())||c.amountCents()!=amount||!c.expectedAccount().equals(normalizeAccount(account))||e.checks().containsValue("mismatch"))throw conflict("凭证与当前收款不匹配，请核查或转异常复核");
  if(e.bankReference()!=null&&!e.bankReference().equalsIgnoreCase(command.receiptReference().trim()))throw conflict("银行 Ref 与凭证识别结果不一致");
 }
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public void bind(StoreScope s,WalletViews.Collection collection,WalletCommands.CollectionConfirm command,UUID actor){
  if(command.proofId()==null)return;
  repository.bind(s,command.proofId(),collection.id(),Map.of("bankReference",collection.receiptReference(),"actualAccount",collection.collectionAccount(),"terminalCode",collection.terminalCode(),"note",Objects.toString(command.note(),""),"confirmedBy",actor.toString(),"verified",true));
 }
 private void sourceExists(StoreScope s,String type,UUID id){if("topup".equals(type))topups.get(s,id,false);else if("bill".equals(type))bills.get(s,id,false);else throw WalletException.invalid();}
 private Evidence replay(WalletCollectionProofRepository.Stored stored,String digest){if(!stored.requestDigest().equals(digest))throw conflict("上传重试内容已改变，请重新选择凭证");return stored.evidence();}
 static String normalizeAccount(String account){String value=account.substring(account.lastIndexOf(':')+1).replaceAll("[\\s-]","").toUpperCase(Locale.ROOT);if(value.matches("[689]\\d{7}"))value="+65"+value;return value;}
 private static String check(String actual,String expected){return actual==null||actual.isBlank()?"missing":actual.equalsIgnoreCase(expected)?"match":"mismatch";}
 private static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 public static WalletException conflict(String message){return new WalletException("WALLET_PROOF_CONFLICT",409,message);}
}
