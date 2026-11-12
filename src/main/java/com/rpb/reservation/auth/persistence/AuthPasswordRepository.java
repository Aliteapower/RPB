package com.rpb.reservation.auth.persistence;

import com.rpb.reservation.auth.api.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Only current-session password mutations; no administrative account selection. */
@Repository
public class AuthPasswordRepository {
 private final JdbcTemplate jdbc;
 public AuthPasswordRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Credentials(UUID id,UUID tenantId,String passwordHash,String actorType,boolean locked){}
 public Credentials lock(UUID account,UUID tenant,String sessionHash){
  return jdbc.query("""
   select a.id,a.tenant_id,a.password_hash,a.actor_type,coalesce(a.locked_until_at>now(),false) as locked
   from auth_accounts a
   where a.id=? and a.tenant_id is not distinct from ? and a.status='active' and a.deleted_at is null
    and exists(select 1 from auth_user_sessions s where s.account_id=a.id
      and s.tenant_id is not distinct from a.tenant_id and s.session_hash=?
      and s.status='active' and s.deleted_at is null and s.expires_at>now())
   for update of a
   """,(r,n)->new Credentials(r.getObject("id",UUID.class),r.getObject("tenant_id",UUID.class),r.getString("password_hash"),r.getString("actor_type"),r.getBoolean("locked")),account,tenant,sessionHash)
   .stream().findFirst().orElseThrow(()->new AuthApiException(AuthApiErrorCode.UNAUTHENTICATED));
 }
 public void failed(Credentials account){jdbc.update("""
  update auth_accounts set
   failed_login_count=case when locked_until_at<=now() then 1 else failed_login_count+1 end,
   locked_until_at=case when locked_until_at<=now() then null when failed_login_count+1>=5 then now()+interval '15 minutes' else locked_until_at end,
   last_login_failed_at=now(),updated_at=now(),version=version+1
  where id=? and tenant_id is not distinct from ?
  """,account.id(),account.tenantId());}
 public void change(Credentials account,String hash,String currentSessionHash){
  if(jdbc.update("""
   update auth_accounts set password_hash=?,password_algo='bcrypt-lowercase-v1',failed_login_count=0,
    locked_until_at=null,updated_at=now(),version=version+1
   where id=? and tenant_id is not distinct from ? and status='active' and deleted_at is null
   """,hash,account.id(),account.tenantId())!=1)throw new IllegalStateException("Password account disappeared");
  jdbc.update("""
   update auth_user_sessions set status='revoked',revoked_at=now(),updated_at=now(),version=version+1
   where account_id=? and tenant_id is not distinct from ? and session_hash<>? and status='active' and deleted_at is null
   """,account.id(),account.tenantId(),currentSessionHash);
  jdbc.update("""
   insert into audit_logs(tenant_id,operation_code,target_type,target_id,actor_type,actor_id,source)
   values(?,'auth.password.change','auth_account',?,?,?,'staff')
   """,account.tenantId(),account.id(),account.actorType(),account.id());
 }
}
