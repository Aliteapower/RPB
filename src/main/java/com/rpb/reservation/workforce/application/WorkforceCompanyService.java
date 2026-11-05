package com.rpb.reservation.workforce.application;

import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.persistence.WorkforceCompanyRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import static com.rpb.reservation.workforce.application.WorkforceDtos.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class WorkforceCompanyService {
    private final WorkforceAccess access;
    private final WorkforceCompanyRepository repository;
    private final WorkforceAudit audit;
    public WorkforceCompanyService(WorkforceAccess access,WorkforceCompanyRepository repository,WorkforceAudit audit){this.access=access;this.repository=repository;this.audit=audit;}
    public List<Company> list(CurrentActor actor,UUID store){
        access.enter(actor,store);access.headOffice(actor);
        return repository.list(actor.tenantId()).stream().filter(c->access.companyAllowed(actor,c.id())).toList();
    }
    @Transactional
    public Company save(CurrentActor actor,UUID store,UUID id,CompanyWrite c){
        access.sourceWrite(actor);
        access.enter(actor,store);access.company(actor,id);
        if(c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.registrationNo()==null||c.registrationNo().trim().length()>40||c.payDay()==null||c.payDay()<1||c.payDay()>28||c.enabled()==null)throw WorkforceException.invalid();
        repository.lock(actor.tenantId(),id);
        var old=repository.list(actor.tenantId()).stream().filter(o->o.id().equals(id)).findFirst().orElseThrow(WorkforceException::missing);
        if(old.version()!=c.expectedVersion())throw WorkforceException.conflict();
        repository.save(actor.tenantId(),id,c,old.version()+1);
        var saved=new Company(id,old.name(),c.registrationNo().trim(),c.payDay(),c.enabled(),old.version()+1);
        audit.changed(actor,store,"company.save",id,old,saved);return saved;
    }
}
