package com.rpb.reservation.workforce.application;

import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.persistence.WorkforceDirectory;
import static com.rpb.reservation.workforce.application.WorkforceDtos.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class WorkforceHomeService {
    private final WorkforceAccess access;
    private final WorkforceDirectory directory;
    private final WorkforceEmployeeService employees;
    public WorkforceHomeService(WorkforceAccess access,WorkforceDirectory directory,WorkforceEmployeeService employees){this.access=access;this.directory=directory;this.employees=employees;}
    public Home home(CurrentActor actor,UUID store){
        access.enter(actor,store);var managed=access.managedStores(actor);boolean can=managed.contains(store),head=access.isHeadOffice(actor);
        var stores=directory.stores(actor.tenantId()).stream().filter(s->managed.contains(s.id())).toList();
        var companies=can?directory.companies(actor.tenantId()).stream().filter(c->head?access.companyAllowed(actor,c.id()):stores.stream().anyMatch(s->s.id().equals(store)&&c.id().equals(s.companyId()))).toList():List.<Option>of();
        var accounts=can?directory.accounts(actor.tenantId(),false).stream().filter(a->!directory.accountStores(actor.tenantId(),a.id()).isEmpty()&&managed.containsAll(directory.accountStores(actor.tenantId(),a.id()))).toList():List.<Option>of();
        var administratorAccounts=head?directory.accounts(actor.tenantId(),true).stream().filter(a->!a.id().equals(actor.actorId())&&directory.accountStores(actor.tenantId(),a.id()).contains(store)).toList():List.<Option>of();
        var myself=employees.myself(actor);
        var clockStores=myself==null?List.<StoreOption>of():directory.stores(actor.tenantId()).stream().filter(s->myself.storeIds().contains(s.id())&&actor.canAccessStore(s.id())).toList();
        return new Home(head,can,stores,companies,accounts,administratorAccounts,myself,clockStores);
    }
}
