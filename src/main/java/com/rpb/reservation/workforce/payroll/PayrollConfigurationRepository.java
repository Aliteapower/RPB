package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import java.time.LocalDate;
import java.time.YearMonth;
import com.rpb.reservation.workforce.statutory.StatutoryPolicies;
import com.rpb.reservation.workforce.statutory.StatutoryPolicyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PayrollConfigurationRepository {
    private final JdbcTemplate jdbc;
    private final WorkforceJson json;
    public PayrollConfigurationRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
    public StatutoryPolicies.Policy activePolicy(UUID tenant,YearMonth month){return new StatutoryPolicyRepository(jdbc,json).resolve(tenant,month);}
    public void lock(UUID id){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.pay.configuration:"+id);}
    public List<SalaryTemplate> listTemplates(UUID tenant,UUID company){return jdbc.query("select snapshot from workforce_pay_templates where tenant_id=? and company_id=? order by name,id",(r,n)->json.read(r.getString(1),SalaryTemplate.class),tenant,company);}
    public Optional<SalaryTemplate> findTemplate(UUID tenant,UUID id){return jdbc.query("select snapshot from workforce_pay_templates where tenant_id=? and id=?",(r,n)->json.read(r.getString(1),SalaryTemplate.class),tenant,id).stream().findFirst();}
    public void saveTemplate(UUID tenant,SalaryTemplate t){
        int changed=jdbc.update("""
            insert into workforce_pay_templates(id,tenant_id,company_id,name,snapshot,enabled,version) values(?,?,?,?,?::jsonb,?,?)
            on conflict(id) do update set name=excluded.name,snapshot=excluded.snapshot,enabled=excluded.enabled,version=excluded.version,updated_at=now()
            where workforce_pay_templates.tenant_id=excluded.tenant_id and workforce_pay_templates.company_id=excluded.company_id
            """,t.id(),tenant,t.companyId(),t.name(),json.write(t),t.enabled(),t.version());
        if(changed!=1)throw WorkforceException.conflict();
    }
    public List<PayProfile> profiles(UUID tenant,UUID company){return jdbc.query("select snapshot from workforce_pay_profiles where tenant_id=? and company_id=? order by employee_id,effective_from,id",(r,n)->json.read(r.getString(1),PayProfile.class),tenant,company);}
    public List<PayProfile> listProfiles(UUID tenant,UUID company){return profiles(tenant,company);}
    public List<PayProfile> employeeProfiles(UUID tenant,UUID company,UUID employee){return jdbc.query("select snapshot from workforce_pay_profiles where tenant_id=? and company_id=? and employee_id=? order by effective_from,id",(r,n)->json.read(r.getString(1),PayProfile.class),tenant,company,employee);}
    public Optional<PayProfile> findProfile(UUID tenant,UUID id){return jdbc.query("select snapshot from workforce_pay_profiles where tenant_id=? and id=?",(r,n)->json.read(r.getString(1),PayProfile.class),tenant,id).stream().findFirst();}
    public void saveProfile(UUID tenant,PayProfile p){
        int changed=jdbc.update("""
            insert into workforce_pay_profiles(id,tenant_id,company_id,employee_id,effective_from,template_id,template_version,snapshot,version) values(?,?,?,?,?,?,?,?::jsonb,?)
            on conflict(id) do update set template_id=excluded.template_id,template_version=excluded.template_version,snapshot=excluded.snapshot,version=excluded.version,updated_at=now()
            where workforce_pay_profiles.tenant_id=excluded.tenant_id and workforce_pay_profiles.company_id=excluded.company_id and workforce_pay_profiles.employee_id=excluded.employee_id and workforce_pay_profiles.effective_from=excluded.effective_from
            """,p.id(),tenant,p.companyId(),p.employeeId(),p.effectiveFrom(),p.templateId(),p.templateVersion(),json.write(p),p.version());
        if(changed!=1)throw WorkforceException.conflict();
    }
    public boolean lockedProfile(UUID tenant,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists(select 1 from workforce_pay_batches b cross join lateral jsonb_array_elements(coalesce(b.calculation->'lines','[]'::jsonb)) l
        where b.tenant_id=? and b.status in ('confirmed','published') and l->'profile'->>'id'=? )
        """,Boolean.class,tenant,id.toString()));}
    /** The same authorized company eligibility as the employee picker; missing birthdays remain absent. */
    public Map<UUID,LocalDate> employeeBirthDates(UUID tenant,UUID company){
        var dates=new LinkedHashMap<UUID,LocalDate>();
        jdbc.query("""
            select e.id,e.birth_date from workforce_employees e where e.tenant_id=? and e.birth_date is not null and
            (e.company_id=? or exists(select 1 from workforce_company_history h where h.tenant_id=e.tenant_id and h.employee_id=e.id and h.company_id=?))
            order by e.employee_no,e.id
            """,r->{dates.put(r.getObject(1,UUID.class),r.getObject(2,LocalDate.class));},tenant,company,company);
        return Map.copyOf(dates);
    }
    public Optional<PrDocument> prDocument(UUID tenant,UUID employee,UUID id){return jdbc.query("select d.id,t.name,d.number,d.approved_on,d.pr_effective_on,d.holder_name from workforce_employee_documents d join workforce_document_types t on t.tenant_id=d.tenant_id and t.id=d.type_id where d.tenant_id=? and d.employee_id=? and d.id=? and d.deleted_at is null and d.pr_effective_on is not null",(r,n)->new PrDocument(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getObject(4,LocalDate.class),r.getObject(5,LocalDate.class),r.getString(6)),tenant,employee,id).stream().findFirst();}
    /** Keyed by employee as well as document id to preserve document ownership checks. */
    public Map<UUID,Map<UUID,PrDocument>> prDocumentsFor(UUID tenant,List<UUID> employees){
        if(employees.isEmpty())return Map.of();var args=new ArrayList<Object>();args.add(tenant);args.addAll(employees);var result=new HashMap<UUID,Map<UUID,PrDocument>>();
        jdbc.query("select d.employee_id,d.id,t.name,d.number,d.approved_on,d.pr_effective_on,d.holder_name from workforce_employee_documents d join workforce_document_types t on t.tenant_id=d.tenant_id and t.id=d.type_id where d.tenant_id=? and d.employee_id in ("+String.join(",",Collections.nCopies(employees.size(),"?"))+") and d.deleted_at is null and d.pr_effective_on is not null",r->{var doc=new PrDocument(r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getObject(5,LocalDate.class),r.getObject(6,LocalDate.class),r.getString(7));result.computeIfAbsent(r.getObject(1,UUID.class),k->new HashMap<>()).put(doc.id(),doc);},args.toArray());return result;
    }
    public Map<UUID,List<PrDocument>> employeePrDocuments(UUID tenant,UUID company){
        var values=new LinkedHashMap<UUID,List<PrDocument>>();
        jdbc.query("""
          select e.id employee,d.id,t.name,d.number,d.approved_on,d.pr_effective_on,d.holder_name from workforce_employees e
          join workforce_employee_documents d on d.tenant_id=e.tenant_id and d.employee_id=e.id
          join workforce_document_types t on t.tenant_id=d.tenant_id and t.id=d.type_id
          where e.tenant_id=? and d.deleted_at is null and d.pr_effective_on is not null and
          (e.company_id=? or exists(select 1 from workforce_company_history h where h.tenant_id=e.tenant_id and h.employee_id=e.id and h.company_id=?))
          order by e.employee_no,d.created_at,d.id
          """,r->{values.computeIfAbsent(r.getObject(1,UUID.class),key->new ArrayList<>()).add(new PrDocument(r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getObject(5,LocalDate.class),r.getObject(6,LocalDate.class),r.getString(7)));},tenant,company,company);
        values.replaceAll((key,list)->List.copyOf(list));return Map.copyOf(values);
    }
    public List<Option> employees(UUID tenant,UUID company){return jdbc.query("""
        select e.id,e.name||' · '||e.employee_no from workforce_employees e where e.tenant_id=? and
        (e.company_id=? or exists(select 1 from workforce_company_history h where h.tenant_id=e.tenant_id and h.employee_id=e.id and h.company_id=?))
        order by e.employee_no,e.id
        """,(r,n)->new Option(r.getObject(1,UUID.class),r.getString(2)),tenant,company,company);}
}
