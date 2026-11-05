package com.rpb.reservation.workforce.application;

import com.rpb.reservation.auth.application.WorkforceAccountProvisioner;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.persistence.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import static com.rpb.reservation.workforce.application.WorkforceDtos.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class WorkforceAdminService {
    private final WorkforceAccess access;
    private final WorkforceDirectory directory;
    private final WorkforceAdminRepository repository;
    private final WorkforceAccountProvisioner accounts;
    private final WorkforceAudit audit;
    public WorkforceAdminService(WorkforceAccess access,WorkforceDirectory directory,WorkforceAdminRepository repository,WorkforceAccountProvisioner accounts,WorkforceAudit audit){
        this.access=access;this.directory=directory;this.repository=repository;this.accounts=accounts;this.audit=audit;
    }
    public List<Administrator> list(CurrentActor actor,UUID store){access.enter(actor,store);access.headOffice(actor);return repository.list(actor.tenantId(),store);}
    @Transactional
    public Administrator save(CurrentActor actor,UUID store,UUID id,AdministratorWrite c){
        access.sourceWrite(actor);
        access.enter(actor,store);access.headOffice(actor);
        if(c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.employeeManage()==null)throw WorkforceException.invalid();
        if(id.equals(actor.actorId()))throw WorkforceException.forbidden();
        repository.lock(actor.tenantId(),id);
        var target=directory.accounts(actor.tenantId(),true).stream().filter(a->a.id().equals(id)).findFirst().orElseThrow(WorkforceException::missing);
        if(!directory.accountStores(actor.tenantId(),id).contains(store))throw WorkforceException.forbidden();
        if(c.payrollCompanyId()!=null)access.company(actor,c.payrollCompanyId());
        var old=repository.list(actor.tenantId(),store).stream().filter(a->a.accountId().equals(id)).findFirst().orElse(null);
        long version=old==null?0:old.version();if(version!=c.expectedVersion())throw WorkforceException.conflict();
        repository.save(actor.tenantId(),store,id,c,version+1);accounts.allowEntry(actor.tenantId(),id);
        var saved=new Administrator(id,target.name(),c.employeeManage(),c.payrollCompanyId(),version+1);
        audit.changed(actor,store,"administrator.save",id,old,saved);return saved;
    }
}
