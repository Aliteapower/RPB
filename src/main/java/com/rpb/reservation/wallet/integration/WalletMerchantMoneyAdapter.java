package com.rpb.reservation.wallet.integration;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.wallet.application.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.domain.WalletException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
/** Converts an explicitly confirmed fixed amount into the existing bill/quote/pay workflow. */
@Component
public class WalletMerchantMoneyAdapter {
 private final WalletAccountRepository accounts;private final WalletBillRepository bills;private final WalletCommandRepository commands;private final WalletAuthorizationService authorization;private final WalletAccess access;
 public WalletMerchantMoneyAdapter(WalletAccountRepository accounts,WalletBillRepository bills,WalletCommandRepository commands,WalletAuthorizationService authorization,WalletAccess access){this.accounts=accounts;this.bills=bills;this.commands=commands;this.authorization=authorization;this.access=access;}
 @Transactional public UUID pay(StoreScope s,CustomerAuthPrincipal p,UUID creator,UUID order,long amount,int version){
  access.customer(s,p,true);accounts.requireProgram(s,true);
  var selected=accounts.customerAccounts(s,p.customerId()).stream().findFirst().orElseThrow(()->new WalletException("WALLET_ACCOUNT_REQUIRED",409,"尚无本店钱包，请先充值"));
  var a=accounts.account(s,selected.id(),true);access.own(a,p);accounts.requireActive(a);
  if(a.version()!=version)throw new WalletException("WALLET_QUOTE_STALE",409,"余额已变化，请重新核对后确认");
  if(a.availableCents()<amount)throw new WalletException("WALLET_INSUFFICIENT_FUNDS",409,"钱包余额不足，未扣款，请先充值");
  var bill=bills.create(s,a,amount,creator);commands.audit(s,creator,"bill.create",bill.id(),bill);
  var quote=authorization.quote(s,p,bill.id());
  return authorization.pay(s,p,bill.id(),new WalletCommands.Pay(quote.id(),"merchant-order:"+order)).bill().id();
 }
}
