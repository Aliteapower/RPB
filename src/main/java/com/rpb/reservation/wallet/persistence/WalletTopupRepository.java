package com.rpb.reservation.wallet.persistence;

import com.rpb.reservation.common.scope.StoreScope;
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
        return jdbc.query("select * from wallet_topups where tenant_id=? and store_id=? order by created_at desc,id limit 100",(rs,n)->row(rs),scope.tenantId().value(),scope.storeId().value());
    }
    public void status(StoreScope scope,UUID id,String status) {
        jdbc.update("update wallet_topups set status=?,version=version+1,updated_at=now() where tenant_id=? and store_id=? and id=?",status,scope.tenantId().value(),scope.storeId().value(),id);
    }
    public void refunded(StoreScope scope,UUID id,long cents) {
        jdbc.update("update wallet_topups set refunded_cents=refunded_cents+?,version=version+1,updated_at=now() where tenant_id=? and store_id=? and id=?",cents,scope.tenantId().value(),scope.storeId().value(),id);
    }
    private Topup row(ResultSet rs)throws SQLException {
        return new Topup(rs.getObject("id",UUID.class),rs.getObject("program_id",UUID.class),rs.getObject("account_id",UUID.class),rs.getObject("store_id",UUID.class),rs.getLong("amount_cents"),rs.getString("channel"),rs.getString("status"),rs.getLong("refunded_cents"),rs.getInt("version"),rs.getTimestamp("created_at").toInstant());
    }
}
