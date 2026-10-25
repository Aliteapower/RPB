package com.rpb.reservation.cards.redemption;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.common.scope.StoreScope;
import java.time.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository("cardRedemptionRepository")
public class RedemptionRepository {
 private final JdbcTemplate jdbc;public RedemptionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Request get(StoreScope s,UUID id,boolean lock){return jdbc.query("select * from card_requests where tenant_id=? and store_id=? and id=?"+(lock?" for update":""),this::map,s.tenantId().value(),s.storeId().value(),id).stream().findFirst().orElseThrow(CardException::missing);}
 public Request create(StoreScope s,UUID actor,RequestCreate c,Instant expiry){UUID id=UUID.randomUUID();jdbc.update("insert into card_requests(id,tenant_id,store_id,plan_id,card_id,kind,quantity,created_by,expires_at) values(?,?,?,?,?,?,?,?,?)",id,s.tenantId().value(),s.storeId().value(),c.planId(),c.cardId(),c.kind(),c.quantity(),actor,Timestamp.from(expiry));return get(s,id,false);}
 public void issue(StoreScope s,UUID id,String hash,Instant expiry){jdbc.update("update card_requests set code_hash=?,authorization_hash=null,authorization_day=null,expires_at=? where tenant_id=? and store_id=? and id=?",hash,Timestamp.from(expiry),s.tenantId().value(),s.storeId().value(),id);}
 public Request code(StoreScope s,String hash,boolean auth,boolean lock){return jdbc.query("select * from card_requests where tenant_id=? and store_id=? and "+(auth?"authorization_hash":"code_hash")+"=?"+(lock?" for update":""),this::map,s.tenantId().value(),s.storeId().value(),hash).stream().findFirst().orElseThrow(CardException::missing);}
 public void authorize(StoreScope s,UUID id,UUID customer,UUID card,String hash,LocalDate day){jdbc.update("update card_requests set customer_id=?,card_id=?,authorization_hash=?,authorization_day=? where tenant_id=? and store_id=? and id=?",customer,card,hash,java.sql.Date.valueOf(day),s.tenantId().value(),s.storeId().value(),id);}
 public void complete(StoreScope s,UUID id,UUID customer,UUID card,UUID entry){jdbc.update("update card_requests set status='completed',customer_id=?,card_id=?,entry_id=?,authorization_hash=null where tenant_id=? and store_id=? and id=?",customer,card,entry,s.tenantId().value(),s.storeId().value(),id);}
 public void cancel(StoreScope s,UUID id){jdbc.update("update card_requests set status='cancelled',authorization_hash=null where tenant_id=? and store_id=? and id=?",s.tenantId().value(),s.storeId().value(),id);}
 private Request map(ResultSet r,int n)throws SQLException{return new Request(r.getObject("id",UUID.class),r.getString("kind"),r.getObject("plan_id",UUID.class),r.getObject("card_id",UUID.class),r.getObject("customer_id",UUID.class),r.getInt("quantity"),r.getObject("created_by",UUID.class),r.getString("status"),r.getTimestamp("expires_at").toInstant(),r.getObject("entry_id",UUID.class),r.getDate("authorization_day")==null?null:r.getDate("authorization_day").toLocalDate());}
}
