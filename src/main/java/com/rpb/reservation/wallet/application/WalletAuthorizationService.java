package com.rpb.reservation.wallet.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.wallet.domain.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.funding.application.WalletFundingService;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.application.WalletViews.*;
import static com.rpb.reservation.wallet.application.WalletCommands.*;

@Service
@Transactional(readOnly=true)
public class WalletAuthorizationService {
    private final WalletAccountRepository accounts;private final WalletLedgerRepository ledger;private final WalletCommandRepository commands;
    private final WalletBillRepository bills;private final WalletAuthorizationRepository authorizations;private final WalletAccess access;private final Clock clock;
    private final WalletFundingService funding;
    public WalletAuthorizationService(WalletAccountRepository accounts,WalletLedgerRepository ledger,WalletCommandRepository commands,WalletBillRepository bills,WalletAuthorizationRepository authorizations,WalletAccess access,Clock clock,WalletFundingService funding) {
        this.accounts=accounts;this.ledger=ledger;this.commands=commands;this.bills=bills;this.authorizations=authorizations;this.access=access;this.clock=clock;this.funding=funding;
    }
    @Transactional
    public PaymentCode issuePaymentCode(StoreScope scope,CustomerAuthPrincipal principal,UUID accountId) {
        return issuePaymentCode(scope,principal,accountId,null);
    }
    @Transactional
    public PaymentCode issuePaymentCode(StoreScope scope,CustomerAuthPrincipal principal,UUID accountId,String purpose) {
        if(purpose!=null && !"merchant_charge".equals(purpose) && !"topup".equals(purpose))throw WalletException.invalid();
        access.customer(scope,principal,true);accounts.requireProgram(scope,true);var a=accounts.account(scope,accountId,true);access.own(a,principal);accounts.requireActive(a);
        boolean charge="merchant_charge".equals(purpose);
        String token=WalletTokens.issue(charge?"WC":"WP");Instant now=clock.instant(),expires=now.plusSeconds(60);
        authorizations.code(scope,a.id(),WalletTokens.hash(token),now,expires);
        return new PaymentCode(charge?"customer_charge":"customer_wallet",token,expires);
    }
    @Transactional
    public Bill bind(StoreScope scope,CurrentActor actor,UUID billId,String code) {
        access.staff(scope,actor,"wallet.charge.create",true);accounts.requireProgram(scope,true);var b=bills.get(scope,billId,true);
        if(b.walletApplied() || b.closed() || b.remainingCents()==0)throw WalletException.conflict("账单不能再次绑定");
        authorizations.bind(scope,b.id(),b.accountId(),WalletTokens.required(code,"WP"),clock.instant());
        commands.audit(scope,actor.actorId(),"bill.bind_code",b.id(),Map.of());return b;
    }
    public Account resolve(StoreScope scope,CurrentActor actor,String code) {
        return resolve(scope,actor,code,null);
    }
    public Account resolve(StoreScope scope,CurrentActor actor,String code,String purpose) {
        if(purpose!=null && !"topup".equals(purpose))throw WalletException.invalid();
        access.staff(scope,actor,"topup".equals(purpose)?"wallet.topup.create":"wallet.charge.create",true);accounts.requireProgram(scope,true);
        var account=accounts.account(scope,authorizations.resolve(scope,WalletTokens.customerCodeHash(code),clock.instant()),false);accounts.requireActive(account);return account;
    }
    @Transactional
    public PaymentCode issueBillCode(StoreScope scope,CurrentActor actor,UUID id) {
        access.staff(scope,actor,"wallet.charge.create",true);accounts.requireProgram(scope,true);var bill=bills.get(scope,id,true);
        if(bill.closed() || bill.walletApplied() || bill.remainingCents()==0)throw WalletException.conflict("账单不能签发付款码");
        String token=WalletTokens.issue("WB");Instant expires=clock.instant().plusSeconds(600);bills.issueCode(scope,id,WalletTokens.hash(token),expires);
        return new PaymentCode("merchant_bill",token,expires);
    }
    public Bill scanBill(StoreScope scope,CustomerAuthPrincipal principal,String token) {
        access.customer(scope,principal,true);accounts.requireProgram(scope,true);var bill=bills.byCode(scope,WalletTokens.required(token,"WB"),clock.instant());
        access.own(accounts.account(scope,bill.accountId(),false),principal);return bill;
    }
    @Transactional
    public Quote quote(StoreScope scope,CustomerAuthPrincipal principal,UUID id) {
        access.customer(scope,principal,true);accounts.requireProgram(scope,true);var b=bills.get(scope,id,false);var a=accounts.account(scope,b.accountId(),true);access.own(a,principal);accounts.requireActive(a);
        if(b.closed() || b.walletApplied() || b.remainingCents()==0)throw WalletException.conflict("账单已处理，请查看支付结果");
        bills.requirePersonalFullPayment(scope,b,a);
        var split=funding.preview(scope,a,WalletMoney.debit(a.availableCents(),b.remainingCents()));
        return authorizations.quote(scope,b,a,split.totalCents(),principal.customerId(),clock.instant().plusSeconds(60),split.principalCents(),split.bonusCents());
    }
    @Transactional
    public Paid pay(StoreScope scope,CustomerAuthPrincipal principal,UUID id,Pay c) {
        access.customer(scope,principal,true);accounts.requireProgram(scope,true);if(c==null || c.quoteId()==null)throw WalletException.invalid();
        return commands.run(scope,principal.customerId(),"bill.wallet_pay",c.idempotencyKey(),Map.of("id",id,"payment",c),Paid.class,()->{
            var initial=bills.get(scope,id,false);var a=accounts.account(scope,initial.accountId(),true);access.own(a,principal);accounts.requireActive(a);
            var b=bills.get(scope,id,true);var authorization=authorizations.authorization(scope,c.quoteId(),id,principal.customerId());var q=authorization.quote();
            if(authorization.consumed() || !q.expiresAt().isAfter(clock.instant()))throw new WalletException("WALLET_AUTHORIZATION_EXPIRED",409,"付款确认已使用或过期");
            if(b.closed() || b.walletApplied() || q.accountVersion()!=a.version() || q.billVersion()!=b.version() || q.amountCents()!=WalletMoney.debit(a.availableCents(),b.remainingCents()))
                throw new WalletException("WALLET_QUOTE_STALE",409,"余额或账单已变化，请重新查看并确认");
            bills.requirePersonalFullPayment(scope,b,a);
            var split=funding.debit(scope,a,q.amountCents(),id,principal.customerId());
            bills.walletApplied(scope,b,q.amountCents());funding.billComponents(scope,id,split);authorizations.consume(scope,q.id(),clock.instant());
            commands.audit(scope,principal.customerId(),"bill.wallet_pay",id,Map.of("amountCents",q.amountCents()));
            return new Paid(bills.get(scope,id,false),accounts.account(scope,a.id(),false));
        });
    }
}
