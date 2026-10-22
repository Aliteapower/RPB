package com.rpb.reservation.wallet.application;

import com.rpb.reservation.common.scope.StoreScope;
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
public class WalletTopupService {
    private final WalletAccountRepository accounts;private final WalletLedgerRepository ledger;private final WalletCommandRepository commands;
    private final WalletTopupRepository topups;private final WalletCollectionService collections;private final WalletAccess access;
    public WalletTopupService(WalletAccountRepository accounts,WalletLedgerRepository ledger,WalletCommandRepository commands,WalletTopupRepository topups,WalletCollectionService collections,WalletAccess access) {
        this.accounts=accounts;this.ledger=ledger;this.commands=commands;this.topups=topups;this.collections=collections;this.access=access;
    }
    @Transactional
    public Topup create(StoreScope scope,CurrentActor actor,TopupCreate c) {
        access.staff(scope,actor,"wallet.topup.create",true);if(c==null)throw WalletException.invalid();
        long amount=WalletMoney.cents(c.amount());String channel=WalletMoney.channel(c.channel());
        return commands.run(scope,actor.actorId(),"topup.create",c.idempotencyKey(),c,Topup.class,()->{
            var p=accounts.requireProgram(scope,true);if(!p.channels().contains(channel))throw WalletException.invalid();
            if("paynow".equals(channel) && !collections.paynowConfigured(scope))throw WalletException.conflict("门店未配置有效 PayNow 收款资料");
            var a=accounts.account(scope,c.accountId(),true);accounts.requireActive(a);
            var t=topups.create(scope,a,amount,channel,actor.actorId());commands.audit(scope,actor.actorId(),"topup.create",t.id(),t);return t;
        });
    }
    @Transactional
    public TopupResult confirm(StoreScope scope,CurrentActor actor,UUID id,CollectionConfirm c) {
        // Existing-order recovery is allowed after subscription expiry; identity, scope and confirm permission still apply.
        access.staff(scope,actor,"wallet.collection.confirm",false);if(c==null)throw WalletException.invalid();
        return commands.run(scope,actor.actorId(),"topup.confirm",c.idempotencyKey(),Map.of("id",id,"confirmation",c),TopupResult.class,()->{
            var initial=topups.get(scope,id,false);var a=accounts.account(scope,initial.accountId(),true);accounts.requireActive(a);
            var t=topups.get(scope,id,true);
            if(!c.verified() || !t.channel().equals(c.channel()) || WalletMoney.cents(c.amount())!=t.amountCents())throw WalletException.invalid();
            if("credited".equals(t.status())) return new TopupResult(t,a);
            if(!"pending_collection".equals(t.status()) && !"verified_pending_credit".equals(t.status()))throw WalletException.conflict("充值单不能确认，请核查实收后处理异常退款");
            var p=accounts.requireProgram(scope,false);if(!"active".equals(p.status()))throw WalletException.conflict("钱包计划已冻结，请先处理资金异常");
            collections.confirm(scope,t.id(),null,t.amountCents(),c,actor.actorId(),List.of(t.channel()));
            long after=Math.addExact(a.balanceCents(),t.amountCents());accounts.update(a,after,a.reservedCents());ledger.append(scope,a,after,"topup_credit",t.id(),actor.actorId(),null);
            topups.status(scope,t.id(),"credited");commands.audit(scope,actor.actorId(),"topup.confirm",t.id(),Map.of("channel",t.channel(),"amountCents",t.amountCents(),"verification","offline_staff"));
            return new TopupResult(topups.get(scope,id,false),accounts.account(scope,a.id(),false));
        });
    }
    public List<Topup> list(StoreScope scope,CurrentActor actor) {access.staff(scope,actor,"wallet.view",false);return topups.list(scope);}
    public List<Topup> list(StoreScope scope,CurrentActor actor,WalletHistoryQuery query) {access.staff(scope,actor,"wallet.view",false);return topups.list(scope,query);}
    public Topup get(StoreScope scope,CurrentActor actor,UUID id) {access.staff(scope,actor,"wallet.view",false);return topups.get(scope,id,false);}
    @Transactional
    public Topup cancel(StoreScope scope,CurrentActor actor,UUID id,String key) {
        access.staff(scope,actor,"wallet.topup.create",false);
        return commands.run(scope,actor.actorId(),"topup.cancel",key,id,Topup.class,()->{
            var t=topups.get(scope,id,true);if(!"pending_collection".equals(t.status()))throw WalletException.conflict("已收款充值不能取消");
            topups.status(scope,id,"cancelled");commands.audit(scope,actor.actorId(),"topup.cancel",id,Map.of());return topups.get(scope,id,false);
        });
    }
    public String paynowQr(StoreScope scope,CurrentActor actor,UUID id,WalletCollectionPort port) {
        access.staff(scope,actor,"wallet.view",true);var t=topups.get(scope,id,false);
        if(!"paynow".equals(t.channel()) || !"pending_collection".equals(t.status()))throw WalletException.conflict("充值单当前不能展示收款码");
        var a=accounts.account(scope,t.accountId(),false);accounts.requireActive(a);accounts.verifyIntegrity(a);
        return port.paynowQr(scope,t.amountCents(),t.id());
    }
}
