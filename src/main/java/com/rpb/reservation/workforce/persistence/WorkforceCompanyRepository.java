package com.rpb.reservation.workforce.persistence;

import com.rpb.reservation.workforce.application.WorkforceDtos.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkforceCompanyRepository {
    private final JdbcTemplate jdbc;
    public WorkforceCompanyRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Company> list(UUID tenant){
        return jdbc.query("""
            select e.id,e.display_name,coalesce(s.registration_no,''),coalesce(s.pay_day,7),coalesce(s.enabled,true),coalesce(s.version,0)
            from operating_entities e left join workforce_company_settings s on s.company_id=e.id and s.tenant_id=e.tenant_id
            where e.tenant_id=? and e.status='active' and e.deleted_at is null order by e.display_name,e.id
            """,(r,n)->new Company(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getInt(4),r.getBoolean(5),r.getLong(6)),tenant);
    }
    public void lock(UUID tenant,UUID id){jdbc.queryForList("select id from operating_entities where tenant_id=? and id=? for update",UUID.class,tenant,id);}
    public void save(UUID tenant,UUID id,CompanyWrite c,long version){
        jdbc.update("""
            insert into workforce_company_settings(tenant_id,company_id,registration_no,pay_day,enabled,version) values(?,?,?,?,?,?)
            on conflict(tenant_id,company_id) do update set registration_no=excluded.registration_no,pay_day=excluded.pay_day,enabled=excluded.enabled,version=excluded.version,updated_at=now()
            """,tenant,id,c.registrationNo().trim(),c.payDay(),c.enabled(),version);
    }
}
