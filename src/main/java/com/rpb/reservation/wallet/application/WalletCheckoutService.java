package com.rpb.reservation.wallet.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.wallet.domain.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.application.WalletViews.*;
import static com.rpb.reservation.wallet.application.WalletCommands.*;

@Service
@Transactional(readOnly=true)
public class WalletCheckoutService {
    private final WalletAccountRepository accounts;private final WalletCommandRepository commands;private final WalletBillRepository bills;
    private final WalletCollectionService collections;private final WalletAccess access;
    public WalletCheckoutService(WalletAccountRepository accounts,WalletLedgerRepository ledger,WalletCommandRepository commands,WalletBillRepository bills,WalletCollectionService collections,WalletAccess access) {
        this.accounts=accounts;this.commands=commands;this.bills=bills;this.collections=collections;this.access=access;
    }
    @Transactional
    public Bill create(StoreScope scope,CurrentActor actor,BillCreate c) {
        access.staff(scope,actor,"wallet.charge.create",true);if(c==null)throw WalletException.invalid();long amount=WalletMoney.cents(c.amount());
        return commands.run(scope,actor.actorId(),"bill.create",c.idempotencyKey(),c,Bill.class,()->{
            accounts.requireProgram(scope,true);var a=accounts.account(scope,c.accountId(),true);accounts.requireActive(a);
            var bill=bills.create(scope,a,amount,actor.actorId());commands.audit(scope,actor.actorId(),"bill.create",bill.id(),bill);return bill;
        });
    }
    @Transactional
    public Bill collect(StoreScope scope,CurrentActor actor,UUID id,CollectionConfirm c) {
        access.staff(scope,actor,"wallet.collection.confirm",false);if(c==null)throw WalletException.invalid();
        return commands.run(scope,actor.actorId(),"bill.collect",c.idempotencyKey(),Map.of("id",id,"confirmation",c),Bill.class,()->{
            var initial=bills.get(scope,id,false);accounts.account(scope,initial.accountId(),true);var b=bills.get(scope,id,true);
            if(b.closed() || b.remainingCents()==0)throw WalletException.conflict("账单已关闭或结清");
            long amount=WalletMoney.cents(c.amount());if(amount>b.remainingCents())throw WalletException.conflict("实收超过剩余应付，请登记异常退回");
            // Expired-store recovery only settles an existing partially-paid bill, never a new charge.
            if(!access.commercial(scope).allowed() && b.walletPaidCents()+b.externalPaidCents()==0)throw WalletException.forbidden();
            var p=accounts.requireProgram(scope,false);collections.confirm(scope,null,b.id(),amount,c,actor.actorId(),p.channels());
            bills.externalPaid(scope,b,amount);commands.audit(scope,actor.actorId(),"bill.collect",id,Map.of("channel",c.channel(),"amountCents",amount));return bills.get(scope,id,false);
        });
    }
    public List<Bill> list(StoreScope scope,CurrentActor actor) {access.staff(scope,actor,"wallet.view",false);return bills.list(scope);}
    public Bill get(StoreScope scope,CurrentActor actor,UUID id) {access.staff(scope,actor,"wallet.view",false);return bills.get(scope,id,false);}
    public List<Bill> pending(StoreScope scope,CustomerAuthPrincipal p) {access.customer(scope,p,false);return bills.customerPending(scope,p.customerId());}
    public Bill ownBill(StoreScope scope,CustomerAuthPrincipal p,UUID id) {access.customer(scope,p,false);var b=bills.get(scope,id,false);access.own(accounts.account(scope,b.accountId(),false),p);return b;}
    public String paynowQr(StoreScope scope,CurrentActor a,UUID id,WalletCollectionPort port) {
        var b=get(scope,a,id);if(b.closed() || b.remainingCents()<=0)throw WalletException.conflict("账单已关闭或结清");
        if(!access.commercial(scope).allowed() && b.walletPaidCents()+b.externalPaidCents()==0)throw WalletException.forbidden();
        return port.paynowQr(scope,b.remainingCents(),id);
    }
    @Transactional
    public Bill cancel(StoreScope scope,CurrentActor actor,UUID id,String key) {
        access.staff(scope,actor,"wallet.charge.create",false);
        return commands.run(scope,actor.actorId(),"bill.cancel",key,id,Bill.class,()->{
            var b=bills.get(scope,id,true);if(b.walletPaidCents()+b.externalPaidCents()>0)throw WalletException.conflict("已收款账单请通过原单退款关闭");
            bills.close(scope,id);commands.audit(scope,actor.actorId(),"bill.cancel",id,Map.of());return bills.get(scope,id,false);
        });
    }
}
