package com.rpb.reservation.wallet.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.WalletException;
import com.rpb.reservation.wallet.persistence.WalletCommandRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.appgate.application.AppGateService;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.wallet.application.WalletViews.*;
import static com.rpb.reservation.wallet.application.WalletCommands.*;

@Service
@Transactional(readOnly=true)
public class WalletCollectionSettingsService {
    private final WalletAccess access;private final WalletCollectionPort collection;private final WalletCommandRepository commands;private final AppGateService gate;
    public WalletCollectionSettingsService(WalletAccess access,WalletCollectionPort collection,WalletCommandRepository commands,AppGateService gate) {this.access=access;this.collection=collection;this.commands=commands;this.gate=gate;}
    public CollectionSettings get(StoreScope s,CurrentActor a) {access.staff(s,a,"wallet.settings.manage",false);return collection.settings(s);}
    @Transactional
    public CollectionSettings save(StoreScope s,CurrentActor a,CollectionSettingsUpdate c) {
        access.staff(s,a,"wallet.settings.manage",true);
        if(c==null || ("active".equals(c.status()) && !c.issuerVerified()))throw WalletException.invalid();
        if(gate.evaluateCommercial("payment",s.tenantId().value(),s.storeId().value()).allowed() && !a.hasPermission("payment.settings.manage"))throw WalletException.forbidden();
        return commands.run(s,a.actorId(),"collection.settings",c.idempotencyKey(),c,CollectionSettings.class,()->{
            var result=collection.configure(s,c);commands.audit(s,a.actorId(),"collection.settings",s.storeId().value(),Map.of("status",result.status(),"issuerVerified",c.issuerVerified(),"version",result.version()));return result;
        });
    }
}
