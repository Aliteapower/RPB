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
public class WalletAccountService {
    private final WalletAccountRepository accounts; private final WalletLedgerRepository ledger;
    private final WalletCommandRepository commands; private final WalletAccess access;
    private final WalletDirectory directory; private final WalletCollectionPort collection;
    public WalletAccountService(WalletAccountRepository accounts,WalletLedgerRepository ledger,WalletCommandRepository commands,WalletAccess access,WalletDirectory directory,WalletCollectionPort collection) {
        this.accounts=accounts;this.ledger=ledger;this.commands=commands;this.access=access;this.directory=directory;this.collection=collection;
    }
    public Context context(StoreScope scope,CurrentActor actor) {access.staff(scope,actor,"wallet.view",false);return context(scope);}
    public Context customerContext(StoreScope scope,CustomerAuthPrincipal principal) {access.customer(scope,principal,false);return context(scope);}
    private Context context(StoreScope scope) {
        var d=access.commercial(scope);var program=accounts.program(scope).orElse(null);var identity=directory.store(scope);
        return new Context(identity,program,d.allowed() && program!=null && "active".equals(program.status()),d.allowed()?null:d.denyReason().name(),collection.paynowConfigured(scope));
    }
    @Transactional
    public Program configure(StoreScope scope,CurrentActor actor,ProgramSettings settings) {
        access.staff(scope,actor,"wallet.settings.manage",true);
        if(settings==null)throw WalletException.invalid();
        return commands.run(scope,actor.actorId(),"program.configure",settings.idempotencyKey(),settings,Program.class,()->{
            var identity=directory.store(scope);if(identity.operatingEntityId()==null)throw WalletException.conflict("请先在统一资料设置经营主体");
            var p=accounts.configure(scope,identity.operatingEntityId(),settings.channels(),settings.version());
            commands.audit(scope,actor.actorId(),"program.configure",p.id(),p);return p;
        });
    }
    @Transactional
    public Account open(StoreScope scope,CurrentActor actor,UUID customer,String key) {
        access.staff(scope,actor,"wallet.topup.create",true);directory.customer(scope.tenantScope(),customer);
        return commands.run(scope,actor.actorId(),"account.open",key,customer,Account.class,()->{
            var a=accounts.open(scope,accounts.requireProgram(scope,true),customer);commands.audit(scope,actor.actorId(),"account.open",a.id(),Map.of("customerId",customer));return a;
        });
    }
    public Account get(StoreScope scope,CurrentActor actor,UUID id) {access.staff(scope,actor,"wallet.view",false);return accounts.account(scope,id,false);}
    public List<Customer> search(StoreScope scope,CurrentActor actor,String query) {access.staff(scope,actor,"wallet.view",false);return directory.search(scope.tenantScope(),query);}
    public Customer customer(StoreScope scope,CurrentActor actor,UUID id) {access.staff(scope,actor,"wallet.view",false);return directory.customer(scope.tenantScope(),id);}
    public List<Account> forCustomer(StoreScope scope,CurrentActor actor,UUID id) {customer(scope,actor,id);return accounts.customerAccounts(scope,id);}
    public List<Entry> entries(StoreScope scope,CurrentActor actor,UUID id,int offset) {get(scope,actor,id);return ledger.entries(scope,id,offset);}
    public List<Account> ownAccounts(StoreScope scope,CustomerAuthPrincipal principal) {access.customer(scope,principal,false);return accounts.customerAccounts(scope,principal.customerId());}
    public List<Entry> ownEntries(StoreScope scope,CustomerAuthPrincipal principal,UUID id,int offset) {
        access.customer(scope,principal,false);access.own(accounts.account(scope,id,false),principal);return ledger.entries(scope,id,offset);
    }
    public com.rpb.reservation.queuedisplay.application.CallScreenMediaContent logo(StoreScope s,CurrentActor a,UUID asset) {access.staff(s,a,"wallet.view",false);return directory.logo(s,asset);}
    public com.rpb.reservation.queuedisplay.application.CallScreenMediaContent ownLogo(StoreScope s,CustomerAuthPrincipal p,UUID asset) {access.customer(s,p,false);return directory.logo(s,asset);}
}
