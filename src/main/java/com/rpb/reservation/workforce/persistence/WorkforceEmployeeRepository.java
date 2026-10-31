package com.rpb.reservation.workforce.persistence;

import com.rpb.reservation.workforce.application.WorkforceDtos.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkforceEmployeeRepository {
    private final JdbcTemplate jdbc;
    private static final String SELECT="select e.*,coalesce(p.name,e.job_title) as current_job_title from workforce_employees e left join workforce_positions p on p.id=e.position_id and p.tenant_id=e.tenant_id ";
    public WorkforceEmployeeRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void lock(UUID id){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.employee:"+id);}
    public boolean hasActiveAttendance(UUID tenant,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_attendance_sessions where tenant_id=? and employee_id=? and out_at is null)",Boolean.class,tenant,id));}
    public Optional<Employee> find(UUID tenant,UUID id){return jdbc.query(SELECT+"where e.tenant_id=? and e.id=?",this::employee,tenant,id).stream().findFirst();}
    /** Two tenant-scoped reads; unlike the single-row mapper this never queries per employee. */
    public Map<UUID,Employee> findAll(UUID tenant,List<UUID> ids){
        if(ids.isEmpty())return Map.of();var args=new ArrayList<Object>();args.add(tenant);args.addAll(ids);
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));var memberships=new HashMap<UUID,List<UUID>>();
        jdbc.query("select employee_id,store_id from workforce_work_stores where tenant_id=? and employee_id in ("+placeholders+") and ended_on is null order by store_id",r->{memberships.computeIfAbsent(r.getObject(1,UUID.class),k->new ArrayList<>()).add(r.getObject(2,UUID.class));},args.toArray());
        var result=new HashMap<UUID,Employee>();jdbc.query(SELECT+"where e.tenant_id=? and e.id in ("+placeholders+")",(r,n)->employee(r,memberships.getOrDefault(r.getObject("id",UUID.class),List.of())),args.toArray()).forEach(e->result.put(e.id(),e));return result;
    }
    public Optional<Employee> byAccount(UUID tenant,UUID account){return jdbc.query(SELECT+"where e.tenant_id=? and e.account_id=?",this::employee,tenant,account).stream().findFirst();}
    public List<Employee> list(UUID tenant,UUID store,int limit,int offset){
        return jdbc.query(SELECT+"""
            where e.tenant_id=?
            and exists(select 1 from workforce_work_stores w where w.employee_id=e.id and w.tenant_id=e.tenant_id and w.store_id=? and w.ended_on is null)
            order by e.employee_no,e.id limit ? offset ?
            """,this::employee,tenant,store,limit,offset);
    }
    private Employee employee(ResultSet r,int n)throws SQLException {
        UUID id=r.getObject("id",UUID.class);
        var stores=jdbc.queryForList("select store_id from workforce_work_stores where employee_id=? and tenant_id=? and ended_on is null order by store_id",UUID.class,id,r.getObject("tenant_id",UUID.class));
        return employee(r,stores);
    }
    private Employee employee(ResultSet r,List<UUID> stores)throws SQLException {
        UUID id=r.getObject("id",UUID.class);
        return new Employee(id,r.getString("employee_no"),r.getString("name"),r.getString("phone"),r.getString("email"),r.getString("current_job_title"),
            r.getObject("hire_date",LocalDate.class),r.getObject("leave_date",LocalDate.class),r.getString("status"),r.getObject("company_id",UUID.class),
            r.getObject("company_effective_date",LocalDate.class),r.getObject("main_store_id",UUID.class),stores,r.getObject("account_id",UUID.class),r.getLong("version"),true,r.getObject("position_id",UUID.class),r.getObject("birth_date",LocalDate.class),r.getString("english_name"),r.getBoolean("enabled"));
    }
    public void save(UUID tenant,UUID id,EmployeeWrite c,UUID account,Employee old,LocalDate today){
        long version=old==null?1:old.version()+1;
        if(old==null)jdbc.update("""
            insert into workforce_employees(id,tenant_id,employee_no,name,phone,email,job_title,hire_date,leave_date,status,company_id,company_effective_date,main_store_id,account_id,version,position_id,birth_date,english_name,enabled)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,tenant,c.employeeNo(),c.name(),c.phone(),c.email(),c.jobTitle(),c.hireDate(),c.leaveDate(),c.status(),c.companyId(),c.companyEffectiveDate(),c.mainStoreId(),account,version,c.positionId(),c.birthDate(),c.englishName(),c.enabled());
        else jdbc.update("""
            update workforce_employees set employee_no=?,name=?,phone=?,email=?,job_title=?,hire_date=?,leave_date=?,status=?,company_id=?,company_effective_date=?,main_store_id=?,account_id=?,version=?,position_id=?,birth_date=?,english_name=?,enabled=?,updated_at=now()
            where id=? and tenant_id=?
            """,c.employeeNo(),c.name(),c.phone(),c.email(),c.jobTitle(),c.hireDate(),c.leaveDate(),c.status(),c.companyId(),c.companyEffectiveDate(),c.mainStoreId(),account,version,c.positionId(),c.birthDate(),c.englishName(),c.enabled(),id,tenant);
        if(old!=null&&!old.hireDate().equals(c.hireDate())){
            jdbc.update("update workforce_work_stores set started_on=?,updated_at=now() where tenant_id=? and employee_id=? and started_on=?",c.hireDate(),tenant,id,old.hireDate());
            if(old.companyId().equals(c.companyId())&&!old.companyEffectiveDate().equals(c.companyEffectiveDate()))jdbc.update("update workforce_company_history set effective_date=? where tenant_id=? and employee_id=? and effective_date=?",c.companyEffectiveDate(),tenant,id,old.companyEffectiveDate());
        }
        if(old==null||!old.companyId().equals(c.companyId()))jdbc.update("insert into workforce_company_history(employee_id,tenant_id,company_id,effective_date,version) values(?,?,?,?,?)",id,tenant,c.companyId(),c.companyEffectiveDate(),version);
        var before=old==null?List.<UUID>of():old.storeIds();
        for(UUID store:before)if(!c.storeIds().contains(store))jdbc.update("update workforce_work_stores set ended_on=?,updated_at=now() where employee_id=? and tenant_id=? and store_id=? and ended_on is null",today,id,tenant,store);
        for(UUID store:c.storeIds())if(!before.contains(store))jdbc.update("insert into workforce_work_stores(employee_id,tenant_id,store_id,started_on) values(?,?,?,?)",id,tenant,store,old==null?c.hireDate():today);
    }
    public EmployeeWrite correctHireDate(UUID tenant,Employee old,EmployeeWrite c){
        if(c.hireDate()==null)throw com.rpb.reservation.workforce.domain.WorkforceException.invalid();
        var earliest=jdbc.queryForObject("select min(d) from (select work_date d from workforce_schedules where tenant_id=? and employee_id=? union all select effective_from from workforce_pay_profiles where tenant_id=? and employee_id=? union all select ended_on from workforce_work_stores where tenant_id=? and employee_id=? and started_on=? and ended_on is not null) t",LocalDate.class,tenant,old.id(),tenant,old.id(),tenant,old.id(),old.hireDate());
        if(earliest!=null&&c.hireDate().isAfter(earliest))throw new com.rpb.reservation.workforce.domain.WorkforceException("WORKFORCE_HIRE_DATE_IN_USE",409);
        long histories=jdbc.queryForObject("select count(*) from workforce_company_history where tenant_id=? and employee_id=?",Long.class,tenant,old.id());
        if(earliest==null&&histories==1&&old.companyId().equals(c.companyId())&&old.companyEffectiveDate().equals(old.hireDate())&&old.companyEffectiveDate().equals(c.companyEffectiveDate()))return com.rpb.reservation.workforce.application.EmployeeProfileRules.effective(c,c.hireDate());
        if(c.companyEffectiveDate()==null||c.hireDate().isAfter(c.companyEffectiveDate())||!old.companyEffectiveDate().equals(c.companyEffectiveDate()))throw new com.rpb.reservation.workforce.domain.WorkforceException("WORKFORCE_HIRE_DATE_IN_USE",409);
        return c;
    }
}
