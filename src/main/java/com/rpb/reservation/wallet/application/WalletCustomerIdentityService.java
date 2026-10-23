package com.rpb.reservation.wallet.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.domain.WalletTokens;
import com.rpb.reservation.wallet.persistence.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.application.WalletViews.*;

/** Reusable customer identification, deliberately separate from payment authorization. */
@Service
@Transactional(readOnly=true)
public class WalletCustomerIdentityService {
 private final WalletAccountRepository accounts;private final WalletAuthorizationRepository legacy;private final WalletCustomerIdentityRepository codes;
 private final WalletAccess access;private final WalletDirectory directory;private final Clock clock;
 public WalletCustomerIdentityService(WalletAccountRepository accounts,WalletAuthorizationRepository legacy,WalletCustomerIdentityRepository codes,WalletAccess access,WalletDirectory directory,Clock clock){this.accounts=accounts;this.legacy=legacy;this.codes=codes;this.access=access;this.directory=directory;this.clock=clock;}
 @Transactional public PaymentCode issue(StoreScope scope,CustomerAuthPrincipal principal){
  access.customer(scope,principal,true);var program=accounts.requireProgram(scope,true);
  String token=WalletTokens.issue("WI");var now=clock.instant();var expiry=now.plusSeconds(WalletTokens.PERSONAL_CODE_LIFETIME_SECONDS);
  codes.issue(scope,program.id(),principal.customerId(),WalletTokens.hash(token),now,expiry);
  return new PaymentCode("customer_identity",token,expiry);
 }
 public Customer resolve(StoreScope scope,CurrentActor actor,String token){
  access.staff(scope,actor,actor!=null&&actor.hasPermission("wallet.topup.create")?"wallet.topup.create":"wallet.points.view",true);
  var program=accounts.requireProgram(scope,true);UUID customer;
  if(token!=null&&token.startsWith("WP.")){
   var account=accounts.account(scope,legacy.resolve(scope,WalletTokens.required(token,"WP"),clock.instant()),false);accounts.requireActive(account);customer=account.customerId();
  }else customer=codes.resolve(scope,program.id(),WalletTokens.required(token,"WI"),clock.instant());
  return directory.customer(scope.tenantScope(),customer);
 }
}
