package com.rpb.reservation.workforce.persistence;

import com.rpb.reservation.workforce.application.WorkforceDtos.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read adapter for existing tenant/company/store and Auth identities. */
@Repository
public class WorkforceDirectory {
    private final JdbcTemplate jdbc;
    public WorkforceDirectory(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void lockSourcesShared(UUID tenant){jdbc.queryForObject("select pg_advisory_xact_lock_shared(hashtextextended(?,0))",Object.class,"workforce.payroll.source:"+tenant);}
    public List<StoreOption> stores(UUID tenant){
        return jdbc.query("select id,display_name,operating_entity_id from stores where tenant_id=? and deleted_at is null and status='active' order by display_name,id",
            (r,n)->new StoreOption(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class)),tenant);
    }
    public List<Option> companies(UUID tenant){
        return jdbc.query("select id,display_name from operating_entities where tenant_id=? and deleted_at is null and status='active' order by display_name,id",
            (r,n)->new Option(r.getObject(1,UUID.class),r.getString(2)),tenant);
    }
    public Set<UUID> managerStoreIds(UUID tenant,UUID account){
        return new HashSet<>(jdbc.queryForList("select store_id from workforce_administrators where tenant_id=? and account_id=? and employee_manage=true",UUID.class,tenant,account));
    }
    public Set<UUID> accountStores(UUID tenant,UUID account){
        return new HashSet<>(jdbc.queryForList("select store_id from auth_account_store_access where tenant_id=? and account_id=? and deleted_at is null",UUID.class,tenant,account));
    }
    public List<Option> accounts(UUID tenant,boolean includeManagers){
        return jdbc.query("""
            select a.id,a.display_name||' · '||a.username from auth_accounts a
            where a.tenant_id=? and a.actor_type='staff' and a.status='active' and a.deleted_at is null
            and not exists(select 1 from auth_account_roles r where r.account_id=a.id and r.deleted_at is null
                and (r.role_code='platform_admin' or (not ? and r.role_code in ('tenant_admin','store_manager'))))
            order by a.display_name,a.id
            """,(r,n)->new Option(r.getObject(1,UUID.class),r.getString(2)),tenant,includeManagers);
    }
    public boolean ordinaryAccount(UUID tenant,UUID account){return accounts(tenant,false).stream().anyMatch(o->o.id().equals(account));}
    public boolean companyEnabled(UUID tenant,UUID company){
        return !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_company_settings where tenant_id=? and company_id=? and enabled=false)",Boolean.class,tenant,company));
    }
}
