package com.rpb.reservation.wallet.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.wallet.domain.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.wallet.funding.application.WalletFundingService;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.application.WalletCommands.*;
import static com.rpb.reservation.wallet.application.WalletViews.*;

/** Cashier confirmation of an explicitly authorized WC code. Identity-only WP never grants a debit. */
@Service
@Transactional(readOnly=true)
public class WalletScanChargeService {
    private final WalletAccountRepository accounts;
    private final WalletLedgerRepository ledger;
    private final WalletCommandRepository commands;
    private final WalletBillRepository bills;
    private final WalletAuthorizationRepository authorizations;
    private final WalletAccess access;
    private final WalletDirectory directory;
    private final Clock clock;private final WalletFundingService funding;
    public WalletScanChargeService(WalletAccountRepository accounts,WalletLedgerRepository ledger,WalletCommandRepository commands,
                                   WalletBillRepository bills,WalletAuthorizationRepository authorizations,WalletAccess access,WalletDirectory directory,Clock clock,WalletFundingService funding) {
        this.accounts=accounts;this.ledger=ledger;this.commands=commands;this.bills=bills;
        this.authorizations=authorizations;this.access=access;this.directory=directory;this.clock=clock;this.funding=funding;
    }

    /** Old clients must move to customer quote/pay or refresh the unified personal code. */
    public Paid legacyChargeUnavailable(StoreScope scope,CurrentActor actor) {
        access.staff(scope,actor,"wallet.charge.create",true);
        access.staff(scope,actor,"wallet.collection.confirm",true);
        throw new WalletException("WALLET_PERSONAL_PAYMENT_CONFIRMATION_REQUIRED",409,"请改用顾客逐笔确认付款，或刷新页面使用统一个人钱包码");
    }
    @Transactional
    public Paid charge(StoreScope scope,CurrentActor actor,ScanCharge c) {
        access.staff(scope,actor,"wallet.charge.create",true);
        access.staff(scope,actor,"wallet.collection.confirm",true);
        if(c==null || c.expectedAccountVersion()==null || c.expectedAccountVersion()<0)throw WalletException.invalid();
        long amount=WalletMoney.cents(c.amount());
        String hash=WalletTokens.required(c.customerCode(),"WC");
        return commands.run(scope,actor.actorId(),"bill.scan_charge",c.idempotencyKey(),c,Paid.class,()->{
            accounts.requireProgram(scope,true);
            var a=accounts.account(scope,c.accountId(),true);accounts.requireActive(a);
            directory.customer(scope.tenantScope(),a.customerId());
            if(a.version()!=c.expectedAccountVersion())throw stale();
            Bill b=null;
            if(c.billId()!=null) {
                b=bills.get(scope,c.billId(),true);
                if(!b.accountId().equals(a.id()))throw WalletException.missing();
                if(c.expectedBillVersion()==null || b.version()!=c.expectedBillVersion() || b.amountCents()!=amount)throw stale();
                if(b.closed() || b.walletApplied() || b.remainingCents()==0)throw WalletException.conflict("账单已处理，请查看支付结果");
            } else if(c.expectedBillVersion()!=null)throw WalletException.invalid();
            // Same lock order as regeneration and customer payment. Check time AFTER the final lock.
            var expires=authorizations.lockUnboundCode(scope,a.id(),hash);
            if(!expires.isAfter(clock.instant()))throw new WalletException("WALLET_AUTHORIZATION_EXPIRED",409,"付款码已过期，请顾客重新出示");
            if(b==null)b=bills.create(scope,a,amount,actor.actorId());
            authorizations.bind(scope,b.id(),a.id(),hash,clock.instant());
            long debit=WalletMoney.debit(a.availableCents(),b.remainingCents());
            var split=funding.debit(scope,a,debit,b.id(),actor.actorId());
            bills.walletApplied(scope,b,debit);funding.billComponents(scope,b.id(),split);
            commands.audit(scope,actor.actorId(),"bill.scan_charge",b.id(),Map.of("accountId",a.id(),"amountCents",debit,"remainingCents",b.remainingCents()-debit));
            return new Paid(bills.get(scope,b.id(),false),accounts.account(scope,a.id(),false));
        });
    }

    public PaymentCodeStatus status(StoreScope scope,CustomerAuthPrincipal principal,String code) {
        access.customer(scope,principal,false);
        var ref=authorizations.reference(scope,WalletTokens.required(code,"WC"));
        access.own(accounts.account(scope,ref.accountId(),false),principal);
        return new PaymentCodeStatus(ref.billId()==null?null:bills.get(new StoreScope(scope.tenantId(),ref.storeId()),ref.billId(),false));
    }
    private WalletException stale() {return new WalletException("WALLET_QUOTE_STALE",409,"余额或账单已变化，请重新核对并确认");}
}
