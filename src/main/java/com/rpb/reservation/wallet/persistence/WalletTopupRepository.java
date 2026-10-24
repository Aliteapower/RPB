package com.rpb.reservation.wallet.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.application.WalletHistoryQuery;
import com.rpb.reservation.wallet.domain.WalletException;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.wallet.application.WalletViews.*;

@Repository
public class WalletTopupRepository {
    private final JdbcTemplate jdbc;
    public WalletTopupRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public Topup create(StoreScope scope,Account a,long cents,String channel,UUID actor) {
        return jdbc.query("insert into wallet_topups(tenant_id,program_id,account_id,store_id,amount_cents,channel,created_by) values(?,?,?,?,?,?,?) returning *",(rs,n)->row(rs),scope.tenantId().value(),a.programId(),a.id(),scope.storeId().value(),cents,channel,actor).getFirst();
    }
    public Topup get(StoreScope scope,UUID id,boolean lock) {
        return jdbc.query("select * from wallet_topups where tenant_id=? and store_id=? and id=?"+(lock?" for update":""),(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value(),id).stream().findFirst().orElseThrow(WalletException::missing);
    }
    public List<Topup> list(StoreScope scope) {
        return list(scope,new WalletHistoryQuery(0,"all"));
    }
    public List<Topup> list(StoreScope scope,WalletHistoryQuery query) {
        String filter=switch(query.status()) {
            case "pending" -> " and status in ('pending_collection','verified_pending_credit','exception')";
            case "completed" -> " and status='credited'";
            case "closed" -> " and status='cancelled'";
            default -> "";
        };
        var parameters=new ArrayList<Object>();parameters.add(scope.tenantId().value());parameters.add(scope.storeId().value());
        if(query.origin()!=null){filter+=" and origin=?";parameters.add(query.origin());}
        if(query.customerId()!=null){filter+=" and customer_id=?";parameters.add(query.customerId());}
        parameters.add(query.offset());
        return jdbc.query("select * from wallet_topups where tenant_id=? and store_id=?"+filter+" order by created_at desc,id limit 100 offset ?",(rs,n)->row(rs),parameters.toArray());
    }
    public void declarePaid(StoreScope s,UUID id){jdbc.update("update wallet_topups set customer_declared_paid_at=coalesce(customer_declared_paid_at,now()),version=version+case when customer_declared_paid_at is null then 1 else 0 end,updated_at=now() where tenant_id=? and store_id=? and id=? and status='pending_collection'",s.tenantId().value(),s.storeId().value(),id);}
    public void status(StoreScope scope,UUID id,String status) {
        jdbc.update("update wallet_topups set status=?,credited_at=case when ?='credited' then coalesce(credited_at,now()) else credited_at end,version=version+1,updated_at=now() where tenant_id=? and store_id=? and id=?",status,status,scope.tenantId().value(),scope.storeId().value(),id);
    }
    public void refunded(StoreScope scope,UUID id,long cents) {
        jdbc.update("update wallet_topups set refunded_cents=refunded_cents+?,version=version+1,updated_at=now() where tenant_id=? and store_id=? and id=?",cents,scope.tenantId().value(),scope.storeId().value(),id);
    }
    private Topup row(ResultSet rs)throws SQLException {
        return new Topup(rs.getObject("id",UUID.class),rs.getObject("program_id",UUID.class),rs.getObject("account_id",UUID.class),rs.getObject("store_id",UUID.class),rs.getLong("amount_cents"),rs.getString("channel"),rs.getString("status"),rs.getLong("refunded_cents"),rs.getInt("version"),rs.getTimestamp("created_at").toInstant(),rs.getLong("bonus_cents"),rs.getString("deduction_policy"),rs.getString("origin"),instant(rs,"customer_declared_paid_at"),instant(rs,"credited_at"),snapshot(rs,"offer_snapshot"),snapshot(rs,"policy_snapshot"));
    }
    private java.time.Instant instant(ResultSet r,String key)throws SQLException{return r.getTimestamp(key)==null?null:r.getTimestamp(key).toInstant();}
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
    private Map<String,Object> snapshot(ResultSet r,String key)throws SQLException{try{return JSON.readValue(r.getString(key),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new SQLException("invalid wallet snapshot",e);}}
}
