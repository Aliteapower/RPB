package com.rpb.reservation.wallet.integration;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.cards.purchase.CardPaymentPort;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.application.WalletCollectionPort;
import java.util.*;
import org.springframework.stereotype.Component;
/** Offline collection validation only. Never credits a stored-value account. */
@Component
public class WalletCardPaymentAdapter implements CardPaymentPort {
 private final com.rpb.reservation.wallet.persistence.WalletAccountRepository accounts;private final WalletCollectionPort collection;
 public WalletCardPaymentAdapter(WalletCollectionPort collection,com.rpb.reservation.wallet.persistence.WalletAccountRepository accounts){this.collection=collection;this.accounts=accounts;}
 public void claim(StoreScope s,java.util.UUID id,PaymentReceipt p){collection.claimReceipt(s,id,"cards",p.channel(),p.account(),p.receipt());}
 public PaymentReceipt recovery(StoreScope s,PaymentConfirm c){if(c==null||!c.verified()||c.receipt()==null||c.receipt().isBlank()||c.receipt().length()>100||c.channel()==null||c.terminal()==null||c.terminal().isBlank())throw CardException.invalid();com.rpb.reservation.wallet.domain.WalletMoney.channel(c.channel());return new PaymentReceipt(c.channel(),collection.canonicalRecoveryAccount(s,c.channel(),c.account(),c.terminal()),c.receipt().trim().toUpperCase(Locale.ROOT),c.amountCents());}
 public PaymentReceipt confirm(StoreScope s,PaymentConfirm c){
  if(!c.verified()||c.channel()==null||c.receipt()==null||c.receipt().isBlank()||c.receipt().length()>100||c.terminal()==null||c.terminal().isBlank())throw CardException.invalid();
  String channel=com.rpb.reservation.wallet.domain.WalletMoney.channel(c.channel());if(!accounts.requireProgram(s,true).channels().contains(channel))throw new CardException("CARD_CHANNEL_DISABLED",409,"该收款科目未启用");String account=collection.canonicalAccount(s,channel,c.account(),c.terminal());return new PaymentReceipt(c.channel(),account,c.receipt().trim().toUpperCase(Locale.ROOT),c.amountCents());
 }
}
