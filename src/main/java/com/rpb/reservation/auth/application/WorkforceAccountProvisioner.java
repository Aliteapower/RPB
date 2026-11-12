package com.rpb.reservation.auth.application;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Auth owns creation of least-privilege accounts; no legacy product defaults. */
@Service
public class WorkforceAccountProvisioner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public WorkforceAccountProvisioner(JdbcTemplate jdbc,PasswordEncoder passwords){this.jdbc=jdbc;this.passwords=passwords;}
    public UUID create(UUID tenant,String username,String name,String password,UUID mainStore,List<UUID> stores){
        UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into auth_accounts(id,tenant_id,username,display_name,actor_type,status,password_hash,password_algo,default_store_id)
            values(?,?,?,?,'staff','active',?,'bcrypt-lowercase-v1',?)
            """,id,tenant,username,name,passwords.encode(password.toLowerCase(Locale.ROOT)),mainStore);
        jdbc.update("insert into auth_account_roles(account_id,role_code) values(?,'store_staff')",id);
        for(UUID store:stores)jdbc.update("insert into auth_account_store_access(account_id,tenant_id,store_id) values(?,?,?)",id,tenant,store);
        allowEntry(tenant,id);return id;
    }
    public void allowEntry(UUID tenant,UUID account){
        jdbc.update("""
            insert into auth_account_permissions(account_id,permission_code)
            select id,'workforce.view' from auth_accounts where id=? and tenant_id=? and deleted_at is null
            and not exists(select 1 from auth_account_permissions p where p.account_id=? and p.permission_code='workforce.view' and p.deleted_at is null)
            """,account,tenant,account);
    }
    public boolean matchesUsername(UUID tenant,UUID account,String username){
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from auth_accounts where tenant_id=? and id=? and username=?)",Boolean.class,tenant,account,username));
    }
}
