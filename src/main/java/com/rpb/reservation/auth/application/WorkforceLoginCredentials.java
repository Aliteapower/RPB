package com.rpb.reservation.auth.application;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Authentication owns credential hashes and session invalidation; callers authorize the target. */
@Service
public class WorkforceLoginCredentials {
 public record Info(UUID accountId,String username,String status,long version,boolean workforceAccess,boolean resetEligible,boolean managerAccount,boolean workforceOnly){}
 public record Changed(Info info,boolean changed){}
 public static class Conflict extends RuntimeException {}
 private final JdbcTemplate jdbc;private final PasswordEncoder encoder;
 public WorkforceLoginCredentials(JdbcTemplate jdbc,PasswordEncoder encoder){this.jdbc=jdbc;this.encoder=encoder;}
 public Optional<Info> find(UUID tenant,UUID account,boolean lock){return jdbc.query("""
  select a.id,a.username,a.status,a.version,
   exists(select 1 from auth_account_permissions p where p.account_id=a.id and p.permission_code='workforce.view' and p.deleted_at is null),
   a.actor_type='staff' and a.status in ('active','disabled')
    and not exists(select 1 from auth_account_roles r where r.account_id=a.id and r.role_code in ('platform_admin','tenant_admin') and r.deleted_at is null)
    and not exists(select 1 from auth_account_permissions p where p.account_id=a.id and p.permission_code in ('tenant.admin.manage','workforce.admin.manage') and p.deleted_at is null),
   exists(select 1 from auth_account_roles r where r.account_id=a.id and r.role_code in ('store_manager','tenant_admin','platform_admin') and r.deleted_at is null),
   not exists(select 1 from auth_account_permissions p where p.account_id=a.id and p.permission_code<>'workforce.view' and p.deleted_at is null)
    and not exists(select 1 from auth_account_roles r where r.account_id=a.id and r.role_code<>'store_staff' and r.deleted_at is null)
    and not exists(select 1 from auth_staff_store_permissions p where p.account_id=a.id and p.tenant_id=a.tenant_id and cardinality(p.permissions)>0)
  from auth_accounts a where a.tenant_id=? and a.id=? and a.deleted_at is null
  """+(lock?" for update of a":""),(r,n)->new Info(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getLong(4),r.getBoolean(5),r.getBoolean(6),r.getBoolean(7),r.getBoolean(8)),tenant,account).stream().findFirst();}
 public Changed reset(UUID tenant,Info locked,long expectedVersion,String password){
  String normalized=password.toLowerCase(Locale.ROOT);
  if(locked.version()==expectedVersion+1){String hash=jdbc.queryForObject("select password_hash from auth_accounts where tenant_id=? and id=?",String.class,tenant,locked.accountId());if(encoder.matches(normalized,hash))return new Changed(locked,false);}
  if(locked.version()!=expectedVersion)throw new Conflict();
  int updated=jdbc.update("""
   update auth_accounts set password_hash=?,password_algo='bcrypt-lowercase-v1',failed_login_count=0,
    locked_until_at=null,updated_at=now(),version=version+1
   where tenant_id=? and id=? and version=? and status in ('active','disabled') and deleted_at is null
   """,encoder.encode(normalized),tenant,locked.accountId(),expectedVersion);
  if(updated!=1)throw new Conflict();
  revoke(tenant,locked.accountId());
  return new Changed(find(tenant,locked.accountId(),false).orElseThrow(),true);
 }
 public Changed useEmployeeNumber(UUID tenant,Info locked,long expectedVersion,String number){
  if(locked.version()==expectedVersion+1&&locked.username().equals(number))return new Changed(locked,false);
  if(locked.version()!=expectedVersion)throw new Conflict();
  if(locked.username().equals(number))return new Changed(locked,false);
  int updated=jdbc.update("update auth_accounts set username=?,version=version+1,updated_at=now() where tenant_id=? and id=? and version=? and deleted_at is null",number,tenant,locked.accountId(),expectedVersion);
  if(updated!=1)throw new Conflict();
  revoke(tenant,locked.accountId());
  return new Changed(find(tenant,locked.accountId(),false).orElseThrow(),true);
 }
 private void revoke(UUID tenant,UUID account){jdbc.update("""
   update auth_user_sessions set status='revoked',revoked_at=now(),updated_at=now(),version=version+1
   where tenant_id=? and account_id=? and status='active' and deleted_at is null
   """,tenant,account);
 }
}
