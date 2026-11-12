package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Clock;
import java.time.ZoneId;
import com.rpb.reservation.workforce.statutory.StatutoryPolicies;
import com.rpb.reservation.workforce.statutory.StatutoryPolicyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PayrollConfigurationRepository {
    private final JdbcTemplate jdbc;
    private final WorkforceJson json;
    private final Clock clock;
    private final com.rpb.reservation.workforce.payroll.governance.EffectivePayGovernance effective;
    public com.rpb.reservation.workforce.statutory.annual.AnnualWageRepository annual(){return new com.rpb.reservation.workforce.statutory.annual.AnnualWageRepository(jdbc,json,clock);}
    public PayrollConfigurationRepository(JdbcTemplate jdbc,WorkforceJson json){this(jdbc,json,Clock.systemUTC());}
    @org.springframework.beans.factory.annotation.Autowired public PayrollConfigurationRepository(JdbcTemplate jdbc,WorkforceJson json,Clock clock){this.jdbc=jdbc;this.json=json;this.clock=clock;this.effective=new com.rpb.reservation.workforce.payroll.governance.EffectivePayGovernance(jdbc,json);}
    public com.rpb.reservation.workforce.payroll.governance.EffectivePayGovernance effective(){return effective;}
    public StatutoryPolicies.Policy activePolicy(UUID tenant,YearMonth month){return new StatutoryPolicyRepository(jdbc,json).resolve(tenant,month);}
    public void lock(UUID id){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.pay.configuration:"+id);}
    public void lockSubject(UUID tenant,UUID company,UUID employee){new com.rpb.reservation.workforce.payroll.governance.PayChangeRepository(jdbc,json).lock("subject:"+tenant+":"+company+":"+employee);}
    public void lockDefaults(UUID tenant,UUID company){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.pay.defaults:"+tenant+":"+company);}
    public List<PositionSalaryDefault> defaults(UUID tenant,UUID company,UUID position){return jdbc.query("select snapshot from workforce_position_salary_defaults where tenant_id=? and company_id=? and position_id=? order by effective_from,id",(r,n)->json.read(r.getString(1),PositionSalaryDefault.class),tenant,company,position);}
    public List<PositionSalaryDefault> defaults(UUID tenant,UUID company,UUID position,String basis){
        if(basis==null)return defaults(tenant,company,position);
        return jdbc.query("select snapshot from workforce_position_basis_salary_defaults where tenant_id=? and company_id=? and position_id=? and basis=? order by effective_from,id",(r,n)->json.read(r.getString(1),PositionSalaryDefault.class),tenant,company,position,basis);
    }
    public PositionSalaryDefault applicableDefault(UUID tenant,UUID company,UUID position,String basis,LocalDate on){
        var current=defaults(tenant,company,position,basis).stream().filter(d->!d.effectiveFrom().isAfter(on)).reduce((a,b)->b).orElse(null);
        if(current!=null||basis==null)return current;
        var legacy=defaults(tenant,company,position).stream().filter(d->!d.effectiveFrom().isAfter(on)).reduce((a,b)->b).orElse(null);
        return legacy!=null&&findTemplate(tenant,legacy.templateId()).map(t->basis.equals(t.rule().basis())).orElse(false)?legacy:null;
    }
    public Optional<PositionSalaryDefault> findDefault(UUID tenant,UUID id){
        var rows=jdbc.query("select snapshot from workforce_position_salary_defaults where tenant_id=? and id=? union all select snapshot from workforce_position_basis_salary_defaults where tenant_id=? and id=?",(r,n)->json.read(r.getString(1),PositionSalaryDefault.class),tenant,id,tenant,id);
        if(rows.size()>1)throw WorkforceException.conflict();return rows.stream().findFirst();
    }
    public void saveDefault(UUID tenant,PositionSalaryDefault d){
        if(d.basis()!=null){
            int changed=jdbc.update("insert into workforce_position_basis_salary_defaults(id,tenant_id,company_id,position_id,basis,effective_from,template_id,template_version,enabled,version,snapshot) values(?,?,?,?,?,?,?,?,?,?,?::jsonb) on conflict(id) do update set template_id=excluded.template_id,template_version=excluded.template_version,enabled=excluded.enabled,version=excluded.version,snapshot=excluded.snapshot,updated_at=now() where workforce_position_basis_salary_defaults.tenant_id=excluded.tenant_id and workforce_position_basis_salary_defaults.company_id=excluded.company_id and workforce_position_basis_salary_defaults.position_id=excluded.position_id and workforce_position_basis_salary_defaults.basis=excluded.basis and workforce_position_basis_salary_defaults.effective_from=excluded.effective_from and workforce_position_basis_salary_defaults.version+1=excluded.version",d.id(),tenant,d.companyId(),d.positionId(),d.basis(),d.effectiveFrom(),d.templateId(),d.templateVersion(),d.enabled(),d.version(),json.write(d));
            if(changed!=1)throw WorkforceException.conflict();return;
        }
        int changed=jdbc.update("insert into workforce_position_salary_defaults(id,tenant_id,company_id,position_id,effective_from,template_id,template_version,enabled,version,snapshot) values(?,?,?,?,?,?,?,?,?,?::jsonb) on conflict(id) do update set template_id=excluded.template_id,template_version=excluded.template_version,enabled=excluded.enabled,version=excluded.version,snapshot=excluded.snapshot,updated_at=now() where workforce_position_salary_defaults.tenant_id=excluded.tenant_id and workforce_position_salary_defaults.company_id=excluded.company_id and workforce_position_salary_defaults.position_id=excluded.position_id and workforce_position_salary_defaults.effective_from=excluded.effective_from",d.id(),tenant,d.companyId(),d.positionId(),d.effectiveFrom(),d.templateId(),d.templateVersion(),d.enabled(),d.version(),json.write(d));
        if(changed!=1)throw WorkforceException.conflict();
    }
    public List<SalaryTemplate> listTemplates(UUID tenant,UUID company){return jdbc.query("select snapshot from workforce_pay_templates where tenant_id=? and company_id=? order by name,id",(r,n)->json.read(r.getString(1),SalaryTemplate.class),tenant,company);}
    /** Legacy templates remain company-shared; only the current store adoption is a new candidate. */
    public List<SalaryTemplate> listTemplates(UUID tenant,UUID company,UUID store){return listTemplates(tenant,company).stream().filter(t->templateAccessible(tenant,store,t.id(),false)).toList();}
    public boolean managedTemplate(UUID tenant,UUID template){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_pay_template_adoptions where tenant_id=? and materialized_template_id=?)",Boolean.class,tenant,template));}
    public void assertTemplateUsable(UUID tenant,UUID store,UUID template){assertTemplateAccess(tenant,store,template,false);}
    /** History permits maintaining an existing same-template profile, never selecting it for a new profile. */
    public void assertTemplateAccess(UUID tenant,UUID store,UUID template,boolean historicalSource){assertTemplateAccess(tenant,store,template,historicalSource,LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore"))));}
    public void assertTemplateAccess(UUID tenant,UUID store,UUID template,boolean historicalSource,LocalDate on){if(!templateAccessible(tenant,store,template,historicalSource,on))throw WorkforceException.forbidden();findTemplate(tenant,template).ifPresent(t->effective.assertEconomicDate(tenant,t,on));}
    public boolean templateAccessible(UUID tenant,UUID store,UUID template,boolean historicalSource){return templateAccessible(tenant,store,template,historicalSource,LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore"))));}
    public boolean templateAccessible(UUID tenant,UUID store,UUID template,boolean historicalSource,LocalDate on){
        var today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore")));var candidateOn=on.isBefore(today)?today:on;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
          select not exists(select 1 from workforce_pay_template_adoptions a where a.tenant_id=? and a.materialized_template_id=?)
          or exists(select 1 from workforce_pay_template_adoptions a join stores s on s.id=a.store_id and s.tenant_id=a.tenant_id
            join operating_entities c on c.id=a.company_id and c.tenant_id=a.tenant_id
            where a.tenant_id=? and a.materialized_template_id=? and a.store_id=? and s.operating_entity_id=a.company_id
            and s.status='active' and s.deleted_at is null and c.status='active' and c.deleted_at is null
            and not exists(select 1 from workforce_company_settings cs where cs.tenant_id=a.tenant_id and cs.company_id=a.company_id and cs.enabled=false)
            and (? or a.id=(select newer.id from workforce_pay_template_adoptions newer where newer.tenant_id=a.tenant_id and newer.store_id=a.store_id and newer.master_id=a.master_id and newer.company_id=a.company_id and newer.effective_from<=? order by newer.effective_from desc,newer.store_version desc limit 1)))
          """,Boolean.class,tenant,template,tenant,template,store,historicalSource,candidateOn));
    }
    public Optional<SalaryTemplate> findTemplate(UUID tenant,UUID id){return jdbc.query("select snapshot from workforce_pay_templates where tenant_id=? and id=?",(r,n)->json.read(r.getString(1),SalaryTemplate.class),tenant,id).stream().findFirst();}
    @org.springframework.transaction.annotation.Transactional public void saveTemplate(UUID tenant,SalaryTemplate t){
        new com.rpb.reservation.workforce.persistence.WorkforceDirectory(jdbc).lockSourcesShared(tenant);lock(t.id());
        effective.assertTemplate(tenant,t);var current=findTemplate(tenant,t.id()).orElse(null);if(current!=null){if(current.equals(t))return;if(current.version()+1!=t.version())throw WorkforceException.conflict();}
        int changed=jdbc.update("""
            insert into workforce_pay_templates(id,tenant_id,company_id,name,snapshot,enabled,version) values(?,?,?,?,?::jsonb,?,?)
            on conflict(id) do update set name=excluded.name,snapshot=excluded.snapshot,enabled=excluded.enabled,version=excluded.version,updated_at=now()
            where workforce_pay_templates.tenant_id=excluded.tenant_id and workforce_pay_templates.company_id=excluded.company_id and workforce_pay_templates.version+1=excluded.version
            """,t.id(),tenant,t.companyId(),t.name(),json.write(t),t.enabled(),t.version());
        if(changed!=1)throw WorkforceException.conflict();
    }
    public List<PayProfile> profiles(UUID tenant,UUID company){return jdbc.query("select snapshot from workforce_pay_profiles where tenant_id=? and company_id=? order by employee_id,effective_from,id",(r,n)->json.read(r.getString(1),PayProfile.class),tenant,company);}
    public List<PayProfile> listProfiles(UUID tenant,UUID company){return profiles(tenant,company);}
    public List<PayProfile> employeeProfiles(UUID tenant,UUID company,UUID employee){return jdbc.query("select snapshot from workforce_pay_profiles where tenant_id=? and company_id=? and employee_id=? order by effective_from,id",(r,n)->json.read(r.getString(1),PayProfile.class),tenant,company,employee);}
    public Optional<PayProfile> findProfile(UUID tenant,UUID id){return jdbc.query("select snapshot from workforce_pay_profiles where tenant_id=? and id=?",(r,n)->json.read(r.getString(1),PayProfile.class),tenant,id).stream().findFirst();}
    @org.springframework.transaction.annotation.Transactional public void saveProfile(UUID tenant,PayProfile p){
        new com.rpb.reservation.workforce.persistence.WorkforceDirectory(jdbc).lockSourcesShared(tenant);new com.rpb.reservation.workforce.payroll.governance.PayChangeRepository(jdbc,json).lock("subject:"+tenant+":"+p.companyId()+":"+p.employeeId());lockDefaults(tenant,p.companyId());new com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository(jdbc).lock(p.employeeId());lock(p.id());
        effective.assertProfile(tenant,p);
        var current=findProfile(tenant,p.id()).orElse(null);if(current!=null){if(current.equals(p))return;if(current.version()+1!=p.version())throw WorkforceException.conflict();if(lockedProfile(tenant,p.id()))throw new WorkforceException("WORKFORCE_PAYROLL_PROFILE_LOCKED",409);}
        int changed=jdbc.update("""
            insert into workforce_pay_profiles(id,tenant_id,company_id,employee_id,effective_from,template_id,template_version,snapshot,version) values(?,?,?,?,?,?,?,?::jsonb,?)
            on conflict(id) do update set template_id=excluded.template_id,template_version=excluded.template_version,snapshot=excluded.snapshot,version=excluded.version,updated_at=now()
            where workforce_pay_profiles.tenant_id=excluded.tenant_id and workforce_pay_profiles.company_id=excluded.company_id and workforce_pay_profiles.employee_id=excluded.employee_id and workforce_pay_profiles.effective_from=excluded.effective_from and workforce_pay_profiles.version+1=excluded.version
            """,p.id(),tenant,p.companyId(),p.employeeId(),p.effectiveFrom(),p.templateId(),p.templateVersion(),json.write(p),p.version());
        if(changed!=1)throw WorkforceException.conflict();
    }
    public boolean lockedProfile(UUID tenant,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists(select 1 from workforce_pay_batches b cross join lateral jsonb_array_elements(coalesce(b.calculation->'lines','[]'::jsonb)) l
        where b.tenant_id=? and b.status in ('confirmed','published') and l->'profile'->>'id'=? ) or exists(select 1 from workforce_pay_segments s where s.tenant_id=? and s.status in ('confirmed','published') and s.snapshot->'cumulative'->'lines'->0->'profile'->>'id'=?)
        """,Boolean.class,tenant,id.toString(),tenant,id.toString()));}
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
        select e.id,e.name||' 路 '||e.employee_no from workforce_employees e where e.tenant_id=? and
        (e.company_id=? or exists(select 1 from workforce_company_history h where h.tenant_id=e.tenant_id and h.employee_id=e.id and h.company_id=?))
        order by e.employee_no,e.id
        """,(r,n)->new Option(r.getObject(1,UUID.class),r.getString(2)),tenant,company,company);}
}
