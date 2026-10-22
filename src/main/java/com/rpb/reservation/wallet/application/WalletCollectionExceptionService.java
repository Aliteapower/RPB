package com.rpb.reservation.wallet.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.*;
import com.rpb.reservation.wallet.persistence.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.application.WalletCommands.*;
import com.rpb.reservation.wallet.application.WalletViews.Collection;

/** Records real but unapplied receipts. No exception path ever credits balance or settles a bill. */
@Service
@Transactional(readOnly=true)
public class WalletCollectionExceptionService {
    private final WalletAccountRepository accounts;private final WalletTopupRepository topups;private final WalletBillRepository bills;
    private final WalletCollectionService collections;private final WalletCollectionRepository repository;private final WalletCommandRepository commands;private final WalletAccess access;
    public WalletCollectionExceptionService(WalletAccountRepository accounts,WalletTopupRepository topups,WalletBillRepository bills,WalletCollectionService collections,WalletCollectionRepository repository,WalletCommandRepository commands,WalletAccess access) {
        this.accounts=accounts;this.topups=topups;this.bills=bills;this.collections=collections;this.repository=repository;this.commands=commands;this.access=access;
    }
    @Transactional
    public Collection record(StoreScope scope,CurrentActor actor,CollectionException c) {
        access.staff(scope,actor,"wallet.refund.manage",false);
        if(c==null || c.sourceId()==null || c.confirmation()==null || !Set.of("top_up","bill").contains(Objects.toString(c.sourceType(),"")))throw WalletException.invalid();
        String reason=WalletMoney.text(c.reason());long amount=WalletMoney.cents(c.confirmation().amount());
        return commands.run(scope,actor.actorId(),"collection.exception",c.idempotencyKey(),c,Collection.class,()->{
            boolean topup="top_up".equals(c.sourceType());
            var initial=topup?topups.get(scope,c.sourceId(),false).accountId():bills.get(scope,c.sourceId(),false).accountId();
            // Serialize with confirmation and refund, but retain factual receipts even if the account is frozen.
            accounts.lockForExternalRecovery(scope,initial);
            if(topup)topups.get(scope,c.sourceId(),true);else bills.get(scope,c.sourceId(),true);
            var collected=collections.confirm(scope,topup?c.sourceId():null,topup?null:c.sourceId(),amount,c.confirmation(),actor.actorId(),List.of("cash","paynow","credit_card"),true);
            var result=repository.markException(scope,collected.id(),reason);
            if(topup && !"credited".equals(topups.get(scope,c.sourceId(),false).status()))topups.status(scope,c.sourceId(),"exception");
            commands.audit(scope,actor.actorId(),"collection.exception",result.id(),Map.of("reason",reason,"amountCents",amount));return result;
        });
    }
    public List<Collection> list(StoreScope scope,CurrentActor actor) {access.staff(scope,actor,"wallet.refund.manage",false);return repository.exceptions(scope);}
}
