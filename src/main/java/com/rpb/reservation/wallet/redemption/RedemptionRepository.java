package com.rpb.reservation.wallet.redemption;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.WalletException;
import java.util.UUID;
import java.time.Instant;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class RedemptionRepository {
 private final JdbcTemplate jdbc;
 public RedemptionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Request(UUID id,UUID customer,UUID creator,int quantity,String description,String status,Instant expiresAt,UUID entry){}
 private Request find(StoreScope s,UUID program,String condition,Object value,boolean lock){return jdbc.query("select * from wallet_points_redemption_requests where tenant_id=? and program_id=? and store_id=? and "+condition+(lock?" for update":""),(r,n)->new Request(r.getObject("id",UUID.class),r.getObject("customer_id",UUID.class),r.getObject("created_by",UUID.class),r.getInt("quantity"),r.getString("description"),r.getString("status"),r.getTimestamp("expires_at").toInstant(),r.getObject("entry_id",UUID.class)),s.tenantId().value(),program,s.storeId().value(),value).stream().findFirst().orElseThrow(WalletException::missing);}
 public Request get(StoreScope s,UUID program,UUID id,boolean lock){return find(s,program,"id=?",id,lock);}
 public Request code(StoreScope s,UUID program,String hash){return find(s,program,"code_hash=?",hash,false);}
 public UUID create(StoreScope s,UUID program,UUID customer,UUID actor,int quantity,String description,Instant expiry){UUID id=UUID.randomUUID();jdbc.update("insert into wallet_points_redemption_requests(id,tenant_id,program_id,store_id,customer_id,created_by,quantity,description,expires_at) values(?,?,?,?,?,?,?,?,?)",id,s.tenantId().value(),program,s.storeId().value(),customer,actor,quantity,description,Timestamp.from(expiry));return id;}
 public void issue(StoreScope s,UUID id,String hash,Instant expires){jdbc.update("update wallet_points_redemption_requests set code_hash=?,expires_at=? where tenant_id=? and store_id=? and id=?",hash,Timestamp.from(expires),s.tenantId().value(),s.storeId().value(),id);}
 public void complete(StoreScope s,UUID id,UUID entry,Instant at){jdbc.update("update wallet_points_redemption_requests set status='completed',entry_id=?,completed_at=? where tenant_id=? and store_id=? and id=?",entry,Timestamp.from(at),s.tenantId().value(),s.storeId().value(),id);}
 public void cancel(StoreScope s,UUID id){jdbc.update("update wallet_points_redemption_requests set status='cancelled' where tenant_id=? and store_id=? and id=?",s.tenantId().value(),s.storeId().value(),id);}
}
