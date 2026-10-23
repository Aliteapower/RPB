package com.rpb.reservation.loyalty.persistence;
import com.rpb.reservation.loyalty.application.PointsViews.*;
import com.rpb.reservation.loyalty.domain.*;
import com.rpb.reservation.common.scope.TenantScope;
import java.util.*;
import java.sql.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class PointsRepository {
 private final JdbcTemplate jdbc;
 public PointsRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public UUID createProgram(UUID tenant,UUID issuer){
  UUID id=UUID.randomUUID();
  jdbc.update("insert into loyalty_programs(id,tenant_id,operating_entity_id,name) values(?,?,?,'手动积分')",id,tenant,issuer);
  return id;
 }
 public Account account(Binding b,UUID customer,boolean create,boolean lock){
  if(create){
   if(b.programId()==null)throw PointsException.missing();
   jdbc.update("insert into loyalty_accounts(tenant_id,program_id,customer_id) values(?,?,?) on conflict(tenant_id,program_id,customer_id) do nothing",b.tenantId(),b.programId(),customer);
  }
  var rows=jdbc.query("select a.*,p.operating_entity_id,p.status as program_status from loyalty_accounts a join loyalty_programs p on p.id=a.program_id and p.tenant_id=a.tenant_id where a.tenant_id=? and p.id=? and p.operating_entity_id=? and a.customer_id=?"+(lock?" for update of a":""),(rs,n)->{
   if(lock&&!"active".equals(rs.getString("program_status")))throw PointsException.conflict("POINTS_PROGRAM_INACTIVE","积分计划已停用");
   return account(rs,b.walletProgramId());
  },b.tenantId(),b.programId(),b.issuerId(),customer);
  if(rows.isEmpty())return new Account(null,b.programId(),b.walletProgramId(),b.tenantId(),b.issuerId(),customer,0,0);
  var a=rows.getFirst();if(lock){
   long total=jdbc.queryForObject("select coalesce(sum(after_balance::bigint-before_balance::bigint),0) from loyalty_entries where tenant_id=? and account_id=?",Long.class,b.tenantId(),a.id());
   if(total!=a.balance())throw PointsException.conflict("POINTS_LEDGER_MISMATCH","积分余额与流水不一致，请管理员核查");
  }return a;
 }
 public Account update(Account a,int balance){
  int n=jdbc.update("update loyalty_accounts set balance=?,version=version+1,updated_at=now() where tenant_id=? and id=? and version=?",balance,a.tenantId(),a.id(),a.version());
  if(n!=1)throw PointsException.conflict("POINTS_VERSION_STALE","积分已变化，请刷新核对");
  return new Account(a.id(),a.programId(),a.walletProgramId(),a.tenantId(),a.issuerId(),a.customerId(),balance,a.version()+1);
 }
 public Entry append(Binding b,UUID actor,Account before,Account after,String kind,int quantity,String reason,String note,String external,UUID original){
  return jdbc.query("insert into loyalty_entries(tenant_id,program_id,operating_entity_id,account_id,store_id,actor_id,kind,quantity,before_balance,after_balance,account_version,reason,note,external_reference,original_entry_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) returning *,null::text as redemption_description",(rs,n)->entry(rs),b.tenantId(),before.programId(),b.issuerId(),before.id(),b.storeId(),actor,kind,quantity,before.balance(),after.balance(),after.version(),reason,note,external,original).getFirst();
 }
 public UUID customerForEntry(Binding b,UUID entry){return jdbc.query("select a.customer_id from loyalty_entries e join loyalty_accounts a on a.id=e.account_id and a.tenant_id=e.tenant_id join loyalty_programs p on p.id=e.program_id and p.tenant_id=e.tenant_id where e.id=? and e.tenant_id=? and p.id=? and p.operating_entity_id=?",(rs,n)->rs.getObject(1,UUID.class),entry,b.tenantId(),b.programId(),b.issuerId()).stream().findFirst().orElseThrow(PointsException::missing);}
 public Entry entry(Binding b,UUID id){return jdbc.query("select e.*,r.redemption_description from loyalty_entries e left join loyalty_redemptions r on r.entry_id=e.id and r.tenant_id=e.tenant_id join loyalty_programs p on p.id=e.program_id and p.tenant_id=e.tenant_id where e.tenant_id=? and p.id=? and p.operating_entity_id=? and e.id=?",(rs,n)->entry(rs),b.tenantId(),b.programId(),b.issuerId(),id).stream().findFirst().orElseThrow(PointsException::missing);}
 public boolean reversed(UUID tenant,UUID entry){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from loyalty_entries where tenant_id=? and original_entry_id=?)",Boolean.class,tenant,entry));}
 public List<Entry> entries(Binding b,UUID customer,int offset){
  if(offset<0||offset>1000000)throw PointsException.invalid();
  var a=account(b,customer,false,false);if(a.id()==null)return List.of();
  return jdbc.query("select e.*,r.redemption_description from loyalty_entries e left join loyalty_redemptions r on r.entry_id=e.id and r.tenant_id=e.tenant_id where e.tenant_id=? and e.account_id=? order by e.account_version desc limit 50 offset ?",(rs,n)->entry(rs),b.tenantId(),a.id(),offset);
 }
 public Redemption redeem(Binding b,Entry e,String description,String external){return jdbc.query("insert into loyalty_redemptions(tenant_id,entry_id,redemption_description,external_reference) values(?,?,?,?) returning *",(rs,n)->new Redemption(rs.getObject("id",UUID.class),e.id(),rs.getString("redemption_description"),rs.getString("external_reference")),b.tenantId(),e.id(),description,external).getFirst();}
 public boolean retainsCustomer(TenantScope scope,UUID customer){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from loyalty_accounts where tenant_id=? and customer_id=?)",Boolean.class,scope.tenantId().value(),customer));}
 private Account account(ResultSet r,UUID walletProgram)throws SQLException{return new Account(r.getObject("id",UUID.class),r.getObject("program_id",UUID.class),walletProgram,r.getObject("tenant_id",UUID.class),r.getObject("operating_entity_id",UUID.class),r.getObject("customer_id",UUID.class),r.getInt("balance"),r.getInt("version"));}
 private Entry entry(ResultSet r)throws SQLException{return new Entry(r.getObject("id",UUID.class),r.getObject("account_id",UUID.class),r.getObject("store_id",UUID.class),r.getObject("actor_id",UUID.class),r.getString("kind"),r.getInt("quantity"),r.getInt("before_balance"),r.getInt("after_balance"),r.getInt("account_version"),r.getString("reason"),r.getString("note"),r.getString("external_reference"),r.getObject("original_entry_id",UUID.class),r.getString("redemption_description"),r.getTimestamp("created_at").toInstant());}
}
