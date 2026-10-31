package com.rpb.reservation.workforce.payroll;

import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.persistence.WorkforceDirectory;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Salary scope is explicit and never follows the employee-management flag. */
@Service
public class PayrollAccess {
    private final WorkforceAccess access;
    private final WorkforceDirectory directory;
    private final JdbcTemplate jdbc;
    public PayrollAccess(WorkforceAccess access,WorkforceDirectory directory,JdbcTemplate jdbc){this.access=access;this.directory=directory;this.jdbc=jdbc;}

    public void enter(CurrentActor actor,UUID store,UUID company){enter(actor,store,company,List.of());}
    public void enter(CurrentActor actor,UUID store,UUID company,Collection<UUID> sourceStores){
        access.enter(actor,store);
        if(company==null||sourceStores==null||sourceStores.stream().anyMatch(Objects::isNull))throw WorkforceException.invalid();
        if(directory.companies(actor.tenantId()).stream().noneMatch(c->c.id().equals(company)))throw WorkforceException.forbidden();
        if(access.isHeadOffice(actor)){access.company(actor,company);}
        else if(!granted(actor,store,company))throw WorkforceException.forbidden();
        var stores=directory.stores(actor.tenantId()).stream().filter(s->company.equals(s.companyId())).toList();
        if(stores.isEmpty()||stores.stream().anyMatch(s->!actor.canAccessStore(s.id())))throw WorkforceException.forbidden();
        var required=new TreeSet<UUID>();stores.forEach(s->required.add(s.id()));required.addAll(sourceStores);
        for(var shop:required)access.enter(actor,shop);
    }
    public List<Option> companies(CurrentActor actor,UUID store){
        access.enter(actor,store);var result=new ArrayList<Option>();
        for(var company:directory.companies(actor.tenantId())){
            if(!access.isHeadOffice(actor)&&!granted(actor,store,company.id()))continue;
            try{enter(actor,store,company.id());result.add(company);}catch(WorkforceException e){if(e.status()!=403)throw e;}
        }
        return List.copyOf(result);
    }
    private boolean granted(CurrentActor actor,UUID store,UUID company){
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_administrators where tenant_id=? and store_id=? and account_id=? and payroll_company_id=?)",Boolean.class,actor.tenantId(),store,actor.actorId(),company));
    }
    /** Eligibility, after enter has established the company-wide salary scope. */
    public void employee(CurrentActor actor,UUID company,UUID employee){
        if(employee==null||!Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from workforce_employees e where e.tenant_id=? and e.id=? and
            (e.company_id=? or exists(select 1 from workforce_company_history h where h.tenant_id=e.tenant_id and h.employee_id=e.id and h.company_id=?)))
            """,Boolean.class,actor.tenantId(),employee,company,company)))throw WorkforceException.missing();
    }
}
