package com.rpb.reservation.wallet.proof;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.payment.application.PaymentProofImageInspector;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.rpb.reservation.wallet.proof.WalletProofViews.*;

/** Customer declarations are evidence only; money still moves through staff collection confirmation. */
@Service
@Transactional(readOnly=true)
public class WalletCustomerProofService {
 private final WalletCollectionProofRepository proofs;
 private final WalletAccess access;
 private final WalletAccountRepository accounts;
 private final WalletTopupRepository topups;
 private final WalletBillRepository bills;
 private final WalletCollectionPort port;
 private final WalletCommandRepository commands;
 private final WalletProofCapture capture;
 public WalletCustomerProofService(WalletCollectionProofRepository proofs,WalletAccess access,WalletAccountRepository accounts,WalletTopupRepository topups,WalletBillRepository bills,WalletCollectionPort port,WalletCommandRepository commands,PaymentProofImageInspector inspector){this.proofs=proofs;this.access=access;this.accounts=accounts;this.topups=topups;this.bills=bills;this.port=port;this.commands=commands;this.capture=new WalletProofCapture(proofs,inspector);}
 public record CustomerEvidence(UUID id,Context context,String bankReference,Map<String,String> checks,String status,String imageUrl){}
 private record Source(UUID accountId,long amount,boolean pending,boolean partiallyPaid){}
 private Source own(StoreScope s,CustomerAuthPrincipal p,String type,UUID id,boolean lock){
  access.customer(s,p,false);
  UUID accountId;
  if("topup".equals(type))accountId=topups.get(s,id,false).accountId();
  else if("bill".equals(type))accountId=bills.get(s,id,false).accountId();
  else throw WalletException.invalid();
  access.own(accounts.account(s,accountId,lock),p);
  if("topup".equals(type)){var t=topups.get(s,id,lock);return new Source(accountId,t.amountCents(),"pending_collection".equals(t.status()),false);}
  var b=bills.get(s,id,lock);return new Source(accountId,b.remainingCents(),!b.closed()&&b.remainingCents()>0,b.walletPaidCents()+b.externalPaidCents()>0);
 }
 private Context context(StoreScope s,String type,UUID id,String channel,Source source){
  if(!source.pending())throw WalletCollectionProofService.conflict("原单已不再待付款，请刷新查看结果");
  if(!"paynow".equals(WalletMoney.channelType(channel))||!port.paynowConfigured(s))throw WalletException.invalid();
  if("topup".equals(type)){if(!topups.get(s,id,false).channel().equals(channel))throw WalletException.invalid();}
  else if(!accounts.requireProgram(s,false).channels().contains(channel))throw WalletException.invalid();
  var settings=port.settings(s);String account="mobile".equals(settings.paynowType())?settings.paynowMobile():settings.paynowUen();
  if(account==null||account.isBlank())throw WalletException.invalid();
  return new Context(type,id,channel,WalletPayNowReference.forSource(id),source.amount(),WalletCollectionProofService.normalizeAccount(account),settings.merchantName());
 }
 public Context context(StoreScope s,CustomerAuthPrincipal p,String type,UUID id,String channel){return context(s,type,id,channel,own(s,p,type,id,false));}
 public Map<String,String> paynow(StoreScope s,CustomerAuthPrincipal p,String type,UUID id,String channel){
  var source=own(s,p,type,id,false);context(s,type,id,channel,source);
  if(!source.partiallyPaid()&&!access.commercial(s).allowed())throw WalletException.forbidden();
  var account=accounts.account(s,source.accountId(),false);accounts.requireActive(account);accounts.verifyIntegrity(account);
  return Map.of("payload",port.paynowQr(s,source.amount(),id));
 }
 @Transactional
 public CustomerEvidence submit(StoreScope s,CustomerAuthPrincipal p,String type,UUID id,String channel,String amount,String key,String filename,String contentType,byte[] image){
  var source=own(s,p,type,id,true);long cents=WalletMoney.cents(amount);
  if(key==null||key.isBlank()||key.length()>160)throw WalletException.invalid();
  String scopedKey="customer:"+WalletProofCapture.digest((p.customerId()+"|"+key).getBytes(StandardCharsets.UTF_8));
  var previous=proofs.byKey(s,scopedKey);
  Context current;
  if(previous.isPresent()){
   var old=previous.get();current=old.evidence().context();
   if(!p.customerId().equals(old.customerId())||!current.sourceType().equals(type)||!current.sourceId().equals(id)||!current.channel().equals(channel))throw WalletCollectionProofService.conflict("上传请求编号与原凭证不一致");
  }else{
   current=context(s,type,id,channel,source);
   if(cents>current.amountCents()||("topup".equals(type)&&cents!=current.amountCents()))throw WalletException.invalid();
  }
  var evidence=capture.capture(s,current,cents,scopedKey,filename,contentType,image,p.authAccountId(),p.customerId());
  if(previous.isEmpty()){
   if("topup".equals(type))topups.declarePaid(s,id);
   commands.audit(s,p.customerId(),"customer.paynow.proof.submit",id,Map.of("proofId",evidence.id(),"sourceType",type));
  }
  return view(s,proofs.get(s,evidence.id(),false),source.pending());
 }
 public List<CustomerEvidence> list(StoreScope s,CustomerAuthPrincipal p,String type,UUID id){var source=own(s,p,type,id,false);return proofs.customerProofs(s,type,id,p.customerId(),false).stream().map(row->view(s,row,source.pending())).toList();}
 public Image image(StoreScope s,CustomerAuthPrincipal p,UUID id){
  access.customer(s,p,false);var stored=proofs.get(s,id,false);
  if(!p.customerId().equals(stored.customerId()))throw WalletException.missing();
  var c=stored.evidence().context();own(s,p,c.sourceType(),c.sourceId(),false);
  return new Image(stored.image(),stored.contentType());
 }
 private CustomerEvidence view(StoreScope s,WalletCollectionProofRepository.Stored stored,boolean pending){var e=stored.evidence();return new CustomerEvidence(e.id(),e.context(),e.bankReference(),e.checks(),stored.collectionId()!=null?"confirmed":pending?"waiting_confirmation":"closed","/api/v1/public/stores/"+s.storeId().value()+"/wallet/collection-proofs/images/"+e.id());}
}
