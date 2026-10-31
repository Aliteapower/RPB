package com.rpb.reservation.workforce.persistence;

import com.rpb.reservation.workforce.application.WorkforceDtos.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkforceAdminRepository {
    private final JdbcTemplate jdbc;
    public WorkforceAdminRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Administrator> list(UUID tenant,UUID store){
        return jdbc.query("""
            select c.account_id,a.display_name,c.employee_manage,c.payroll_company_id,c.version
            from workforce_administrators c join auth_accounts a on a.id=c.account_id and a.tenant_id=c.tenant_id
            where c.tenant_id=? and c.store_id=? and a.deleted_at is null order by a.display_name,a.id
            """,(r,n)->new Administrator(r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3),r.getObject(4,UUID.class),r.getLong(5)),tenant,store);
    }
    public void lock(UUID tenant,UUID account){jdbc.queryForList("select id from auth_accounts where id=? and tenant_id=? for update",UUID.class,account,tenant);}
    public void save(UUID tenant,UUID store,UUID account,AdministratorWrite c,long version){
        jdbc.update("""
            insert into workforce_administrators(tenant_id,store_id,account_id,employee_manage,payroll_company_id,version) values(?,?,?,?,?,?)
            on conflict(store_id,account_id) do update set employee_manage=excluded.employee_manage,payroll_company_id=excluded.payroll_company_id,version=excluded.version,updated_at=now()
            """,tenant,store,account,c.employeeManage(),c.payrollCompanyId(),version);
    }
}
