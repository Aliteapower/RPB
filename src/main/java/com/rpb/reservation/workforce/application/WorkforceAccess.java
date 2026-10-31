package com.rpb.reservation.workforce.application;

import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.persistence.WorkforceDirectory;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class WorkforceAccess {
    private final AppGateService gate;
    private final WorkforceDirectory directory;
    public WorkforceAccess(AppGateService gate,WorkforceDirectory directory){this.gate=gate;this.directory=directory;}
    /** Acquire before employee/calendar locks; payroll capture takes the matching exclusive lock. */
    public void sourceWrite(CurrentActor actor){if(actor==null||actor.tenantId()==null)throw WorkforceException.forbidden();directory.lockSourcesShared(actor.tenantId());}
    public void enter(CurrentActor actor,UUID store){
        if(actor==null||actor.actorId()==null||actor.tenantId()==null||actor.roles().contains("platform_admin")||!actor.canAccessStore(store))throw WorkforceException.forbidden();
        var decision=gate.evaluate(new AppGateAccessRequest("workforce",actor.tenantId(),store,"workforce.view",actor));
        if(!decision.allowed())throw new WorkforceException(decision.denyReason().name(),403);
    }
    public boolean isHeadOffice(CurrentActor actor){
        return actor!=null&&actor.roles().contains("tenant_admin")&&!actor.roles().contains("store_manager")
            &&!actor.roles().contains("platform_admin")&&actor.hasPermission("tenant.admin.manage")&&actor.hasPermission("workforce.admin.manage");
    }
    public void headOffice(CurrentActor actor){if(!isHeadOffice(actor))throw WorkforceException.forbidden();}
    public Set<UUID> managedStores(CurrentActor actor){
        var stores=new HashSet<>(isHeadOffice(actor)?actor.storeIds():directory.managerStoreIds(actor.tenantId(),actor.actorId()));
        stores.retainAll(actor.storeIds());return Set.copyOf(stores);
    }
    public void manage(CurrentActor actor,UUID store){if(!managedStores(actor).contains(store))throw WorkforceException.forbidden();}
    public boolean companyAllowed(CurrentActor actor,UUID company){
        if(!isHeadOffice(actor))return false;
        var stores=directory.stores(actor.tenantId()).stream().filter(s->company.equals(s.companyId())).toList();
        return !stores.isEmpty()&&stores.stream().allMatch(s->actor.canAccessStore(s.id()));
    }
    public void company(CurrentActor actor,UUID company){if(company==null||!companyAllowed(actor,company))throw WorkforceException.forbidden();}
}
