package com.rpb.reservation.wallet.merchantorder;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.WalletException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class MerchantOrderRepository {
 private final JdbcTemplate jdbc;
 public MerchantOrderRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Order(UUID id,String kind,Long amount,Integer quantity,String description,String status,UUID creator,UUID customer,UUID bill,UUID entry,Instant expires){}
 private Order find(StoreScope s,UUID program,String column,Object value,boolean lock){return jdbc.query("select * from wallet_merchant_orders where tenant_id=? and program_id=? and store_id=? and "+column+"=?"+(lock?" for update":""),(r,n)->new Order(r.getObject("id",UUID.class),r.getString("kind"),(Long)r.getObject("amount_cents"),(Integer)r.getObject("quantity"),r.getString("description"),r.getString("status"),r.getObject("created_by",UUID.class),r.getObject("customer_id",UUID.class),r.getObject("bill_id",UUID.class),r.getObject("points_entry_id",UUID.class),r.getTimestamp("expires_at").toInstant()),s.tenantId().value(),program,s.storeId().value(),value).stream().findFirst().orElseThrow(WalletException::missing);}
 public Order get(StoreScope s,UUID program,UUID id,boolean lock){return find(s,program,"id",id,lock);}
 public Order code(StoreScope s,UUID program,String hash){return find(s,program,"code_hash",hash,false);}
 public UUID create(StoreScope s,UUID program,UUID creator,String kind,Long amount,Integer quantity,String description,Instant expires){UUID id=UUID.randomUUID();jdbc.update("insert into wallet_merchant_orders(id,tenant_id,program_id,store_id,created_by,kind,amount_cents,quantity,description,expires_at) values(?,?,?,?,?,?,?,?,?,?)",id,s.tenantId().value(),program,s.storeId().value(),creator,kind,amount,quantity,description,Timestamp.from(expires));return id;}
 public void issue(StoreScope s,UUID id,String hash,Instant expires){jdbc.update("update wallet_merchant_orders set code_hash=?,expires_at=? where tenant_id=? and store_id=? and id=?",hash,Timestamp.from(expires),s.tenantId().value(),s.storeId().value(),id);}
 public void complete(StoreScope s,UUID id,UUID customer,UUID bill,UUID entry,Instant at){jdbc.update("update wallet_merchant_orders set status='completed',customer_id=?,bill_id=?,points_entry_id=?,completed_at=? where tenant_id=? and store_id=? and id=?",customer,bill,entry,Timestamp.from(at),s.tenantId().value(),s.storeId().value(),id);}
 public void cancel(StoreScope s,UUID id){jdbc.update("update wallet_merchant_orders set status='cancelled' where tenant_id=? and store_id=? and id=?",s.tenantId().value(),s.storeId().value(),id);}
}
